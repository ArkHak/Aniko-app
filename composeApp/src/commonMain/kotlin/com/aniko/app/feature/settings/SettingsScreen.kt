package com.aniko.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aniko.ui.component.AnixLanguagePicker
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран настроек: переход в свой профиль, галерею дизайн-токенов, переключатель языка (P5.T9 —
 * канонический дом переключателя, не только debug-галерея токенов) и выход из аккаунта.
 *
 * Язык читается/пишется через [languageTag]/[onLanguageTagChange], а не через свой Koin-инжект
 * `LocaleStore` внутри `SettingsViewModel` — экран остаётся тонким прокси без собственного стейта
 * (см. критерий миграции на MVI-контракт в журнале Фазы 5: `SettingsViewModel` НЕ мигрирует).
 */
@Suppress("LongParameterList") // 5 опциональных колбэков/параметров одного плоского экрана без
// собственного стейта (см. KDoc выше про критерий немиграции на MVI) — группировка в
// data class ради обхода линта добавила бы косвенность без пользы для читаемости на 6 полях.
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {},
    onDesignGalleryClick: () -> Unit = {},
    languageTag: String? = null,
    onLanguageTagChange: (String?) -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens

    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            ListItem(
                headlineContent = { Text(text = strings.settingsMyProfile) },
                modifier = Modifier.clickable(onClick = onProfileClick),
            )
            ListItem(
                headlineContent = { Text(text = strings.settingsDesignGallery) },
                modifier = Modifier.clickable(onClick = onDesignGalleryClick),
            )
            ListItem(
                headlineContent = { Text(text = strings.settingsLanguage) },
                supportingContent = {
                    AnixLanguagePicker(
                        currentTag = languageTag,
                        onSelect = onLanguageTagChange,
                        modifier = Modifier.padding(top = dimens.spaceXs),
                    )
                },
            )
            ListItem(
                headlineContent = {
                    Text(
                        text = strings.settingsSignOut,
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.clickable { viewModel.signOut() },
            )
        }
    }
}
