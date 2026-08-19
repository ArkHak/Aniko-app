package com.aniko.app

import com.aniko.app.navigation.DeepLinkDispatcher

/**
 * Точка входа для Swift-обёртки (P10.T7) — экспортируется в фреймворк `ComposeApp` как
 * `DeepLinkEntryKt.handleDeepLinkUrl(url:)`, вызывается из `iosApp/iosApp/iOSApp.swift`
 * (`.onOpenURL { url in ... }`) — SwiftUI сам решает вопрос холодного/тёплого старта: колбэк
 * срабатывает и при запуске приложения по ссылке, и пока оно уже открыто, отдельно обрабатывать
 * `application(_:open:options:)`/`scene(_:openURLContexts:)` не нужно.
 *
 * `CFBundleURLTypes` со схемой `aniko` зарегистрирована в `iosApp/iosApp/Info.plist`.
 */
@Suppress("unused") // вызывается из Swift, не из Kotlin
fun handleDeepLinkUrl(url: String) {
    DeepLinkDispatcher.dispatch(url)
}
