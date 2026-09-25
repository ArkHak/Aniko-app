package com.aniko.data.update

import com.russhwolf.settings.Settings

/**
 * Локальное состояние проверки обновлений: когда проверяли в последний раз (троттлинг «не чаще
 * раза в сутки») и какую версию пользователь попросил больше не предлагать.
 */
class UpdateStore(
    private val settings: Settings,
) {
    /** Epoch millis последней завершённой проверки; `0` — не проверяли. */
    var lastCheckAtMs: Long
        get() = settings.getLong(KEY_LAST_CHECK_AT, 0L)
        set(value) = settings.putLong(KEY_LAST_CHECK_AT, value)

    /** Версия (`0.2.0`), скрытая кнопкой «Пропустить эту версию»; `null` — ничего не пропущено. */
    var skippedVersion: String?
        get() = settings.getStringOrNull(KEY_SKIPPED_VERSION)
        set(value) {
            if (value == null) settings.remove(KEY_SKIPPED_VERSION) else settings.putString(KEY_SKIPPED_VERSION, value)
        }

    private companion object {
        const val KEY_LAST_CHECK_AT = "update.lastCheckAtMs"
        const val KEY_SKIPPED_VERSION = "update.skippedVersion"
    }
}
