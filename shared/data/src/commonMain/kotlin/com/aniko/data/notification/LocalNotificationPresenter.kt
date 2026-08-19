package com.aniko.data.notification

/**
 * Готовое к показу локальное OS-уведомление (P10.T5/T6).
 *
 * «Локальное» = порождённое самим приложением, без участия сервера пуш-нотификаций. Это прямое
 * следствие вердикта P10.T4: FCM-топик Anixart живёт в чужом Firebase-проекте и получить его наш
 * клиент не может ни на Android, ни (тем более) на iOS.
 *
 * @param id стабильный идентификатор — серверный `AppNotification.id`. Именно он, а не счётчик:
 * повторный показ того же уведомления (например, если процесс упал между показом и сохранением
 * «последнего виденного id») обновит уже висящее в шторке, а не добавит дубль.
 * @param title заголовок.
 * @param body текст.
 * @param deepLink `aniko://release/{id}` или `null`, если уведомление не привязано к тайтлу
 * (заявка в друзья, статья). Реализация вправе игнорировать — см. KDoc платформенных классов,
 * до навигации доводит только Android.
 */
data class LocalNotification(
    val id: Long,
    val title: String,
    val body: String,
    val deepLink: String? = null,
)

/**
 * Платформенный показ [LocalNotification] (P10.T5 — Desktop, плюс Android/iOS для P10.T6).
 *
 * Обычный интерфейс с реализацией на платформу и связыванием через Koin `platformModule()`, а не
 * `expect`/`actual`, — тот же выбор и по той же причине, что у соседнего
 * [com.aniko.data.sync.BackgroundSyncScheduler] и [com.aniko.data.sync.ConnectivityMonitor]:
 * Android-реализации нужен `Context`, а `expect class` не умеет принимать платформенный тип в
 * конструкторе, не протащив его в общий контракт. Плюс интерфейс тривиально подменяется фейком в
 * `commonTest` (см. `NotificationPollerTest`), чего `actual`-класс не позволяет.
 */
interface LocalNotificationPresenter {
    /**
     * Есть ли у приложения право показывать уведомления, и если нет — запросить его, когда
     * платформа позволяет сделать это без UI.
     *
     * Один метод на два разных платформенных механизма намеренно:
     * - **iOS** может и запросить (`UNUserNotificationCenter.requestAuthorization` — системный
     *   алерт, вызывается откуда угодно, повторный вызов возвращает уже принятое решение без
     *   показа алерта);
     * - **Android 13+** — не может: `POST_NOTIFICATIONS` запрашивается только через
     *   `ActivityResultLauncher`, то есть из живой `Activity`. Отсюда реализация лишь **проверяет**
     *   разрешение, а запрос делает UI (`NotificationPermissionState` в `composeApp`, экран
     *   настроек);
     * - **Desktop** — разрешения нет вообще, проверяется доступность системного трея.
     *
     * @return `false` — показывать нельзя, вызывающий обязан молча пропустить показ (не падать и
     * не ретраить).
     */
    suspend fun ensurePermission(): Boolean

    /** Показывает уведомление. Не бросает: отказ платформы — не повод ронять фоновый тик. */
    suspend fun show(notification: LocalNotification)
}
