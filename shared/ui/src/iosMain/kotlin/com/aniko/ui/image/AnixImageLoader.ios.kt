package com.aniko.ui.image

import coil3.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * iOS: `<sandbox>/Library/Caches/Aniko/ImageCache`.
 *
 * Сознательно `Caches` (а НЕ `Application Support`, как у офлайн-БД в `shared:database`
 * — см. `IosDatabaseDriverFactory`): постеры восстановимы из сети в любой момент, а `Caches`
 * — единственный каталог из стандартных Apple-рекомендаций, который система вправе очищать
 * под нехваткой места на устройстве и который НЕ попадает в iCloud-бэкап/iTunes-бэкап.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun imageCacheDirectory(context: PlatformContext): Path {
    val fileManager = NSFileManager.defaultManager
    val cachesUrl =
        fileManager.URLForDirectory(
            directory = NSCachesDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    val imageCacheUrl = cachesUrl?.URLByAppendingPathComponent("Aniko/ImageCache")
    val path = requireNotNull(imageCacheUrl?.path) { "Failed to resolve Caches directory" }
    return path.toPath()
}
