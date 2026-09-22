package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.ui.manga.ChapterList
import tachiyomi.domain.chapter.model.Chapter

/**
 * Returns a copy of the list with duplicate chapters removed
 */
fun List<Chapter>.removeDuplicates(currentChapter: Chapter): List<Chapter> {
    return groupBy { it.chapterNumber }
        .map { (_, chapters) ->
            chapters.find { it.id == currentChapter.id }
                ?: chapters.find { it.scanlator == currentChapter.scanlator }
                ?: chapters.first()
        }
}

/**
 * Returns a copy of the list with duplicate chapter entries removed,
 * keeping the first entry of each chapter number
 */
fun List<ChapterList.Item>.removeDuplicates(): List<ChapterList.Item> {
    return distinctBy { it.chapter.chapterNumber }
}
