package com.aniko.data.notification

import com.aniko.model.AppNotification

/**
 * Превращает доменное [AppNotification] в текст конкретного [LocalNotification] (P10.T6).
 *
 * Почему это интерфейс, а не функция прямо здесь: текст обязан быть локализован, а локализация
 * живёт в `shared/ui` (Lyricist, `Strings`/`EnStrings`/`RuStrings`) — модуле, от которого
 * `shared/data` не зависит и не должен (`shared/ui` тащит Compose Runtime). Единственное место,
 * которое видит оба модуля сразу, — `composeApp`, там и лежит реализация
 * (`AppNotificationContentFactory`). Тот же приём, что уже применён к выбору языка: `LocaleStore`
 * в `shared/data` хранит тег, а резолвит его в реальные строки уровень выше (`ProvideAppStrings`).
 *
 * Реализация **не** может быть `@Composable`: вызывающий — фоновый тик синхронизации
 * ([com.aniko.data.sync.PeriodicSyncTask]), на Android он выполняется в `CoroutineWorker` без
 * какой-либо композиции вообще.
 */
fun interface NotificationContentFactory {
    /** @return `null`, если уведомление такого вида показывать нечем (нет данных для текста). */
    fun create(notification: AppNotification): LocalNotification?
}
