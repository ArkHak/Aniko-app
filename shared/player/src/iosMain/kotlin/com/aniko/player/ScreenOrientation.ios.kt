@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aniko.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.UIKit.UIInterfaceOrientationPortraitUpsideDown
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS

/**
 * iOS: поворот в альбомную ориентацию, пока открыт полноэкранный плеер — официальным
 * `UIWindowScene.requestGeometryUpdate` (iOS 16+).
 *
 * Раньше здесь был честный CUT: считалось, что нужен доступ к `UIWindowScene` через обёртку
 * `MainViewController`. Сцена доступна и без неё — через `UIApplication.connectedScenes`; живая
 * проверка 2026-10-02 показала, что без поворота «полный экран» на iPhone — маленькое видео посреди
 * портретного экрана, то есть фича не работала вовсе.
 *
 * `Info.plist` разрешает landscape на уровне приложения (`UISupportedInterfaceOrientations`), поэтому
 * запрос `Landscape` система выполняет. При выходе из fullscreen возвращаем ориентацию, в которой
 * экран был до входа (как Android, см. KDoc expect-функции [LockLandscapeOrientationEffect]): из
 * портрета — обратно в портрет, из уже альбомной — ничего не трогаем.
 *
 * На iOS 15 (`requestGeometryUpdate` нет — проверка через `respondsToSelector`) эффект — no-op:
 * пользователь может повернуть телефон сам, интерфейс поддерживает landscape.
 */
@Composable
actual fun LockLandscapeOrientationEffect() {
    DisposableEffect(Unit) {
        val scene = activeWindowScene()
        val wasPortrait =
            scene?.interfaceOrientation.let {
                it == UIInterfaceOrientationPortrait || it == UIInterfaceOrientationPortraitUpsideDown
            }
        scene?.requestOrientation(UIInterfaceOrientationMaskLandscape)
        onDispose {
            if (wasPortrait) activeWindowScene()?.requestOrientation(UIInterfaceOrientationMaskPortrait)
        }
    }
}

private fun activeWindowScene(): UIWindowScene? =
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull()

private fun UIWindowScene.requestOrientation(mask: ULong) {
    if (!respondsToSelector(NSSelectorFromString(REQUEST_GEOMETRY_SELECTOR))) return
    requestGeometryUpdateWithPreferences(
        UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = mask),
        errorHandler = null,
    )
}

private const val REQUEST_GEOMETRY_SELECTOR = "requestGeometryUpdateWithPreferences:errorHandler:"
