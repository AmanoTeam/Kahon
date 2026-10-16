package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

@Inject
class SavedSearchRestorer(
    private val savedSearchRepository: SavedSearchRepository,
) {

    suspend fun restoreSavedSearches(backupSavedSearches: List<BackupSavedSearch>) {
        if (backupSavedSearches.isEmpty()) return

        val currentSavedSearches = savedSearchRepository.getAll()

        backupSavedSearches
            .filter { backupSavedSearch ->
                currentSavedSearches.none { currentSavedSearch ->
                    currentSavedSearch.source == backupSavedSearch.source &&
                        currentSavedSearch.name == backupSavedSearch.name &&
                        currentSavedSearch.query.orEmpty() == backupSavedSearch.query &&
                        (currentSavedSearch.filtersJson ?: "[]") == backupSavedSearch.filterList
                }
            }
            .map { backupSavedSearch ->
                SavedSearch(
                    id = 0L,
                    source = backupSavedSearch.source,
                    name = backupSavedSearch.name,
                    query = backupSavedSearch.query.ifBlank { null },
                    filtersJson = backupSavedSearch.filterList.ifBlank { null }
                        ?.takeUnless { it == "[]" },
                )
            }
            .let { toInsert ->
                if (toInsert.isNotEmpty()) savedSearchRepository.insertAll(toInsert)
            }
    }
}
