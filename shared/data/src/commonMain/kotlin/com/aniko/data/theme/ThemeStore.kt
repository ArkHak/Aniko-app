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
 * Поддерживаемые значения: `"light"`, `"dark"`, `"amoled"`. Любое другое значение
 * при записи округляется до `null` (дефолт светлая тема).
 */
class ThemeStore(
    private val settings: Settings,
) {
    private val _themeMode = MutableStateFlow(settings.getStringOrNull(KEY_THEME_MODE)?.let(::validate))

    /** Явно выбранная тема (`"light"`/`"dark"`/`"amoled"`) либо `null`, если пользователь
     * не переопределял системную. */
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
        val VALID_MODES = setOf("light", "dark", "amoled")

        fun validate(mode: String?): String? = mode?.takeIf { it in VALID_MODES }
    }
}
