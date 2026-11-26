package eu.kanade.tachiyomi.adblock

import android.content.res.AssetManager
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class ParseFilterListHelper {

    fun parseFilterList(
        assetManager: AssetManager,
        filterListFilePath: String,
    ): FilterListDataClass {
        val filterListDataClass = FilterListDataClass()

        try {
            BufferedReader(InputStreamReader(assetManager.open(filterListFilePath))).use { bufferedReader ->
                bufferedReader.forEachLine { line -> parseLine(line, filterListDataClass) }
            }
        } catch (_: IOException) {
        }

        return filterListDataClass
    }

    private fun parseLine(
        line: String,
        filterListDataClass: FilterListDataClass,
    ) {
        if (line.isBlank() || line.startsWith('[')) return
        if (line.contains("##") || line.contains("#?#") || line.contains("#@#") || line.contains("#$#")) return
        if (line.startsWith('!')) return

        var entryString = line
        var domain = FilterOptionDisposition.Null
        var domainList = emptyList<String>()
        var thirdParty = FilterOptionDisposition.Null
        var hasUnsupportedOption = false

        val indexOfLastDollarSign = entryString.lastIndexOf('$')
        if (indexOfLastDollarSign > -1) {
            val originalFilterOptionsString = entryString.substring(indexOfLastDollarSign + 1)
            entryString = entryString.take(indexOfLastDollarSign)

            if (originalFilterOptionsString.isNotEmpty()) {
                for (filterOptionString in originalFilterOptionsString.split(',')) {
                    when {
                        filterOptionString.startsWith("domain=") -> {
                            val modifiedFilterOptionString = filterOptionString.substring(7)
                            if (modifiedFilterOptionString.startsWith('~')) {
                                domain = FilterOptionDisposition.Override
                                domainList = modifiedFilterOptionString.replace("~", "").split('|')
                            } else {
                                domain = FilterOptionDisposition.Apply
                                domainList = modifiedFilterOptionString.split('|')
                            }
                        }

                        filterOptionString == "third-party" -> thirdParty = FilterOptionDisposition.Apply
                        filterOptionString == "~third-party" -> thirdParty = FilterOptionDisposition.Override
                        filterOptionString == "important" -> Unit
                        filterOptionString.isNotEmpty() -> hasUnsupportedOption = true
                    }
                }

                if (hasUnsupportedOption) return
                if (domain == FilterOptionDisposition.Null && thirdParty == FilterOptionDisposition.Null) return
            }

            if (entryString.isEmpty()) return
        }

        var initialMatch = false
        var finalMatch = false
        val isAllowList = entryString.startsWith("@@")
        if (isAllowList) entryString = entryString.substring(2)

        val initialDomain = entryString.startsWith("||")
        if (initialDomain) {
            entryString = entryString.substring(2)
            initialMatch = true
        }
        val regularExpression = !initialDomain && entryString.contains("\\")

        val appliedEntryList = if (regularExpression) {
            listOf(entryString)
        } else {
            if (entryString.startsWith('|')) {
                entryString = entryString.substring(1)
                initialMatch = true
            }
            if (entryString.endsWith('|')) {
                entryString = entryString.dropLast(1)
                finalMatch = true
            }
            if (entryString.startsWith('*')) entryString = entryString.substring(1)
            if (entryString.endsWith('*')) entryString = entryString.dropLast(1)
            entryString.split('*')
        }

        val entry = FilterListEntryDataClass(
            appliedEntryList = appliedEntryList,
            domainList = domainList,
            finalMatch = finalMatch,
            initialMatch = initialMatch,
            domain = domain,
            thirdParty = thirdParty,
        )

        val targetList = when {
            isAllowList && initialDomain -> filterListDataClass.initialDomainAllowList
            isAllowList && regularExpression -> filterListDataClass.regularExpressionAllowList
            isAllowList -> filterListDataClass.mainAllowList
            initialDomain -> filterListDataClass.initialDomainBlockList
            regularExpression -> filterListDataClass.regularExpressionBlockList
            else -> filterListDataClass.mainBlockList
        }
        targetList.add(entry)
    }
}
