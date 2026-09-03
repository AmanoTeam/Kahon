package eu.kanade.tachiyomi.data.backup.restore.restorers

import app.cash.sqldelight.async.coroutines.awaitAsList
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import tachiyomi.data.Database

@Inject
class SavedSearchRestorer(
    private val database: Database,
) {

    suspend fun restoreSavedSearches(backupSavedSearches: List<BackupSavedSearch>) {
        if (backupSavedSearches.isEmpty()) return

        database.transaction {
            val currentSavedSearches = database.saved_searchQueries.selectAll().awaitAsList()

            backupSavedSearches.filter { backupSavedSearch ->
                currentSavedSearches.none { currentSavedSearch ->
                    currentSavedSearch.source == backupSavedSearch.source &&
                        currentSavedSearch.name == backupSavedSearch.name &&
                        currentSavedSearch.query.orEmpty() == backupSavedSearch.query &&
                        (currentSavedSearch.filters_json ?: "[]") == backupSavedSearch.filterList
                }
            }.forEach { backupSavedSearch ->
                database.saved_searchQueries.insert(
                    source = backupSavedSearch.source,
                    name = backupSavedSearch.name,
                    query = backupSavedSearch.query.ifBlank { null },
                    filtersJson = backupSavedSearch.filterList.ifBlank { null }
                        ?.takeUnless { it == "[]" },
                )
            }
        }
    }
}
