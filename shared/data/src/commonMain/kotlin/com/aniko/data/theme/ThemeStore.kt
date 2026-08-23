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
 * [themeMode] — `null` означает «нет явного выбора, следовать системной теме». Разрешение
 * `null` в реальную тему (чтение `isSystemInDarkTheme()`) — забота вызывающего кода в
 * `composeApp`, не этого класса: `shared/data` намеренно не зависит от Compose, чтобы не тащить
 * UI-фреймворк в слой данных.
 */
class ThemeStore(
    private val settings: Settings,
) {
    private val _themeMode = MutableStateFlow(settings.getStringOrNull(KEY_THEME_MODE))

    /** Явно выбранная тема (`"light"`/`"dark"`) либо `null`, если пользователь не переопределял системную. */
    val themeMode: StateFlow<String?> = _themeMode.asStateFlow()

    /** [mode] `null` сбрасывает выбор обратно на «следовать системе». */
    fun setThemeMode(mode: String?) {
        if (mode == null) {
            settings.remove(KEY_THEME_MODE)
        } else {
            settings.putString(KEY_THEME_MODE, mode)
        }
        _themeMode.value = mode
    }

    private companion object {
        const val KEY_THEME_MODE = "theme.mode"
    }
}
