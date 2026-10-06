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
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConcurrencyModel.MultipleReadersSingleWriter
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConnectionFactory
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.FileProvider
import com.eygraber.sqldelight.androidx.driver.SqliteJournalMode
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
object DatabaseBindings {

    /**
     * Configured like Room's connection manager: WAL unless the device is low on RAM, one writer with four
     * readers (one in TRUNCATE mode), and a busy timeout on every connection.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun providesSqlDriver(context: Context): SqlDriver {
        repairStaleSchemaVersion(context)
        val isWal = context.getSystemService<ActivityManager>()?.isLowRamDevice ?: true
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
                journalMode = if (isWal) SqliteJournalMode.WAL else SqliteJournalMode.Truncate,
                concurrencyModel = MultipleReadersSingleWriter(isWal = isWal, nonWalCount = 1, walCount = 4),
            ),
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesDatabase(driver: SqlDriver): Database {
        return Database(
            driver = driver,
            historyAdapter = History.Adapter(
                read_atAdapter = DateColumnAdapter,
            ),
            mangaAdapter = Manga.Adapter(
                remote_genreAdapter = StringListColumnAdapter,
                remote_update_strategyAdapter = UpdateStrategyColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
            ),
            chapterAdapter = Chapter.Adapter(
                remote_memoAdapter = MemoColumnAdapter,
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
}
