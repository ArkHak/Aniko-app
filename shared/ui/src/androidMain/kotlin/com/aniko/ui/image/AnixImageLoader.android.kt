package com.aniko.ui.image

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toOkioPath

/**
 * Android: `context.cacheDir` — тот же каталог, что использует система для очистки при нехватке
 * места ("Очистить кэш" в настройках приложения тоже метит сюда), поэтому диск-кэш Coil ведёт
 * себя ровно так, как ожидает пользователь и ОС.
 */
actual fun imageCacheDirectory(context: PlatformContext): Path = context.cacheDir.resolve("image_cache").toOkioPath()
