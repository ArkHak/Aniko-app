package com.aniko.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.aniko.ui.component.AnixLanguagePicker
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран настроек: галерея дизайн-токенов, уведомления, переключатель языка (P5.T9 — канонический
 * дом переключателя на мобильных/таблетах; на Desktop он же дублируется в `sidebarFooter`
 * `AdaptiveScaffold`) и выход из аккаунта.
 *
 * P13.T2 (сверка с мокапом Claude Design) убрала отсюда два пункта:
 * - «Мой профиль» — раньше этот экран был вкладкой таб-бара и открывал профиль сам, теперь
 *   наоборот: [com.aniko.app.feature.profile.ProfileScreen] стал вкладкой, а этот экран открывается
 *   ИЗ него (шестерёнка в его `TopAppBar`) — обратная навигация сюда через «Мой профиль» была бы
 *   бессмысленным циклом;
 * - переключатель темы (`AnixThemePicker`) — физически переехал в `ProfileScreen.kt`
 *   (`ProfileContent`), мокап рисует его прямо под шапкой профиля. Переключатель языка НЕ
 *   переехал вместе с ним — решение принято по умолчанию (план оставлял выбор): мокап явно требует
 *   переноса только Theme, а перенос языка добавил бы асимметрию с уже существующим дублем в
 *   `sidebarFooter` без видимой пользы.
 *
 * Из-за первого пункта экран больше не таб-рут — теперь это дочерний маршрут со своим `TopAppBar`
 * (заголовок + кнопка «назад» на [onBack]), тем же паттерном, что [NotificationSettingsScreen]/
 * `TokenGalleryScreen`.
 *
 * Язык читается/пишется через [languageTag]/[onLanguageTagChange], а не через свой Koin-инжект
 * `LocaleStore` внутри `SettingsViewModel` — экран остаётся тонким прокси без собственного стейта
 * (см. критерий миграции на MVI-контракт в журнале Фазы 5: `SettingsViewModel` НЕ мигрирует).
 */
@Suppress("LongParameterList") // 7 опциональных колбэков/параметров одного плоского экрана без
// собственного стейта (см. KDoc выше про критерий немиграции на MVI) — группировка в data class
// ради обхода линта добавила бы косвенность без пользы для читаемости.
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onDesignGalleryClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    languageTag: String? = null,
    onLanguageTagChange: (String?) -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens

    Scaffold(
        modifier = modifier.testTag(AnixTestTags.SETTINGS_SCREEN_ROOT),
        topBar = { SettingsTopBar(onBack = onBack) },
    ) { innerPadding ->
        // Подтверждено на устройстве (Фаза 11, T9): M3 ListItem не сливает headlineContent в свой
        // кликабельный узел (тот же паттерн, что и остальные M3-компоненты этой фазы) —
        // clearAndSetSemantics на каждом пункте, кроме языка (у него нет своего onClick — переключение
        // происходит через AnixLanguagePicker внутри, уже озвученный отдельно).
        Surface(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
}

/**
 * `TopAppBar` экрана настроек — вынесена из [SettingsScreen] отдельной функцией (detekt
 * `LongMethod`: экран стал дочерним маршрутом со своим `TopAppBar` в P13.T2, тело функции
 * перестало укладываться в лимит).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    val strings = LocalStrings.current
    TopAppBar(
        title = { Text(strings.settingsTitle) },
        navigationIcon = {
            // Подтверждено на устройстве (Фаза 11, T9): IconButton не сливает
            // Icon.contentDescription в свой кликабельный узел.
            IconButton(
                onClick = onBack,
                modifier = Modifier.clearAndSetSemantics { contentDescription = strings.backContentDescription },
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        },
    )
}

/**
 * Строка настроек «заголовок + чипы выбора» — каркас для пункта языка (изначально общий с темой,
 * пока `AnixThemePicker` не переехал на `ProfileScreen` в P13.T2; функция осталась отдельной ради
 * симметрии с тем, как аналогичная строка используется на `ProfileScreen` для темы). У пункта нет
 * своего `onClick` — переключение происходит внутри [content] (см. `AnixLanguagePicker`), поэтому
 * `clearAndSetSemantics` здесь не нужен.
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
