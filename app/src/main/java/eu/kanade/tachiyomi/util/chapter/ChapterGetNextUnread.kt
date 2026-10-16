package eu.kanade.tachiyomi.util.chapter

import eu.kanade.domain.chapter.model.applyFilters
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.manga.ChapterList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

/**
 * Gets next unread chapter with filters and sorting applied.
 * When [skipDupe] is true, duplicate chapters are removed the same way as the chapter list,
 * so hidden duplicates are never picked
 */
fun List<Chapter>.getNextUnread(manga: Manga, downloadManager: DownloadManager, skipDupe: Boolean): Chapter? {
    return applyFilters(manga, downloadManager)
        .let { if (skipDupe) it.removeDuplicates() else it }
        .let { chapters ->
            if (manga.sortDescending()) {
                chapters.findLast { !it.read }
            } else {
                chapters.find { !it.read }
            }
        }
}

/**
 * Gets next unread chapter with filters and sorting applied.
 * When [skipDupe] is true, duplicate chapters are removed the same way as the chapter list,
 * so hidden duplicates are never picked
 */
fun List<ChapterList.Item>.getNextUnread(manga: Manga, skipDupe: Boolean): Chapter? {
    return applyFilters(manga).toList()
        .let { if (skipDupe) it.removeDuplicates() else it }
        .let { chapters ->
            if (manga.sortDescending()) {
                chapters.findLast { !it.chapter.read }
            } else {
                chapters.find { !it.chapter.read }
            }
        }?.chapter
}
