package com.aniko.data.locale

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Хранилище выбора языка приложения (Фаза 2 плана, P2.T11).
 *
 * Тот же паттерн, что [com.aniko.data.session.SessionStore] использует для `profileId` —
 * значение не секретное, поэтому обычный [Settings] (plaintext), без secure storage.
 *
 * [languageTag] — `null` означает «нет явного выбора, следовать системной локали». Разрешение
 * `null` в реальный язык (чтение [androidx.compose.ui.text.intl.Locale.current]) — забота
 * `com.aniko.ui.i18n.ProvideAppStrings` в `shared/ui`, не этого класса: `shared/data` намеренно
 * не зависит от Compose, чтобы не тащить UI-фреймворк в слой данных.
 */
class LocaleStore(
    private val settings: Settings,
) {
    private val _languageTag = MutableStateFlow(settings.getStringOrNull(KEY_LANGUAGE_TAG))

    /** Явно выбранный язык (`"en"`/`"ru"`) либо `null`, если пользователь не переопределял системный. */
    val languageTag: StateFlow<String?> = _languageTag.asStateFlow()

    /** [tag] `null` сбрасывает выбор обратно на «следовать системе». */
    fun setLanguageTag(tag: String?) {
        if (tag == null) {
            settings.remove(KEY_LANGUAGE_TAG)
        } else {
            settings.putString(KEY_LANGUAGE_TAG, tag)
        }
        _languageTag.value = tag
    }

    private companion object {
        const val KEY_LANGUAGE_TAG = "locale.language_tag"
    }
}
