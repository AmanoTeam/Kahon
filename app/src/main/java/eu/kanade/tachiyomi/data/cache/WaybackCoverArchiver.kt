package eu.kanade.tachiyomi.data.cache

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.MangaUpdate
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps track of which manga covers are archived at the Wayback Machine, submitting save requests
 * for the missing ones, so covers whose original URL dies can still be served from the archive.
 */
@Inject
@SingleIn(AppScope::class)
class WaybackCoverArchiver(
    private val networkHelper: NetworkHelper,
    private val updateManga: UpdateManga,
    @AppCoroutineScope private val scope: CoroutineScope,
) {

    private enum class Status { ARCHIVED, SAVE_SUBMITTED }

    // A 302 instead of the snapshot response is the answer this client is meant to see
    private val availabilityClient: OkHttpClient by lazy {
        networkHelper.client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private val statuses = ConcurrentHashMap<String, Status>()

    fun archiveIfMissing(coverUrl: String?) {
        val url = coverUrl?.takeIf(::isArchivable) ?: return
        scope.launch {
            when (checkAvailability(url)) {
                true -> statuses[url] = Status.ARCHIVED
                false -> submitSave(url)
                // Transient failure, checked again the next time the manga is opened
                null -> {}
            }
        }
    }

    /**
     * Returns the Wayback Machine URL of [coverUrl] if it has a snapshot, otherwise submits a save
     * request and returns null.
     */
    suspend fun findArchivedUrl(coverUrl: String): String? {
        if (!isArchivable(coverUrl)) return null
        when (statuses[coverUrl]) {
            Status.ARCHIVED -> return archivedUrl(coverUrl)
            // A save was just submitted and won't be available right away
            Status.SAVE_SUBMITTED -> return null
            null -> {}
        }
        return when (checkAvailability(coverUrl)) {
            true -> {
                statuses[coverUrl] = Status.ARCHIVED
                archivedUrl(coverUrl)
            }
            false -> {
                submitSave(coverUrl)
                null
            }
            null -> null
        }
    }

    suspend fun storeArchivedUrl(mangaId: Long, archivedUrl: String) {
        updateManga.await(MangaUpdate(mangaId) { thumbnailUrl = archivedUrl })
    }

    private fun isArchivable(coverUrl: String): Boolean {
        return coverUrl.startsWith("http", true) && !coverUrl.contains("web.archive.org")
    }

    private fun archivedUrl(coverUrl: String): String {
        return CHECK_URL_PREFIX + coverUrl
    }

    private suspend fun checkAvailability(coverUrl: String): Boolean? {
        return try {
            availabilityClient.newCall(Request.Builder().url(archivedUrl(coverUrl)).build()).await()
                .use { response ->
                    when (response.code) {
                        HTTP_FOUND -> true
                        HTTP_NOT_FOUND -> false
                        else -> null
                    }
                }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Wayback Machine availability check failed for $coverUrl" }
            null
        }
    }

    private fun submitSave(coverUrl: String) {
        if (statuses.putIfAbsent(coverUrl, Status.SAVE_SUBMITTED) != null) return
        scope.launch {
            try {
                networkHelper.client.newCall(Request.Builder().url(SAVE_URL_PREFIX + coverUrl).build())
                    .await()
                    .use { response ->
                        logcat { "Wayback Machine save request for $coverUrl answered with ${response.code}" }
                    }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Wayback Machine save request for $coverUrl failed" }
            }
        }
    }

    companion object {
        private const val CHECK_URL_PREFIX = "https://web.archive.org/web/0if_/"
        private const val SAVE_URL_PREFIX = "https://web.archive.org/save/"

        private const val HTTP_FOUND = 302
        private const val HTTP_NOT_FOUND = 404
    }
}
