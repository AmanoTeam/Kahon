package eu.kanade.tachiyomi.adblock

class FilterListDataClass {
    val mainAllowList = mutableListOf<FilterListEntryDataClass>()
    val initialDomainAllowList = mutableListOf<FilterListEntryDataClass>()
    val regularExpressionAllowList = mutableListOf<FilterListEntryDataClass>()
    val mainBlockList = mutableListOf<FilterListEntryDataClass>()
    val initialDomainBlockList = mutableListOf<FilterListEntryDataClass>()
    val regularExpressionBlockList = mutableListOf<FilterListEntryDataClass>()
}
