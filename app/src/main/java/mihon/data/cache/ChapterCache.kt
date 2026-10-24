package mihon.data.cache

import android.content.Context
import android.text.format.Formatter
import com.amanoteam.flare.Flare
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.DownloadStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.core.common.cache.TtlDiskCache
import mihon.core.metro.AppCoroutineScope
import okhttp3.Response
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * The page lists and images of chapters read online.
 *
 * Page lists expire soon, as sources sign or rotate their image URLs. Images last longer.
 */
@Inject
@SingleIn(AppScope::class)
class ChapterCache(
    private val context: Context,
    private val json: Json,
    @AppCoroutineScope scope: CoroutineScope,
) {

    private val root = File(context.cacheDir, "chapter_cache")

    // Out of the cache directory, so the system clearing that leaves the indexes
    private val indexes = File(context.noBackupFilesDir, "chapter_cache")

    private val pageLists = TtlDiskCache(File(root, "pages"), File(indexes, "pages.index"), 1.hours, scope)

    private val images = TtlDiskCache(File(root, "images"), File(indexes, "images.index"), 1.days, scope)

    private val imageBounds = TtlDiskCache(File(root, "bounds"), File(indexes, "bounds.index"), 1.days, scope)

    /** Guards the native cropping calls of [Flare]. */
    private val flareLock = Any()

    init {
        scope.launch {
            pageLists.warmUp()
            images.warmUp()
            imageBounds.warmUp()
        }
    }

    suspend fun readableSize(): String = Formatter.formatFileSize(context, pageLists.totalSize() + images.totalSize())

    suspend fun getPageList(chapter: Chapter): List<Page>? {
        val input = pageLists.open(chapter.key) ?: return null
        return try {
            json.decodeFromString(withContext(Dispatchers.IO) { input.bufferedReader().use { it.readText() } })
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Failed to read page list from cache" }
            pageLists.remove(chapter.key)
            null
        }
    }

    suspend fun putPageList(chapter: Chapter, pages: List<Page>) {
        try {
            val value = json.encodeToString(pages).toByteArray()
            pageLists.put(chapter.key) { it.write(value) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Failed to put page list to cache" }
        }
    }

    suspend fun isImageInCache(imageUrl: String): Boolean = imageUrl in images

    /**
     * The image's file, or null when it isn't cached.
     */
    suspend fun getImageFile(imageUrl: String): File? = images.get(imageUrl)

    /**
     * Opens the image, or returns null when it isn't cached.
     */
    suspend fun openImage(imageUrl: String): InputStream? = images.open(imageUrl)

    suspend fun removeImage(imageUrl: String) {
        images.remove(imageUrl)
    }

    /**
     * Finds the content bands of the image stored in [input], without touching any file.
     *
     * @return IntArray of [left, top, width, height, bandCount, bandTop, bandHeight, ...], or null
     * when the image cannot be processed.
     */
    fun findBands(input: File): IntArray? {
        return try {
            synchronized(flareLock) {
                Flare.findBands(input.path)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Failed to load flare" }
            null
        }
    }

    /**
     * Get the content bands of the image with the given url, when cached.
     *
     * @param imageUrl url of image.
     * @return the bands flare found, or null when not cached or undecodable.
     */
    suspend fun getImageBounds(imageUrl: String): IntArray? {
        return try {
            imageBounds.get(imageUrl)?.bufferedReader()?.useLines { it.firstOrNull() }?.toBandsOrNull()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Failed to get image bounds from cache" }
            null
        }
    }

    /**
     * Stores the image [response] holds, and closes it.
     *
     * @param live also receives the bytes as they arrive.
     */
    @Throws(IOException::class)
    suspend fun putImage(imageUrl: String, response: Response, live: DownloadStream? = null): File {
        response.body.use { body ->
            live?.reserve(body.contentLength())
            val file = images.put(imageUrl) { out ->
                body.source().use { source ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        live?.append(buffer, 0, n)
                    }
                }
            }

            // Store the content bounds flare found for the image, so the reader can window it
            // without re-encoding it
            try {
                val bands = findBands(file)
                imageBounds.put(imageUrl) { out ->
                    out.write((bands?.joinToString(",") ?: "").toByteArray())
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Failed to put image bounds into cache" }
            }

            return file
        }
    }

    /**
     * Removes everything, returning how many entries there were.
     */
    suspend fun clear(): Int = pageLists.clear() + images.clear() + imageBounds.clear()

    private val Chapter.key: String
        get() = "$mangaId$url"
}

/**
 * Parses the bands flare found for an image, [left, top, width, height, count, bandTop,
 * bandHeight, ...], or null when the entry is missing or malformed.
 */
private fun String.toBandsOrNull(): IntArray? {
    val bands = split(",").mapNotNull { it.toIntOrNull() }
    val count = bands.getOrElse(4) { 0 }
    return if (bands.size == 5 + 2 * count && count > 0 && bands[2] > 0 && bands[3] > 0) bands.toIntArray() else null
}
