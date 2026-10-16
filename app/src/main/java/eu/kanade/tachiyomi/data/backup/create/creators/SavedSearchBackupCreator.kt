package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

@Inject
class SavedSearchBackupCreator(
    private val savedSearchRepository: SavedSearchRepository,
) {

    suspend operator fun invoke(): List<BackupSavedSearch> {
        return savedSearchRepository.getAll().map { savedSearch ->
            BackupSavedSearch(
                source = savedSearch.source,
                name = savedSearch.name,
                query = savedSearch.query.orEmpty(),
                filterList = savedSearch.filtersJson ?: "[]",
            )
        }
    }
}
