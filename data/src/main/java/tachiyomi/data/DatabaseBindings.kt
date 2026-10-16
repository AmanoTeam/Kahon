package tachiyomi.data

import android.app.ActivityManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.core.content.getSystemService
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitQuery
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConcurrencyModel.MultipleReadersSingleWriter
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConnectionFactory
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.FileProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import logcat.LogPriority
import mihon.data.database.DatabaseRepositoryImpl
import tachiyomi.core.common.util.system.logcat

@ContributesTo(AppScope::class)
@BindingContainer
object DatabaseBindings {

    /**
     * WAL on every device, so a write never blocks reads. Low-RAM devices get one reader instead of four, since each
     * connection keeps its own page cache.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun providesSqlDriver(context: Context, databaseRepository: DatabaseRepositoryImpl): SqlDriver {
        repairStaleSchemaVersion(context)
        val isLowRam = context.getSystemService<ActivityManager>()?.isLowRamDevice == true
        return AndroidxSqliteDriver(
            connectionFactory = object : AndroidxSqliteConnectionFactory {
                override val driver: SQLiteDriver = BundledSQLiteDriver()

                override fun createConnection(name: String): SQLiteConnection {
                    return driver.open(name).apply {
                        execSQL("PRAGMA busy_timeout = 3000")
                    }
                }
            },
            databaseType = AndroidxSqliteDatabaseType.FileProvider(context, "tachiyomi.db"),
            schema = Database.Schema,
            configuration = AndroidxSqliteConfiguration(
                isForeignKeyConstraintsEnabled = true,
                concurrencyModel = MultipleReadersSingleWriter(isWal = true, walCount = if (isLowRam) 1 else 4),
            ),
            // Runs before the driver compares the schema version, and onOpen once the schema is ready
            onConfigure = {
                val version = executePragmaQuery("user_version", { QueryResult.Value(it.apply { next() }.getLong(0)) })
                databaseRepository.isMigrating.value = version in 1..<Database.Schema.version
            },
            onOpen = {
                try {
                    restoreUserOverrideMetadata()
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to restore manga override metadata" }
                }
                databaseRepository.isMigrating.value = false
            },
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesDatabase(driver: SqlDriver): Database {
        return Database(
            driver = driver,
            historyAdapter = History.Adapter(
                read_atAdapter = InstantColumnAdapter,
            ),
            mangaAdapter = Manga.Adapter(
                remote_genreAdapter = StringListColumnAdapter,
                remote_update_strategyAdapter = UpdateStrategyColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
                user_favorite_atAdapter = InstantColumnAdapter,
                state_chapter_last_updateAdapter = InstantColumnAdapter,
                state_chapter_next_updateAdapter = InstantColumnAdapter,
                state_cover_last_modifiedAdapter = InstantColumnAdapter,
            ),
            chapterAdapter = Chapter.Adapter(
                remote_date_uploadAdapter = InstantColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
                user_last_page_readAdapter = IntColumnAdapter,
                state_date_fetchAdapter = InstantColumnAdapter,
            ),
            manga_trackAdapter = Manga_track.Adapter(
                start_dateAdapter = InstantColumnAdapter,
                finish_dateAdapter = InstantColumnAdapter,
            ),
        )
    }

    private fun repairStaleSchemaVersion(context: Context) {
        val dbFile = context.getDatabasePath("tachiyomi.db")
        if (!dbFile.exists() || dbFile.length() == 0L) return

        val db = try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (_: SQLiteException) {
            return
        }

        try {
            val version = db.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else return
            }

            if (version >= Database.Schema.version && !hasTable(db, "source")) {
                val targetVersion = if (hasColumn(db, "mangas", "favorite_at")) 17L else 15L
                db.execSQL("PRAGMA user_version = $targetVersion")
            } else if (
                version >= 20L &&
                version <= Database.Schema.version &&
                hasColumn(db, "manga", "user_override_metadata")
            ) {
                val targetVersion = when {
                    !isColumnNullable(db, "manga", "state_cover_last_modified") -> 19L
                    version == 20L -> 21L
                    else -> null
                }
                if (targetVersion == 19L) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS manga_user_override_backup(" +
                            "manga_id INTEGER NOT NULL PRIMARY KEY, user_override_metadata INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "INSERT OR REPLACE INTO manga_user_override_backup(manga_id, user_override_metadata) " +
                            "SELECT id, user_override_metadata FROM manga WHERE user_override_metadata != 0",
                    )
                }
                targetVersion?.let { db.execSQL("PRAGMA user_version = $it") }
            }
        } finally {
            db.close()
        }
    }

    private fun hasTable(db: SQLiteDatabase, name: String): Boolean {
        return db
            .rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name))
            .use { it.moveToFirst() }
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        return db
            .rawQuery("SELECT 1 FROM pragma_table_info(?) WHERE name = ?", arrayOf(table, column))
            .use { it.moveToFirst() }
    }

    private fun isColumnNullable(db: SQLiteDatabase, table: String, column: String): Boolean {
        return db
            .rawQuery("SELECT \"notnull\" FROM pragma_table_info(?) WHERE name = ?", arrayOf(table, column))
            .use { it.moveToFirst() && it.getInt(0) == 0 }
    }

    private suspend fun SqlDriver.restoreUserOverrideMetadata() {
        val hasBackup = awaitQuery(
            identifier = null,
            sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'manga_user_override_backup'",
            mapper = { cursor -> cursor.next() },
            parameters = 0,
        ).value
        if (hasBackup) {
            await(
                identifier = null,
                sql = "UPDATE manga " +
                    "SET user_override_metadata = " +
                    "(SELECT user_override_metadata FROM manga_user_override_backup WHERE manga_id = manga.id) " +
                    "WHERE id IN (SELECT manga_id FROM manga_user_override_backup)",
                parameters = 0,
            )
            await(
                identifier = null,
                sql = "DROP TABLE IF EXISTS manga_user_override_backup",
                parameters = 0,
            )
        }
    }
}
