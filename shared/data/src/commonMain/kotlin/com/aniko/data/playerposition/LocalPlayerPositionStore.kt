package com.aniko.data.playerposition

import com.russhwolf.settings.Settings

/**
 * Ключ для сохранённой позиции плеера.
 *
 * Позиция привязана к конкретному выпуску, источнику и серии — один и тот же ordinal серии
 * в разных источниках может вести на разные фрагменты, поэтому ключ включает все три
 * компонента.
 */
data class PositionKey(
    val releaseId: Int,
    val sourceId: Int,
    val episodeOrdinal: Int,
)

/**
 * Локальное хранилище последней позиции воспроизведения серий (P16.T7).
 *
 * Тот же паттерн, что [com.aniko.data.locale.LocaleStore]: обычный [Settings] (plaintext),
 * без secure storage — позиция просмотра не секретна.
 */
class LocalPlayerPositionStore(
    private val settings: Settings,
) {
    /** Сохранить позицию [positionMs] для ключа [key]. */
    suspend fun save(
        key: PositionKey,
        positionMs: Long,
    ) {
        settings.putLong(key.toSettingsKey(), positionMs)
    }

    /** Вернуть сохранённую позицию в миллисекундах либо `null`, если записи нет. */
    suspend fun load(key: PositionKey): Long? =
        if (settings.hasKey(key.toSettingsKey())) {
            settings.getLong(key.toSettingsKey(), 0L)
        } else {
            null
        }

    /** Удалить сохранённую позицию для ключа [key]. */
    suspend fun clear(key: PositionKey) {
        settings.remove(key.toSettingsKey())
    }

    private fun PositionKey.toSettingsKey(): String = "player.position.$releaseId.$sourceId.$episodeOrdinal"
}
