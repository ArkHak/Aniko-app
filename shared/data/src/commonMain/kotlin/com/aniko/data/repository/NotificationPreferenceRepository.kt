package com.aniko.data.repository

import com.aniko.data.api.NotificationPreferenceApi
import com.aniko.data.mapper.toDomain
import com.aniko.model.NotificationPreferenceToggle
import com.aniko.model.NotificationPreferences

/**
 * Серверные тумблеры типов уведомлений (P10.T6).
 *
 * Тонкий слой поверх [NotificationPreferenceApi] — своего состояния не держит и ничего не кэширует
 * намеренно: тумблеры меняются редко, читаются одним экраном настроек, а любой локальный кэш здесь
 * работал бы против инвертирующей семантики сервера (см. KDoc [NotificationPreferenceApi]) —
 * рассинхрон кэша с сервером означал бы, что следующее переключение уводит флаг не туда.
 *
 * [toggle] сразу перечитывает состояние по той же причине: сервер не сообщает, чему стал равен
 * флаг после инверсии, а угадывать его локальным `!value` — значит расходиться с сервером при
 * любой параллельной правке (например, из официального клиента).
 */
class NotificationPreferenceRepository(
    private val api: NotificationPreferenceApi,
) {
    suspend fun preferences(): NotificationPreferences = api.my().toDomain()

    /** @return актуальное состояние всех тумблеров ПОСЛЕ переключения. */
    suspend fun toggle(toggle: NotificationPreferenceToggle): NotificationPreferences {
        api.toggle(toggle)
        return preferences()
    }
}
