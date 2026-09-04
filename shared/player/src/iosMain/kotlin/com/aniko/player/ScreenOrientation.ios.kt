package com.aniko.player

import androidx.compose.runtime.Composable

/**
 * **CUT на iOS** — программная блокировка ориентации на landscape не реализована.
 *
 * Единственный работающий без полноценной Swift-интеграции способ — приватный (недокументированный)
 * KVC-приём `UIDevice.currentDevice.setValue(_:forKey:"orientation")`: `NSKeyValueCoding.setValue
 * (forKey:)` не резолвится в доступном на этой машине наборе Kotlin/Native cinterop-биндингов
 * `platform.darwin.NSObject`/`platform.UIKit.UIDevice` (проверено — `platform.darwin.NSObject` уже
 * даёт компилироваться самому типу, но метод `setValue(forKey:)` на нём не находится ни в одной
 * комбинации сигнатур). Официальный современный API (`UIWindowScene.requestGeometryUpdate`,
 * iOS 16+) требует доступа к реальному `UIWindowScene` активного окна — `MainViewController.kt`
 * (`composeApp/iosMain`) отдаёт `ComposeUIViewController` как есть, без обёртки, которая
 * держала бы такую ссылку; протягивать её ради одной кнопки — за пределами этой задачи.
 *
 * `Info.plist` уже разрешает landscape на уровне приложения (`UISupportedInterfaceOrientations`),
 * поэтому кнопка "На весь экран" в [com.aniko.app.feature.player.PlayerOverlay] всё равно разворачивает
 * видео на всю ширину экрана в ТЕКУЩЕЙ ориентации — если пользователь физически повернёт телефон,
 * система сама переведёт интерфейс в landscape (то же самое, что уже работало до этой задачи).
 * Отсутствует только программное автоматическое переключение по тапу без физического поворота —
 * честно вырезано, а не подделано молчаливым no-op без объяснения (тот же принцип, что и Quality-
 * CUT в `PlayerBottomPanel`, `docs/REELWAVE_PLAN.md`, отчёт P13.T9).
 */
@Composable
actual fun LockLandscapeOrientationEffect() {
}
