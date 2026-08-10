package com.aniko.database.driver

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aniko.database.AnikoDatabase
import java.io.File

/**
 * Desktop: `JdbcSqliteDriver` — в отличие от `AndroidSqliteDriver`/`NativeSqliteDriver`, НЕ
 * создаёт и НЕ мигрирует схему сама, это нужно делать вручную через `PRAGMA user_version`
 * (см. официальный гайд SQLDelight по JDBC-драйверу).
 *
 * `journal_mode = WAL` — параллельные читатели не блокируются писателем, важно для будущего
 * офлайн-воркера (пишет в очередь), работающего одновременно с UI (читает кэш).
 */
class DesktopDatabaseDriverFactory : DatabaseDriverFactory {
    override fun create(): SqlDriver {
        val databaseFile = resolveDatabaseFile()
        databaseFile.parentFile?.mkdirs()

        val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")
        driver.execute(null, "PRAGMA journal_mode = WAL;", 0)
        migrateIfNeeded(driver)
        return driver
    }

    private fun migrateIfNeeded(driver: JdbcSqliteDriver) {
        val currentVersion = readUserVersion(driver)
        val schemaVersion = AnikoDatabase.Schema.version
        when {
            currentVersion == 0L -> AnikoDatabase.Schema.create(driver)
            currentVersion < schemaVersion -> AnikoDatabase.Schema.migrate(driver, currentVersion, schemaVersion)
        }
        if (currentVersion != schemaVersion) {
            driver.execute(null, "PRAGMA user_version = $schemaVersion;", 0)
        }
    }

    private fun readUserVersion(driver: JdbcSqliteDriver): Long =
        driver
            .executeQuery(
                identifier = null,
                sql = "PRAGMA user_version;",
                mapper = { cursor ->
                    QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
                },
                parameters = 0,
            ).value

    /**
     * Путь по ОС (нет XDG-каталогов на Android/iOS, поэтому такой разброс — не обобщить одной
     * реализацией с ними):
     * - macOS: `~/Library/Application Support/Aniko/aniko.db`
     * - Linux: `${XDG_DATA_HOME:-~/.local/share}/aniko/aniko.db`
     * - Windows: `%LOCALAPPDATA%\Aniko\aniko.db`
     */
    private fun resolveDatabaseFile(): File {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val userHome = System.getProperty("user.home").orEmpty()
        val directory =
            when {
                osName.contains("mac") -> File(userHome, "Library/Application Support/Aniko")
                osName.contains("win") ->
                    File(System.getenv("LOCALAPPDATA") ?: File(userHome, "AppData/Local").path, "Aniko")
                else -> {
                    val xdgDataHome = System.getenv("XDG_DATA_HOME") ?: File(userHome, ".local/share").path
                    File(xdgDataHome, "aniko")
                }
            }
        return File(directory, DatabaseDriverFactory.DATABASE_NAME)
    }
}
