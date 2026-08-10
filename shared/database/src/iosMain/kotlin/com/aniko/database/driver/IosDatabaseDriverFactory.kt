package com.aniko.database.driver

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.aniko.database.AnikoDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * iOS: файл БД в `<sandbox>/Library/Application Support/Aniko/aniko.db`.
 *
 * Сознательно НЕ `Caches` (система вправе вычистить каталог под нехваткой места — офлайн-очередь
 * с несинканными операциями была бы уничтожена без предупреждения) и НЕ `Documents` (виден
 * пользователю в Files.app и по умолчанию попадает в iCloud-бэкап — БД-кэш там не место).
 * `Application Support` — стандартная рекомендация Apple для приватных данных приложения,
 * которые должны переживать перезапуск, но не нужны пользователю напрямую.
 *
 * Каталог `Application Support/Aniko` не создаётся системой автоматически (в отличие от
 * `Documents`/`Caches`) — создаём его вручную перед тем, как `NativeSqliteDriver` попытается
 * открыть файл по этому пути.
 */
class IosDatabaseDriverFactory : DatabaseDriverFactory {
    @OptIn(ExperimentalForeignApi::class)
    override fun create(): SqlDriver {
        val basePath = applicationSupportDirectory()
        return NativeSqliteDriver(
            schema = AnikoDatabase.Schema,
            name = DatabaseDriverFactory.DATABASE_NAME,
            onConfiguration = { config ->
                config.copy(extendedConfig = config.extendedConfig.copy(basePath = basePath))
            },
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun applicationSupportDirectory(): String {
        val fileManager = NSFileManager.defaultManager
        val appSupportUrl =
            fileManager.URLForDirectory(
                directory = NSApplicationSupportDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            )
        val anikoDirectory = appSupportUrl?.URLByAppendingPathComponent("Aniko")
        val path = requireNotNull(anikoDirectory?.path) { "Failed to resolve Application Support directory" }
        fileManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        return path
    }
}
