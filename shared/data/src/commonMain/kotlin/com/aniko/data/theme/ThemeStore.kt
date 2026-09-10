package com.aniko.data.theme

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Хранилище выбора темы приложения.
 *
 * Тот же паттерн, что [com.aniko.data.locale.LocaleStore] использует для `languageTag` —
 * значение не секретное, поэтому обычный [Settings] (plaintext), без secure storage.
 *
 * [themeMode] — `null` означает «явный выбор не сделан»: приложение по умолчанию показывает
 * светлую тему (макет Home в Claude Design светлый, сверка 2026-09-08; системная тема не
 * учитывается). Разрешение `null` в конкретную тему — забота вызывающего кода в `composeApp`,
 * не этого класса: `shared/data` намеренно не зависит от Compose, чтобы не тащить
 * UI-фреймворк в слой данных.
 *
 * Поддерживаемые значения: `"light"`, `"dark"` (2026-09-10: AMOLED-вариант слит в единственную
 * тёмную тему — та же чёрная эстетика, пользователь предпочёл её дефолтом тёмной темы, см. отчёт
 * задачи в `docs/REELWAVE_PLAN.md`). Legacy-значение `"amoled"`, сохранённое до этой правки,
 * мигрирует на `"dark"` прозрачно при чтении (то же визуальное поведение, ключ просто
 * переименован) — не откатывается на дефолт, как обычное невалидное значение.
 */
class ThemeStore(
    private val settings: Settings,
) {
    private val _themeMode = MutableStateFlow(settings.getStringOrNull(KEY_THEME_MODE)?.let(::validate))

    /** Явно выбранная тема (`"light"`/`"dark"`) либо `null`, если пользователь не переопределял
     * системную. */
    val themeMode: StateFlow<String?> = _themeMode.asStateFlow()

    /** [mode] `null` сбрасывает выбор обратно на дефолт (светлая тема). */
    fun setThemeMode(mode: String?) {
        val validated = validate(mode)
        if (validated == null) {
            settings.remove(KEY_THEME_MODE)
        } else {
            settings.putString(KEY_THEME_MODE, validated)
        }
        _themeMode.value = validated
    }

    private companion object {
        const val KEY_THEME_MODE = "theme.mode"
        val VALID_MODES = setOf("light", "dark")

        /** `"amoled"` — legacy-ключ (до 2026-09-10) мигрирует на `"dark"`, остальные невалидные
         * значения округляются до `null` (дефолт светлая тема). */
        fun validate(mode: String?): String? =
            when (mode) {
                "amoled" -> "dark"
                in VALID_MODES -> mode
                else -> null
            }
    }
}
