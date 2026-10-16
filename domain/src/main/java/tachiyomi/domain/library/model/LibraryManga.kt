package tachiyomi.domain.library.model

import tachiyomi.domain.manga.model.Manga
import kotlin.time.Instant

data class LibraryManga(
    val manga: Manga,
    val categories: List<Long>,
    val totalChapters: Long,
    val readCount: Long,
    val unreadCount: Long,
    // Unread count matching the chapter list with duplicate chapter numbers skipped
    val unreadCountDeduped: Long,
    val bookmarkCount: Long,
    val latestUpload: Instant?,
    val chapterFetchedAt: Instant?,
    val lastRead: Instant?,
) {
    val id: Long = manga.id

    val hasBookmarks
        get() = bookmarkCount > 0

    val hasStarted = readCount > 0
}
