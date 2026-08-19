package com.aniko.app

import com.aniko.app.di.initKoinOnce
import com.aniko.data.sync.BackgroundSyncScheduler
import org.koin.mp.KoinPlatform

/**
 * Регистрация обработчиков `BGTaskScheduler` (P10.T1). Экспортируется в фреймворк `ComposeApp` как
 * `BackgroundSyncRegistrationKt.registerBackgroundSyncTasks()`.
 *
 * Отдельная точка входа рядом с [MainViewController], а не строчка внутри него: iOS требует, чтобы
 * `BGTaskScheduler.register` был вызван ДО возврата из
 * `application(_:didFinishLaunchingWithOptions:)`, иначе система бросает
 * `NSInternalInconsistencyException` при первой же попытке подать заявку. `MainViewController()`
 * вызывается позже — когда SwiftUI строит `ComposeView`, — так что оттуда это делать нельзя.
 * Зовёт `AppDelegate` в `iosApp/iosApp/iOSApp.swift`.
 *
 * [initKoinOnce] здесь обязателен и идемпотентен: этот код выполняется РАНЬШЕ
 * [MainViewController], то есть графа зависимостей может ещё не быть.
 */
fun registerBackgroundSyncTasks() {
    initKoinOnce()
    KoinPlatform.getKoin().get<BackgroundSyncScheduler>().registerHandlers()
}
