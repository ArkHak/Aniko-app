package com.aniko.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Мост между платформенными точками входа (Android `Intent.data` в `MainActivity.onCreate`/
 * `onNewIntent`, iOS `.onOpenURL` в `iosApp/iosApp/iOSApp.swift`, Desktop CLI-аргумент в `main()`)
 * и Compose-деревом (`AnixAppScaffold` в `App.kt`, единственное место с живым `NavController`).
 *
 * Простой top-level `object` со `StateFlow`, а не Koin-синглтон и не прямой вызов
 * `navController.navigate(...)`: платформенные точки входа вызываются ДО гарантированной
 * инициализации Koin/Compose (Android `onCreate` до `setContent`, Desktop `main()` до
 * `application { }`), а `NavController` в принципе не существует, пока не собран
 * `AnixAppScaffold` — той же проблеме уже есть прецедент в этом файле дерева: `onBackHandlerReady`
 * в `App.kt` решает симметричную задачу (Compose → наружу вместо наружу → Compose) тем же приёмом
 * "передать позже через колбэк/поток", а не притягиванием DI туда, где ему рано появляться.
 *
 * `StateFlow` (не одноразовый callback/`SharedFlow`) намеренно: если ссылка пришла холодным
 * стартом ДО того, как отрисовался `AnixAppScaffold` (например, пользователь ещё не залогинен —
 * `AnixSessionGate` в это время показывает `LoginScreen`), значение остаётся в [pending] и
 * подхватывается, как только `AnixAppScaffold` наконец соберёт свою `LaunchedEffect`-подписку —
 * навигация на deep link просто откладывается до логина, а не теряется молча.
 */
object DeepLinkDispatcher {
    private val pendingUrl = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = pendingUrl.asStateFlow()

    /** Платформенная точка входа вызывает при получении URL (холодный старт или уже запущенное приложение). */
    fun dispatch(url: String) {
        pendingUrl.value = url
    }

    /** Вызывается после того, как [pending] обработан навигацией (успешно или нет) — иначе то же
     * значение обработалось бы повторно при следующей пересборке `AnixAppScaffold`. */
    fun consume() {
        pendingUrl.value = null
    }
}
