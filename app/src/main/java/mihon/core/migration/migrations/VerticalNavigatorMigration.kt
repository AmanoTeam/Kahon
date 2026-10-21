package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@ContributesIntoSet(AppScope::class)
class VerticalNavigatorMigration(
    private val preferenceStore: PreferenceStore,
    private val readerPreferences: ReaderPreferences,
) : Migration {
    override val version: Float = 25f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        // Old versions enabled the vertical navigator by default. Don't transfer the stale
        // default over; default to none and fall back to the horizontal slider instead.
        preferenceStore.getBoolean("pref_webtoon_vertical_navigator", true).let {
            if (it.isSet()) it.delete()
        }

        val oldVerticalNavigatorOnLeft = preferenceStore.getBoolean("pref_webtoon_vertical_navigator_on_left", false)
        if (oldVerticalNavigatorOnLeft.isSet()) {
            readerPreferences.verticalNavigatorOnLeft.set(oldVerticalNavigatorOnLeft.get())
            oldVerticalNavigatorOnLeft.delete()
        }

        return@withContext true
    }
}
