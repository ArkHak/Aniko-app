package com.aniko.ui.image

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Desktop: ОС-специфичный cache-каталог — тот же разбор по ОС, что и у
 * `DesktopDatabaseDriverFactory` в `shared:database`, но здесь именно `Caches`/`Cache`, а не
 * `Application Support`/данные: содержимое одноразовое (перекачивается из сети при
 * необходимости), поэтому системе/пользователю можно спокойно его чистить.
 *
 * - macOS: `~/Library/Caches/Aniko/ImageCache`
 * - Linux: `${XDG_CACHE_HOME:-~/.cache}/aniko/image_cache`
 * - Windows: `%LOCALAPPDATA%\Aniko\Cache\ImageCache`
 */
actual fun imageCacheDirectory(context: PlatformContext): Path {
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    val userHome = System.getProperty("user.home").orEmpty()
    val directory =
        when {
            osName.contains("mac") -> File(userHome, "Library/Caches/Aniko/ImageCache")
            osName.contains("win") ->
                File(System.getenv("LOCALAPPDATA") ?: File(userHome, "AppData/Local").path, "Aniko/Cache/ImageCache")
            else -> {
                val xdgCacheHome = System.getenv("XDG_CACHE_HOME") ?: File(userHome, ".cache").path
                File(xdgCacheHome, "aniko/image_cache")
            }
        }
    return directory.toOkioPath()
}
