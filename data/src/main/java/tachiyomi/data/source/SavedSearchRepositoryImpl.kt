package tachiyomi.data.source

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SavedSearchRepositoryImpl(
    private val database: Database,
) : SavedSearchRepository {

    override suspend fun getById(savedSearchId: Long): SavedSearch? {
        return database.saved_searchQueries
            .selectById(savedSearchId, SavedSearchMapper::map)
            .awaitAsOneOrNull()
    }

    override suspend fun getBySourceId(sourceId: Long): List<SavedSearch> {
        return database.saved_searchQueries
            .selectBySource(sourceId, SavedSearchMapper::map)
            .awaitAsList()
    }

    override fun getBySourceIdAsFlow(sourceId: Long): Flow<List<SavedSearch>> {
        return database.saved_searchQueries
            .selectBySource(sourceId, SavedSearchMapper::map)
            .subscribeToList()
    }

    override suspend fun delete(savedSearchId: Long) {
        database.saved_searchQueries.deleteById(savedSearchId)
    }

    override suspend fun insert(savedSearch: SavedSearch): Long {
        return database.transactionWithResult {
            val currentSavedSearches = database.saved_searchQueries
                .selectAll(SavedSearchMapper::map)
                .awaitAsList()
            val existedSavedSearchId = currentSavedSearches.find { currentSavedSearch ->
                currentSavedSearch.source == savedSearch.source &&
                    currentSavedSearch.name == savedSearch.name &&
                    currentSavedSearch.query == savedSearch.query &&
                    currentSavedSearch.filtersJson == savedSearch.filtersJson
            }?.id

            existedSavedSearchId
                ?: database.saved_searchQueries.insert(
                    savedSearch.source,
                    savedSearch.name,
                    savedSearch.query,
                    savedSearch.filtersJson,
                )
        }
    }

    override suspend fun insertAll(savedSearch: List<SavedSearch>) {
        database.transaction {
            savedSearch.forEach {
                database.saved_searchQueries.insert(
                    it.source,
                    it.name,
                    it.query,
                    it.filtersJson,
                )
            }
        }
    }
}
