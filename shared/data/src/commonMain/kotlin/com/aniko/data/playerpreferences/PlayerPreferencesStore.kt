package com.aniko.data.playerpreferences

import com.aniko.player.PREFERRED_QUALITY_HEIGHTS
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Хранилище пользовательских настроек воспроизведения — раздел «Воспроизведение» экрана настроек.
 *
 * Тот же паттерн, что [com.aniko.data.theme.ThemeStore]/[com.aniko.data.theme.AppIconStore]:
 * значение не секретное, поэтому обычный [Settings] (plaintext), синглтон в `dataModule`, чтение —
 * реактивным [StateFlow].
 *
 * Сейчас здесь одна настройка — [preferredQualityHeight] («Предпочтительное качество видео по
 * умолчанию»). Она применяется при СТАРТЕ воспроизведения (см.
 * [com.aniko.player.EmbedVideoController.setPreferredQuality]); ручной выбор качества в плеере
 * действует на текущую серию и сюда НЕ пишется.
 */
class PlayerPreferencesStore(
    private val settings: Settings,
) {
    private val _preferredQualityHeight =
        MutableStateFlow(settings.getIntOrNull(KEY_PREFERRED_QUALITY_HEIGHT)?.let(::validate))

    /**
     * Предпочтительная высота кадра в px — одна из [PREFERRED_QUALITY_HEIGHTS] (`1080`/`720`/`480`/
     * `360`) либо `null`, что означает «Авто» (ничего не переопределять — поведение хоста по
     * умолчанию, дефолт приложения).
     */
    val preferredQualityHeight: StateFlow<Int?> = _preferredQualityHeight.asStateFlow()

    /**
     * [heightPx] вне [PREFERRED_QUALITY_HEIGHTS] (включая `null`) возвращает «Авто»: запись
     * удаляется, а не хранится мусором, который старая/новая версия набора качеств могла бы
     * прочитать по-разному.
     */
    fun setPreferredQualityHeight(heightPx: Int?) {
        val validated = validate(heightPx)
        if (validated == null) {
            settings.remove(KEY_PREFERRED_QUALITY_HEIGHT)
        } else {
            settings.putInt(KEY_PREFERRED_QUALITY_HEIGHT, validated)
        }
        _preferredQualityHeight.value = validated
    }

    private companion object {
        const val KEY_PREFERRED_QUALITY_HEIGHT = "player.preferred_quality_height"

        fun validate(heightPx: Int?): Int? = heightPx?.takeIf { it in PREFERRED_QUALITY_HEIGHTS }
    }
}
