package com.aniko.database.driver

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.aniko.database.AnikoDatabase

/**
 * Android: `AndroidSqliteDriver` создаёт/мигрирует схему сам через `SQLiteOpenHelper`,
 * файл БД лежит в стандартном `Context.getDatabasePath(...)` (`/data/data/<app>/databases/`).
 */
class AndroidDatabaseDriverFactory(
    private val context: Context,
) : DatabaseDriverFactory {
    override fun create(): SqlDriver =
        AndroidSqliteDriver(
            schema = AnikoDatabase.Schema,
            context = context,
            name = DatabaseDriverFactory.DATABASE_NAME,
        )
}
