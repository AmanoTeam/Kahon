package eu.kanade.tachiyomi.adblock

enum class FilterOptionDisposition {
    Null,
    Apply,
    Override,
}

class FilterListEntryDataClass(
    val appliedEntryList: List<String>,
    val domainList: List<String> = emptyList(),
    val finalMatch: Boolean = false,
    val initialMatch: Boolean = false,
    val domain: FilterOptionDisposition = FilterOptionDisposition.Null,
    val thirdParty: FilterOptionDisposition = FilterOptionDisposition.Null,
)
