package tachiyomi.domain.source.model

data class SavedSearch(
    // Saved search identifier, unique
    val id: Long,

    // The source the saved search is for
    val source: Long,

    // The name of the saved search
    val name: String,

    // The query if there is any
    val query: String?,

    // The filter list
    val filtersJson: String?,
)
