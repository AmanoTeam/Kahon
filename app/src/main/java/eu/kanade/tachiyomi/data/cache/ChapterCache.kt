package eu.kanade.tachiyomi.data.cache

import android.content.Context
import android.text.format.Formatter
import com.amanoteam.flare.Flare
import com.jakewharton.disklrucache.DiskLruCache
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.DownloadStream
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.saveTo
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Response
import okio.buffer
import okio.sink
import okio.source
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import java.io.File
import java.io.IOException

/**
 * Class used to create chapter cache
 * For each image in a chapter a file is created
 * For each chapter a Json list is created and converted to a file.
 * The files are in format *md5key*.0
 *
 * @param context the application context.
 */
@Inject
@SingleIn(AppScope::class)
class ChapterCache(
    private val context: Context,
    private val json: Json,
) {

    /** Guards the native cropping calls of [Flare]. */
    private val flareLock = Any()

    /** Cache class used for cache management. */
    private val diskCache by lazy {
        DiskLruCache.open(
            File(context.cacheDir, "chapter_disk_cache"),
            PARAMETER_APP_VERSION,
            PARAMETER_VALUE_COUNT,
            PARAMETER_CACHE_SIZE,
        )
    }

    /**
     * Returns directory of cache.
     */
    private val cacheDir: File
        get() = diskCache.directory

    /**
     * Returns real size of directory.
     */
    private val realSize: Long
        get() = DiskUtil.getDirectorySize(cacheDir)

    /**
     * Returns real size of directory in human readable format.
     */
    val readableSize: String
        get() = Formatter.formatFileSize(context, realSize)

    /**
     * Get page list from cache.
     *
     * @param chapter the chapter.
     * @return the list of pages.
     */
    fun getPageListFromCache(chapter: Chapter): List<Page> {
        // Get the key for the chapter.
        val key = DiskUtil.hashKeyForDisk(getKey(chapter))

        // Convert JSON string to list of objects. Throws an exception if snapshot is null
        return diskCache.get(key).use {
            json.decodeFromString(it.getString(0))
        }
    }

    /**
     * Add page list to disk cache.
     *
     * @param chapter the chapter.
     * @param pages list of pages.
     */
    fun putPageListToCache(chapter: Chapter, pages: List<Page>) {
        // Convert list of pages to json string.
        val cachedValue = json.encodeToString(pages)

        // Initialize the editor (edits the values for an entry).
        var editor: DiskLruCache.Editor? = null

        try {
            // Get editor from md5 key.
            val key = DiskUtil.hashKeyForDisk(getKey(chapter))
            editor = diskCache.edit(key) ?: return

            // Write chapter urls to cache. The second value of every entry holds image bounds;
            // page lists have none, but the editor still has to write it.
            editor.newOutputStream(0).sink().buffer().use {
                it.write(cachedValue.toByteArray())
                it.flush()
            }
            editor.newOutputStream(1).close()

            diskCache.flush()
            editor.commit()
            editor.abortUnlessCommitted()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to put page list to cache" }
            // Ignore.
        } finally {
            editor?.abortUnlessCommitted()
        }
    }

    /**
     * Returns true if image is in cache.
     *
     * @param imageUrl url of image.
     * @return true if in cache otherwise false.
     */
    fun isImageInCache(imageUrl: String): Boolean {
        return try {
            val key = DiskUtil.hashKeyForDisk(imageUrl)
            val inJournal = diskCache.get(key).use { it != null }
            val fileExists = getImageFile(imageUrl).exists()
            if (inJournal && !fileExists) {
                logcat(LogPriority.WARN) { "Image is in journal but file is missing: $imageUrl" }
            }
            inJournal && fileExists
        } catch (_: IOException) {
            false
        }
    }

    /**
     * Get image file from url.
     *
     * @param imageUrl url of image.
     * @return path of image.
     */
    fun getImageFile(imageUrl: String): File {
        // Get file from md5 key.
        val imageName = DiskUtil.hashKeyForDisk(imageUrl) + ".0"
        return File(diskCache.directory, imageName)
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
     * Add image to cache.
     *
     * The image is stored unmodified, with the content bounds flare found for it as the second
     * value of the entry, so the reader can window the page without re-encoding it.
     *
     * @param imageUrl url of image.
     * @param response http response from page.
     * @param live also receives the bytes as they arrive.
     * @throws IOException image error.
     */
    @Throws(IOException::class)
    fun putImageToCache(imageUrl: String, response: Response, live: DownloadStream? = null) {
        // Initialize editor (edits the values for an entry).
        var editor: DiskLruCache.Editor? = null

        try {
            // Get editor from md5 key.
            val key = DiskUtil.hashKeyForDisk(imageUrl)
            editor = diskCache.edit(key) ?: return

            // Save the image to a temporary file so flare can decode it from a path.
            val input = File.createTempFile("flare_", null, context.cacheDir)

            try {
                if (live == null) {
                    response.body.source().saveTo(input)
                } else {
                    live.reserve(response.body.contentLength())
                    response.body.source().use { source ->
                        input.outputStream().use { out ->
                            val buffer = ByteArray(1 shl 16)
                            while (true) {
                                val n = source.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                live.append(buffer, 0, n)
                            }
                        }
                    }
                }

                input.source().buffer().saveTo(editor.newOutputStream(0))
                val bands = findBands(input)
                editor.newOutputStream(1).use {
                    it.write((bands?.joinToString(",") ?: "").toByteArray())
                }
            } finally {
                input.delete()
            }

            diskCache.flush()
            editor.commit()
        } finally {
            response.body.close()
            editor?.abortUnlessCommitted()
        }
    }

    /**
     * Get the content bands of the image with the given url, when cached.
     *
     * @param imageUrl url of image.
     * @return the bands flare found, or null when not cached or undecodable.
     */
    fun getImageBounds(imageUrl: String): IntArray? {
        return try {
            val key = DiskUtil.hashKeyForDisk(imageUrl)
            diskCache.get(key).use { snapshot ->
                snapshot?.getString(1)?.toBandsOrNull()
            }
        } catch (e: IOException) {
            logcat(LogPriority.WARN, e) { "Failed to get image bounds from cache" }
            null
        }
    }

    fun clear(): Int {
        var deletedFiles = 0
        cacheDir.listFiles()?.forEach {
            if (removeFileFromCache(it.name)) {
                deletedFiles++
            }
        }
        return deletedFiles
    }

    /**
     * Remove file from cache.
     *
     * @param file name of file "md5.0".
     * @return status of deletion for the file.
     */
    private fun removeFileFromCache(file: String): Boolean {
        // Make sure we don't delete the journal file (keeps track of cache)
        if (file == "journal" || file.startsWith("journal.")) {
            return false
        }

        return try {
            // Remove the extension from the file to get the key of the cache
            val key = file.substringBeforeLast(".")
            // Remove file from cache
            diskCache.remove(key)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to remove file from cache" }
            false
        }
    }

    private fun getKey(chapter: Chapter): String {
        return "${chapter.mangaId}${chapter.url}"
    }
}

/** Application cache version.  */
private const val PARAMETER_APP_VERSION = 3

/**
 * Parses the bands flare found for an image, [left, top, width, height, count, bandTop,
 * bandHeight, ...], or null when the entry is missing or malformed.
 */
private fun String.toBandsOrNull(): IntArray? {
    val bands = split(",").mapNotNull { it.toIntOrNull() }
    val count = bands.getOrElse(4) { 0 }
    return if (bands.size == 5 + 2 * count && count > 0 && bands[2] > 0 && bands[3] > 0) bands.toIntArray() else null
}

/** The number of values per cache entry: the image and its content bounds.  */
private const val PARAMETER_VALUE_COUNT = 2

/** The maximum number of bytes this cache should use to store.  */
private const val PARAMETER_CACHE_SIZE = 100L * 1024 * 1024
