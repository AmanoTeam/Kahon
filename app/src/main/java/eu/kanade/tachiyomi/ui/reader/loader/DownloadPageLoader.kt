package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.core.archive.archiveReader
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Loader used to load a chapter from the downloaded chapters.
 */
internal class DownloadPageLoader(
    private val chapter: ReaderChapter,
    private val manga: Manga,
    private val source: Source,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val chapterCache: ChapterCache,
) : PageLoader() {

    private val context: Context by injectLazy()

    private var archivePageLoader: ArchivePageLoader? = null

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val dbChapter = chapter.chapter
        val chapterPath = downloadProvider.findChapterDir(
            dbChapter.name,
            dbChapter.scanlator,
            dbChapter.url,
            manga.title,
            source,
        )
        return if (chapterPath?.isFile == true) {
            getPagesFromArchive(chapterPath)
        } else {
            getPagesFromDirectory()
        }
    }

    override fun recycle() {
        super.recycle()
        archivePageLoader?.recycle()
    }

    private suspend fun getPagesFromArchive(file: UniFile): List<ReaderPage> {
        val loader = ArchivePageLoader(file.archiveReader(context)).also { archivePageLoader = it }
        // Pages come out ready; send them back through loadPage so their bounds are found lazily
        return loader.getPages().onEach { it.status = Page.State.Queue }
    }

    private suspend fun getPagesFromDirectory(): List<ReaderPage> {
        val pages = downloadManager.buildPageList(source, manga, chapter.chapter)
        return pages.map { page ->
            ReaderPage(page.index, page.url, page.imageUrl) {
                context.contentResolver.openInputStream(page.uri ?: Uri.EMPTY)!!
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        archivePageLoader?.loadPage(page)
        if (page.status == Page.State.Ready) return

        page.bounds = findPageBounds(page)
        page.status = Page.State.Ready
    }

    /**
     * Finds the content bands of [page] with flare, so the reader can window it. Done per page as
     * it is read, rather than persisted at download time.
     *
     * @return the bands flare found, or null when they cannot be found.
     */
    private fun findPageBounds(page: ReaderPage): IntArray? {
        val input = File.createTempFile("flare_", null, context.cacheDir)

        return try {
            page.stream!!().use { stream ->
                input.outputStream().use { stream.copyTo(it) }
            }
            chapterCache.findBands(input)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Failed to find the bands of page ${page.index}" }
            null
        } finally {
            input.delete()
        }
    }
}
