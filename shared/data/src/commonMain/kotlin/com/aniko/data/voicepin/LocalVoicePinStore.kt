package com.aniko.data.voicepin

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Локальное хранилище закреплённых (pinned) команд озвучки (P16.T6).
 *
 * Тот же паттерн, что [com.aniko.data.locale.LocaleStore]: обычный [Settings] (plaintext),
 * без secure storage — список пинов не секретен, а потеря приводит максимум к сбросу
 * пользовательской сортировки.
 *
 * Множество пинов хранится как одна отсортированная строка через запятую — IDs озвучек
 * целые и не содержат разделителей, поэтому сериализация тривиальна и не требует
 * дополнительных зависимостей.
 */
class LocalVoicePinStore(
    private val settings: Settings,
) {
    private val pinnedIdsFlow = MutableStateFlow(readPinnedIds())

    /** Поток множества ID закреплённых команд озвучки. */
    fun pinnedIds(): Flow<Set<Int>> = pinnedIdsFlow.asStateFlow()

    /** Переключить состояние пина для команды [id]. */
    suspend fun toggle(id: Int) {
        setPinned(id, id !in pinnedIdsFlow.value)
    }

    /** Явно установить или снять пин команды [id]. */
    suspend fun setPinned(
        id: Int,
        pinned: Boolean,
    ) {
        val updated =
            pinnedIdsFlow.value.toMutableSet().apply {
                if (pinned) add(id) else remove(id)
            }
        writePinnedIds(updated)
        pinnedIdsFlow.value = updated
    }

    private fun readPinnedIds(): Set<Int> =
        settings
            .getStringOrNull(KEY_PINNED_IDS)
            ?.takeIf { it.isNotEmpty() }
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()

    private fun writePinnedIds(ids: Set<Int>) {
        if (ids.isEmpty()) {
            settings.remove(KEY_PINNED_IDS)
        } else {
            settings.putString(KEY_PINNED_IDS, ids.sorted().joinToString(","))
        }
    }

    private companion object {
        const val KEY_PINNED_IDS = "voice.pinned_ids"
    }
}
