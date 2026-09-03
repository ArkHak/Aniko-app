package com.aniko.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.aniko.ui.component.AnixLanguagePicker
import com.aniko.ui.component.AnixThemePicker
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран настроек: переход в свой профиль, галерею дизайн-токенов, переключатель языка (P5.T9 —
 * канонический дом переключателя, не только debug-галерея токенов), переключатель темы и выход
 * из аккаунта.
 *
 * Язык и тема читаются/пишутся через [languageTag]/[onLanguageTagChange] и
 * [themeMode]/[onThemeModeChange], а не через свои Koin-инжекты `LocaleStore`/`ThemeStore` внутри
 * `SettingsViewModel` — экран остаётся тонким прокси без собственного стейта (см. критерий миграции
 * на MVI-контракт в журнале Фазы 5: `SettingsViewModel` НЕ мигрирует).
 */
@Suppress("LongParameterList") // 8 опциональных колбэков/параметров одного плоского экрана без
// собственного стейта (см. KDoc выше про критерий немиграции на MVI) — группировка в
// data class ради обхода линта добавила бы косвенность без пользы для читаемости на 9 полях.
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {},
    onDesignGalleryClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    languageTag: String? = null,
    onLanguageTagChange: (String?) -> Unit = {},
    themeMode: String? = null,
    onThemeModeChange: (String?) -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens

    // Подтверждено на устройстве (Фаза 11, T9): M3 ListItem не сливает headlineContent в свой
    // кликабельный узел (тот же паттерн, что и остальные M3-компоненты этой фазы) —
    // clearAndSetSemantics на каждом пункте, кроме языка (у него нет своего onClick — переключение
    // происходит через AnixLanguagePicker внутри, уже озвученный отдельно).
    Surface(modifier = modifier.fillMaxSize().testTag(AnixTestTags.SETTINGS_SCREEN_ROOT)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text(text = strings.settingsMyProfile) },
                modifier =
                    Modifier
                        .clickable(onClick = onProfileClick)
                        .clearAndSetSemantics { contentDescription = strings.settingsMyProfile },
            )
            ListItem(
                headlineContent = { Text(text = strings.settingsDesignGallery) },
                modifier =
                    Modifier
                        .clickable(onClick = onDesignGalleryClick)
                        .clearAndSetSemantics { contentDescription = strings.settingsDesignGallery },
            )
            ListItem(
                headlineContent = { Text(text = strings.settingsNotificationsSection) },
                modifier =
                    Modifier
                        .clickable(onClick = onNotificationsClick)
                        .clearAndSetSemantics { contentDescription = strings.settingsNotificationsSection },
            )
            SettingsPickerListItem(headline = strings.settingsLanguage) {
                AnixLanguagePicker(
                    currentTag = languageTag,
                    onSelect = onLanguageTagChange,
                    modifier = Modifier.padding(top = dimens.spaceXs),
                )
            }
            SettingsPickerListItem(headline = strings.settingsTheme) {
                AnixThemePicker(
                    currentMode = themeMode,
                    onSelect = onThemeModeChange,
                    modifier = Modifier.padding(top = dimens.spaceXs),
                )
            }
            ListItem(
                headlineContent = {
                    Text(
                        text = strings.settingsSignOut,
                        color = AnixThemeTokens.colors.errorText,
                    )
                },
                modifier =
                    Modifier
                        .clickable { viewModel.signOut() }
                        .clearAndSetSemantics { contentDescription = strings.settingsSignOut },
            )
        }
    }
}

/**
 * Строка настроек «заголовок + чипы выбора» — общий каркас для языка и темы (detekt `LongMethod`
 * на [SettingsScreen]: без вынесения этой пары `ListItem` в отдельную функцию тело экрана
 * превышало лимит строк). У обоих пунктов нет своего `onClick` — переключение происходит внутри
 * [content] (см. `AnixLanguagePicker`/`AnixThemePicker`), поэтому `clearAndSetSemantics` здесь не
 * нужен (тот же случай, что и был у языка до вынесения).
 */
@Composable
private fun SettingsPickerListItem(
    headline: String,
    content: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text = headline) },
        supportingContent = content,
    )
}
