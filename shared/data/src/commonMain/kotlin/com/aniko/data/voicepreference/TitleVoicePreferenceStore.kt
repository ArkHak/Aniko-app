package com.aniko.data.voicepreference

import com.russhwolf.settings.Settings

/**
 * Локальное хранилище выбранной озвучки конкретного тайтла — «запоминание озвучки» (dubbing memory).
 *
 * Тот же паттерн, что [com.aniko.data.playerpreferences.PlayerPreferencesStore]: обычный
 * [Settings] (plaintext), синглтон в `dataModule`. Выбор не секретен, а потеря приводит максимум
 * к возврату дефолта «первый тип/источник».
 *
 * Пара «тип озвучки + источник» хранится АТОМАРНО под одним ключом `title.voice.<releaseId>`
 * в формате `"<typeId>:<sourceId>"` — оба ID целые и не содержат разделителей, поэтому
 * сериализация тривиальна. Атомарность важна: раздельные ключи позволили бы рассинхрон
 * (typeId новой озвучки + sourceId старой), из которой резолв пришлось бы выпутываться.
 * Полураспад парой не страшен: читающая сторона (resolve цепочки на экране релиза)
 * валидирует оба ID против свежих списков `voiceTypes`/`sources` и падает на дефолт,
 * если сохранённая пара больше не существует.
 *
 * Запись — только когда известны ОБА значения: в `ReleaseDetailsViewModel` это выбор источника
 * (`selectSource` — там typeId уже в стейте), смена одного лишь типа озвучки в стор НЕ пишется
 * (source ещё не выбран, писать было бы некуда); в `PlayerViewModel` — переключение озвучки
 * в пикере, где пара известна сразу.
 */
class TitleVoicePreferenceStore(
    private val settings: Settings,
) {
    /** Сохранённая пара «тип озвучки + источник» для одного тайтла. */
    data class SavedVoice(
        val typeId: Int,
        val sourceId: Int,
    )

    /**
     * Сохранённая пара для [releaseId], либо `null`, если выбора не было или запись битая
     * (любой мусор в `Settings` читается как «нет выбора» — см. [parse]).
     */
    fun load(releaseId: Int): SavedVoice? = parse(settings.getStringOrNull(key(releaseId)))

    /** Запомнить [typeId]/[sourceId] как последний явный выбор пользователя для [releaseId]. */
    fun save(
        releaseId: Int,
        typeId: Int,
        sourceId: Int,
    ) {
        settings.putString(key(releaseId), "$typeId$SEPARATOR$sourceId")
    }

    /** Забыть сохранённый выбор для [releaseId] (записи нет — удаление no-op). */
    fun clear(releaseId: Int) {
        settings.remove(key(releaseId))
    }

    private fun parse(raw: String?): SavedVoice? {
        val parts = raw?.split(SEPARATOR).orEmpty()
        if (parts.size != 2) return null
        val typeId = parts[0].toIntOrNull()
        val sourceId = parts[1].toIntOrNull()
        return if (typeId == null || sourceId == null) null else SavedVoice(typeId = typeId, sourceId = sourceId)
    }

    private companion object {
        const val KEY_PREFIX = "title.voice."
        const val SEPARATOR = ":"

        fun key(releaseId: Int) = "$KEY_PREFIX$releaseId"
    }
}
