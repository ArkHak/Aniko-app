package com.aniko.player

import androidx.compose.runtime.Composable

/**
 * Блокировка ориентации экрана на альбомную, пока композируется вызывающая сторона (P13-плеер:
 * кнопка "На весь экран" → видео разворачивается горизонтально, как у обычных видео-приложений).
 * Снимается сама при выходе из композиции (`DisposableEffect` в платформенных реализациях) —
 * вызывающая сторона не обязана помнить восстановить исходную ориентацию вручную.
 *
 * - Android: реальная блокировка через `Activity.requestedOrientation` (см. `ScreenOrientation.android.kt`).
 *   Политика восстановления: при захвате фиксируется эффективная ориентация устройства до записи
 *   лока; при освобождении, если приложение жило на `UNSPECIFIED`, форсируется "входная"
 *   ориентация (`SENSOR_PORTRAIT`/`SENSOR_LANDSCAPE`) на ~400ms settle-окно, после чего возвращается
 *   `UNSPECIFIED`. Это детерминированно возвращает экран к той ориентации, в которой пользователь
 *   зашёл в плеер, и не оставляет его в landscape из-за датчика. Cancel-safe: повторный вход
 *   внутри settle-окна отменяет отложенное восстановление и не захватывает установленный
 *   самим модулем settle-force (`SENSOR_PORTRAIT`/`SENSOR_LANDSCAPE`) как базу.
 *   `android:configChanges="orientation|..."` уже стоит на `MainActivity` в манифесте, поэтому
 *   Activity не пересоздаётся при повороте и `requestedOrientation` можно менять на лету.
 * - iOS: программная блокировка не реализована (CUT, см. `ScreenOrientation.ios.kt`) —
 *   `UISupportedInterfaceOrientations` в `Info.plist` разрешает landscape/portrait на уровне
 *   приложения, поэтому при физическом повороте устройства интерфейс сам перейдёт в landscape.
 *   Автоматический поворот по тапу без физического поворота отсутствует.
 * - Desktop: no-op — у окна нет "ориентации" в этом смысле, а Desktop-плеер и так остаётся
 *   стабом (системный браузер, `project_reelwave_desktop_player_backlog` п.2), кнопки
 *   fullscreen там не будет вовсе.
 */
@Composable
expect fun LockLandscapeOrientationEffect()
