package com.aniko.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard

@Composable
actual fun rememberClipboardController(): ClipboardController = remember { IosClipboardController }

@Composable
actual fun rememberShareController(): ShareController = remember { IosShareController }

private object IosClipboardController : ClipboardController {
    override fun copyText(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}

/**
 * `UIActivityViewController` (P10.T9).
 *
 * `UIApplication.sharedApplication.keyWindow?.rootViewController` — приложение состоит из ровно
 * одного `ComposeUIViewController` (см. `composeApp/src/iosMain/.../MainViewController.kt`,
 * `UIApplicationSupportsMultipleScenes = false` в `Info.plist`), отдельного UIKit-стека
 * навигации/нескольких сцен нет, поэтому презентовать шер-диалог можно напрямую поверх корневого
 * контроллера. `keyWindow` формально deprecated с iOS 13 в пользу `UIWindowScene`, но для
 * single-window/single-scene конфигурации этого проекта достаточно — заводить observer сцены
 * ради одного диалога не входит в объём этой задачи.
 *
 * На iPad `UIActivityViewController` презентуется как popover и штатно требует
 * `popoverPresentationController.sourceView`/`sourceRect` — без источника презентации там крашится.
 * Не выставляется здесь намеренно: используемая версия Kotlin/Native cinterop-биндинга UIKit в
 * этом проекте не экспонирует `UIViewController.popoverPresentationController` (подтверждено
 * компилятором — `Unresolved reference`, отличие от задокументированного публичного API UIKit),
 * а сам проект по README таргетирует iPhone (инструкции по side-load — только под iPhone, iPad
 * нигде не упоминается) — принятое для v1 ограничение, не тихая недоработка.
 */
private object IosShareController : ShareController {
    override fun shareText(
        text: String,
        subject: String?,
    ): ShareResult {
        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        val activityViewController =
            UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        rootViewController?.presentViewController(activityViewController, animated = true, completion = null)
        return ShareResult.SHEET_PRESENTED
    }
}
