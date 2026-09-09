package com.aniko.data.theme

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Хранилище выбора иконки приложения (P16.T21).
 *
 * Тот же паттерн, что [ThemeStore] — обычный [Settings] (значение не секретное), синглтон в
 * `dataModule`. В отличие от [ThemeStore] здесь нет `null`-состояния «не выбрано»: иконка всегда
 * определена, дефолт — [DEFAULT_ICON] ("main", единственная реально существующая
 * `MainActivity` — остальные три ключа резолвятся в `activity-alias` в `AndroidManifest.xml`,
 * применяются на Android через `AppIconHelper`). Само переключение системных компонентов
 * (`PackageManager.setComponentEnabledSetting`) — забота вызывающего кода в `composeApp`
 * (`AndroidAppIconHelper`), не этого класса: `shared/data` намеренно не зависит от Android SDK.
 */
class AppIconStore(
    private val settings: Settings,
) {
    private val _iconKey = MutableStateFlow(settings.getStringOrNull(KEY_ICON)?.let(::validate) ?: DEFAULT_ICON)

    /** Ключ выбранной иконки — один из [SUPPORTED_ICONS], дефолт [DEFAULT_ICON]. */
    val iconKey: StateFlow<String> = _iconKey.asStateFlow()

    /** [key] вне [SUPPORTED_ICONS] (включая `null`) откатывается на [DEFAULT_ICON]. */
    fun setIconKey(key: String?) {
        val validated = key?.let(::validate) ?: DEFAULT_ICON
        settings.putString(KEY_ICON, validated)
        _iconKey.value = validated
    }

    companion object {
        /** Ключ иконки "Основная" — единственный, у которого нет `activity-alias` (сама `MainActivity`). */
        const val DEFAULT_ICON: String = "main"

        /** Все поддерживаемые ключи — совпадают с суффиксами `activity-alias` в манифесте Android. */
        val SUPPORTED_ICONS: Set<String> = setOf("main", "classic", "dream", "ice")

        private const val KEY_ICON = "app.icon"

        private fun validate(key: String): String? = key.takeIf { it in SUPPORTED_ICONS }
    }
}
