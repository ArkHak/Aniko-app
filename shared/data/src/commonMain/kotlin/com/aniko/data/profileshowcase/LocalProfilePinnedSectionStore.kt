package com.aniko.data.profileshowcase

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Секции витрины профиля, которые пользователь может закрепить наверх (P16.T13 «пины вкладок»).
 *
 * Четыре значения — по числу вкладок официальной витрины Anixart 10 (статистика/оценки/
 * коллекции/комментарии, см. `docs/REELWAVE_PLAN.md` P16.T13). На экране профиля Aniko реально
 * существуют секции-аналоги [STATISTICS] (`StatsGrid` + `ProfileChartsSection`) и [ACHIEVEMENTS]
 * (витрина полученных бейджей — ближайший аналог «оценок» v10); «Коллекции»/«Комментарии»
 * оригинала на этом экране не реализованы (см. P16.T16, вне объёма этой задачи) — вместо пустых
 * заглушек под эти два имени предложены две другие реально существующие секции, которые
 * пользователь так же может захотеть увидеть первыми: [FAVORITE_GENRES] и [RECENTLY_WATCHED].
 */
enum class ProfileShowcaseSection {
    STATISTICS,
    ACHIEVEMENTS,
    FAVORITE_GENRES,
    RECENTLY_WATCHED,
}

/**
 * Локальное, клиентское закрепление ОДНОЙ секции профиля наверх (P16.T13 «пины вкладок»).
 *
 * Сервер уже отдаёт поле `pinned_section_id` (см. KDoc `ProfilePreferenceDto`; живая проверка
 * 2026-09-10, `GET profile/{id}`, несколько реальных id: `pinned_section_id: 0` у всех) — но его
 * семантика (какие section-id существуют, что означает 0, каким запросом редактируется) нигде не
 * задокументирована и не декомпилирована в рамках этой задачи. Отправка туда произвольного
 * значения могла бы незаметно испортить состояние аккаунта на официальном клиенте без способа
 * проверить последствия на этом зеркале. Поэтому пин реализован ЛОКАЛЬНО — тот же паттерн, что
 * [com.aniko.data.voicepin.LocalVoicePinStore]: обычный [Settings] (plaintext, без secure
 * storage) — потеря значения максимум сбрасывает визуальный порядок секций, ничего не рвёт.
 */
class LocalProfilePinnedSectionStore(
    private val settings: Settings,
) {
    private val pinnedSectionFlow = MutableStateFlow(readPinnedSection())

    /** Текущая закреплённая секция или `null`, если пользователь ничего не закрепил. */
    fun pinnedSection(): Flow<ProfileShowcaseSection?> = pinnedSectionFlow.asStateFlow()

    /** Закрепить [section] наверх (перезаписывает прежний пин — закреплена только одна секция). */
    suspend fun pin(section: ProfileShowcaseSection) {
        settings.putString(KEY_PINNED_SECTION, section.name)
        pinnedSectionFlow.value = section
    }

    /** Снять текущий пин, если он есть. */
    suspend fun clear() {
        settings.remove(KEY_PINNED_SECTION)
        pinnedSectionFlow.value = null
    }

    /** Переключить пин [section]: снять, если уже закреплена именно она, иначе закрепить. */
    suspend fun toggle(section: ProfileShowcaseSection) {
        if (pinnedSectionFlow.value == section) clear() else pin(section)
    }

    private fun readPinnedSection(): ProfileShowcaseSection? =
        settings
            .getStringOrNull(KEY_PINNED_SECTION)
            // Неизвестное/устаревшее значение (набор секций поменялся между версиями) трактуется
            // как «пина нет», а не как исключение — тот же приём, что и `LocalCatalogFilterStore`.
            ?.let { raw -> ProfileShowcaseSection.entries.firstOrNull { it.name == raw } }

    private companion object {
        const val KEY_PINNED_SECTION = "profile.pinned_section"
    }
}
