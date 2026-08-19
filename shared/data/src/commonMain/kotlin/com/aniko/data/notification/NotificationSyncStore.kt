package com.aniko.data.notification

import com.russhwolf.settings.Settings

/**
 * Локальное состояние поллинга уведомлений (P10.T6): что мы уже видели и уже показывали.
 *
 * Тот же паттерн, что [com.aniko.data.locale.LocaleStore] — обычный [Settings] без secure
 * storage: значения не секретные (счётчик и идентификатор), а их потеря приводит максимум к
 * одному пропущенному циклу уведомлений.
 *
 * Почему состояние вообще нужно локально, а не берётся из `is_new` сервера: `is_new` сбрасывается
 * вызовом `notification/read`, то есть зависит от того, открывал ли пользователь уведомления в
 * ОФИЦИАЛЬНОМ клиенте. Нам же нужен ответ на другой вопрос — «показывали ли ЭТО уведомление МЫ»,
 * и его сервер не знает в принципе.
 */
class NotificationSyncStore(
    private val settings: Settings,
) {
    /**
     * Значение `notification/count` на прошлом тике, либо `null`, если тика ещё не было.
     *
     * Дешёвый предохранитель: пока счётчик не вырос, вторая (тяжёлая) страница `notification/all/0`
     * не запрашивается вообще — один маленький GET на 15 минут вместо двух.
     */
    var lastCount: Long?
        get() = if (settings.hasKey(KEY_LAST_COUNT)) settings.getLong(KEY_LAST_COUNT, 0L) else null
        set(value) {
            if (value == null) settings.remove(KEY_LAST_COUNT) else settings.putLong(KEY_LAST_COUNT, value)
        }

    /**
     * Максимальный id уведомления, про которое пользователь уже знает, либо `null` — «поллинг ещё
     * ни разу не отрабатывал на этом устройстве».
     *
     * Разница между `null` и `0` здесь смысловая, а не техническая: при `null`
     * [selectUnseen] не показывает НИЧЕГО, только запоминает текущий максимум (иначе первый же
     * запуск после установки вывалил бы в шторку всю первую страницу истории аккаунта). При `0`
     * показалось бы всё.
     */
    var lastNotifiedId: Long?
        get() = if (settings.hasKey(KEY_LAST_NOTIFIED_ID)) settings.getLong(KEY_LAST_NOTIFIED_ID, 0L) else null
        set(value) {
            if (value == null) {
                settings.remove(KEY_LAST_NOTIFIED_ID)
            } else {
                settings.putLong(KEY_LAST_NOTIFIED_ID, value)
            }
        }

    /**
     * Сброс при выходе из аккаунта: следующий пользователь на том же устройстве не должен получить
     * уведомления предыдущего, а «первый запуск» для него обязан снова отработать как первый.
     */
    fun clear() {
        settings.remove(KEY_LAST_COUNT)
        settings.remove(KEY_LAST_NOTIFIED_ID)
    }

    private companion object {
        const val KEY_LAST_COUNT = "notifications.last_count"
        const val KEY_LAST_NOTIFIED_ID = "notifications.last_notified_id"
    }
}
