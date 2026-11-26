package eu.kanade.tachiyomi.adblock

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Inject
@SingleIn(AppScope::class)
class AdBlockEngine(
    private val context: Context,
    private val preferences: AdBlockPreferences,
    @AppCoroutineScope private val scope: CoroutineScope,
) {
    private val parseFilterListHelper = ParseFilterListHelper()
    private val checkFilterListHelper = CheckFilterListHelper()

    private val readyLatch = CountDownLatch(1)
    private val initializationStarted = AtomicBoolean(false)

    private var filterLists: List<FilterListDataClass> = emptyList()

    fun initialize() {
        if (!initializationStarted.compareAndSet(false, true)) return

        scope.launch {
            val parsedFilterLists = mutableListOf<FilterListDataClass>()
            try {
                parsedFilterLists.add(parseFilterList("filterlists/ultraprivacy.txt"))
                parsedFilterLists.add(parseFilterList("filterlists/ultralist.txt"))
                parsedFilterLists.add(parseFilterList("filterlists/easyprivacy.txt"))
                parsedFilterLists.add(parseFilterList("filterlists/easylist.txt"))
                parsedFilterLists.add(parseFilterList("filterlists/fanboy-annoyance.txt"))
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to parse the ad block filter lists" }
            } finally {
                filterLists = parsedFilterLists
                readyLatch.countDown()
            }
        }
    }

    fun checkRequest(
        requestUrlString: String,
        requestHostString: String?,
        webPageUrlString: String,
        firstPartyHostString: String,
    ): RequestDisposition {
        if (!preferences.enabled.get() || requestUrlString == webPageUrlString) return RequestDisposition.Default

        initialize()
        if (!readyLatch.await(30, TimeUnit.SECONDS)) {
            logcat(LogPriority.ERROR) { "Timed out waiting for the ad block filter lists" }
            return RequestDisposition.Default
        }

        val requestUrlWithSeparatorsString = requestUrlString
            .replace(':', '^')
            .replace('/', '^')
            .replace('?', '^')
            .replace('=', '^')
            .replace('&', '^')
            .let { if (it.endsWith('^')) it else "${it}^" }

        val fqdnIndex = requestUrlString.indexOf("://")
        val startIndex = if (fqdnIndex >= 0) fqdnIndex + 3 else 0

        val request = RequestDataClass(
            requestUrlString = requestUrlString,
            requestUrlWithSeparatorsString = requestUrlWithSeparatorsString,
            truncatedRequestUrlString = requestUrlString.substring(startIndex),
            truncatedRequestUrlWithSeparatorsString = requestUrlWithSeparatorsString.substring(startIndex),
            firstPartyHostString = firstPartyHostString,
            isThirdPartyRequest = firstPartyHostString.isNotEmpty() &&
                requestHostString != null &&
                requestHostString != firstPartyHostString,
        )

        for (filterList in filterLists) {
            checkFilterListHelper.checkFilterList(request, filterList)?.let { return it }
        }

        return RequestDisposition.Default
    }

    private fun parseFilterList(path: String): FilterListDataClass {
        return parseFilterListHelper.parseFilterList(context.assets, path)
    }
}
