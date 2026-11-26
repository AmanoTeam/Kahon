package eu.kanade.tachiyomi.adblock

import java.util.regex.Pattern

class CheckFilterListHelper {

    fun checkFilterList(
        request: RequestDataClass,
        filterList: FilterListDataClass,
    ): RequestDisposition? {
        return checkEntries(request, filterList.mainAllowList, RequestDisposition.Allowed, truncated = false)
            ?: checkEntries(request, filterList.initialDomainAllowList, RequestDisposition.Allowed, truncated = true)
            ?: checkRegularExpressions(request, filterList.regularExpressionAllowList, RequestDisposition.Allowed)
            ?: checkEntries(request, filterList.mainBlockList, RequestDisposition.Blocked, truncated = false)
            ?: checkEntries(request, filterList.initialDomainBlockList, RequestDisposition.Blocked, truncated = true)
            ?: checkRegularExpressions(request, filterList.regularExpressionBlockList, RequestDisposition.Blocked)
    }

    private fun checkEntries(
        request: RequestDataClass,
        entries: List<FilterListEntryDataClass>,
        disposition: RequestDisposition,
        truncated: Boolean,
    ): RequestDisposition? {
        val urlString = if (truncated) request.truncatedRequestUrlString else request.requestUrlString
        val urlWithSeparatorsString = if (truncated) {
            request.truncatedRequestUrlWithSeparatorsString
        } else {
            request.requestUrlWithSeparatorsString
        }

        for (entry in entries) {
            checkAppliedEntry(request, disposition, urlString, truncated, entry)?.let { return it }
            checkAppliedEntry(request, disposition, urlWithSeparatorsString, truncated, entry)?.let { return it }
        }

        return null
    }

    private fun checkRegularExpressions(
        request: RequestDataClass,
        entries: List<FilterListEntryDataClass>,
        disposition: RequestDisposition,
    ): RequestDisposition? {
        for (entry in entries) {
            val pattern = Pattern.compile(entry.appliedEntryList[0])
            if (pattern.matcher(request.requestUrlString).matches()) {
                checkDomain(request, disposition, entry)?.let { return it }
            }
        }

        return null
    }

    private fun checkAppliedEntry(
        request: RequestDataClass,
        disposition: RequestDisposition,
        urlString: String,
        truncated: Boolean,
        entry: FilterListEntryDataClass,
    ): RequestDisposition? {
        val appliedEntries = entry.appliedEntryList
        val matches = if (appliedEntries.size == 1) {
            val appliedEntry = appliedEntries[0]
            when {
                entry.initialMatch && entry.finalMatch ->
                    urlString.endsWith(appliedEntry) &&
                        initialMatchIndex(urlString, appliedEntry, truncated) >= 0

                entry.initialMatch -> initialMatchIndex(urlString, appliedEntry, truncated) >= 0

                entry.finalMatch -> urlString.endsWith(appliedEntry)

                else -> urlString.contains(appliedEntry)
            }
        } else {
            var matches = true
            var remainingUrlString = urlString
            var startIndex = 0
            val endIndex = if (entry.finalMatch) appliedEntries.size - 1 else appliedEntries.size

            if (entry.initialMatch) {
                val index = initialMatchIndex(remainingUrlString, appliedEntries[0], truncated)
                if (index >= 0) {
                    remainingUrlString = remainingUrlString.substring(index + appliedEntries[0].length)
                    startIndex = 1
                } else {
                    matches = false
                }
            }

            if (matches) {
                for (i in startIndex until endIndex) {
                    val index = remainingUrlString.indexOf(appliedEntries[i])
                    if (index >= 0) {
                        remainingUrlString = remainingUrlString.substring(index + appliedEntries[i].length)
                    } else {
                        matches = false
                        break
                    }
                }

                if (matches && entry.finalMatch && !remainingUrlString.endsWith(appliedEntries.last())) {
                    matches = false
                }
            }

            matches
        }

        return if (matches) checkDomain(request, disposition, entry) else null
    }

    private fun initialMatchIndex(urlString: String, appliedEntry: String, truncated: Boolean): Int {
        if (urlString.startsWith(appliedEntry)) return 0
        if (!truncated) return -1

        val hostEnd = urlString.indexOfAny(charArrayOf('/', ':', '?', '#', '^'))
            .let { if (it == -1) urlString.length else it }

        var entryIndex = urlString.indexOf(appliedEntry)
        while (entryIndex in 0 until hostEnd) {
            if (entryIndex > 0 && urlString[entryIndex - 1] == '.') return entryIndex
            entryIndex = urlString.indexOf(appliedEntry, entryIndex + 1)
        }

        return -1
    }

    private fun checkDomain(
        request: RequestDataClass,
        disposition: RequestDisposition,
        entry: FilterListEntryDataClass,
    ): RequestDisposition? {
        val domainApplies = when (entry.domain) {
            FilterOptionDisposition.Null -> true
            FilterOptionDisposition.Apply -> entry.domainList.any { request.firstPartyHostString.endsWith(it) }
            FilterOptionDisposition.Override -> entry.domainList.none { request.firstPartyHostString.endsWith(it) }
        }

        return if (domainApplies) checkThirdParty(request, disposition, entry) else null
    }

    private fun checkThirdParty(
        request: RequestDataClass,
        disposition: RequestDisposition,
        entry: FilterListEntryDataClass,
    ): RequestDisposition? {
        val thirdPartyApplies = when (entry.thirdParty) {
            FilterOptionDisposition.Null -> true
            FilterOptionDisposition.Apply -> request.isThirdPartyRequest
            FilterOptionDisposition.Override -> !request.isThirdPartyRequest
        }

        return if (thirdPartyApplies) disposition else null
    }
}
