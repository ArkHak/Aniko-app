package com.aniko.database.driver

import app.cash.sqldelight.db.SqlDriver

/**
 * Платформенная фабрика [SqlDriver] для `AnikoDatabase`.
 *
 * Реализации: `AndroidDatabaseDriverFactory` (androidMain), `IosDatabaseDriverFactory` (iosMain),
 * `DesktopDatabaseDriverFactory` (desktopMain) — биндятся в `platformModule()` в `composeApp`.
 */
interface DatabaseDriverFactory {
    fun create(): SqlDriver

    companion object {
        const val DATABASE_NAME = "aniko.db"
    }
}
