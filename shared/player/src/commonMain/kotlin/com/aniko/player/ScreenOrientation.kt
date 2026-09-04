package com.aniko.player

import androidx.compose.runtime.Composable

/**
 * Блокировка ориентации экрана на альбомную, пока композируется вызывающая сторона (P13-плеер:
 * кнопка "На весь экран" → видео разворачивается горизонтально, как у обычных видео-приложений).
 * Снимается сама при выходе из композиции (`DisposableEffect` в платформенных реализациях) —
 * вызывающая сторона не обязана помнить восстановить исходную ориентацию вручную.
 *
 * - Android: реальная блокировка через `Activity.requestedOrientation` (см. `.android.kt`) —
 *   протестирована живьём на эмуляторе.
 *   `android:configChanges="orientation|..."` уже стоит на `MainActivity` в манифесте, поэтому
 *   Activity не пересоздаётся при повороте и `requestedOrientation` можно менять на лету.
 * - iOS: `UIDevice.setValue(_:forKey:"orientation")` (см. `.ios.kt`) — устоявшийся в комьюнити
 *   Kotlin/Native приём (Apple не публикует официальный imperative API для программного поворота
 *   без полноценного `UIWindowScene`-геометрического запроса iOS 16+, а поддерживать обе ветки
 *   ради этого излишне). `UISupportedInterfaceOrientations` в `Info.plist` уже разрешает
 *   landscape/portrait на уровне приложения — без этого ключа поворот не сработал бы никаким
 *   способом. **Не проверено живьём** (WebDriverAgent не смог тапать по Compose-дереву на этой
 *   сборке в течение всей сессии верификации Фазы 13 — см. `docs/REELWAVE_PLAN.md`), но код
 *   компилируется под iOS-таргет; App Store здесь не при чём (P12.T1 — публикация в сторы не
 *   планируется), так что рисков ревью нет.
 * - Desktop: no-op — у окна нет "ориентации" в этом смысле, а Desktop-плеер и так остаётся
 *   стабом (системный браузер, `project_reelwave_desktop_player_backlog` п.2), кнопки
 *   fullscreen там не будет вовсе.
 */
@Composable
expect fun LockLandscapeOrientationEffect()
