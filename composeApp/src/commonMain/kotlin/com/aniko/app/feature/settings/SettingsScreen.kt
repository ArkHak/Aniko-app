package com.aniko.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.di.AppIconHelper
import com.aniko.data.theme.AppIconStore
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.AnixLanguagePicker
import com.aniko.ui.component.AnixThemePicker
import com.aniko.ui.component.ChipRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран настроек: галерея дизайн-токенов, уведомления, переключатель языка (P5.T9 — канонический
 * дом переключателя языка; с 2026-09-08 единственный — desktop-дубль в `sidebarFooter`
 * `AdaptiveScaffold` удалён по запросу пользователя: обрезался на малой высоте сайдбара),
 * переключатель темы и выход из аккаунта.
 *
 * P13.T2 (сверка с мокапом Claude Design) убрала отсюда два пункта:
 * - «Мой профиль» — раньше этот экран был вкладкой таб-бара и открывал профиль сам, теперь
 *   наоборот: [com.aniko.app.feature.profile.ProfileScreen] стал вкладкой, а этот экран открывается
 *   ИЗ него (шестерёнка в его `TopAppBar`) — обратная навигация сюда через «Мой профиль» была бы
 *   бессмысленным циклом. Это решение остаётся в силе.
 * - переключатель темы (`AnixThemePicker`) — физически переехал в `ProfileScreen.kt` под мокап
 *   Claude Design. **Живой фидбек пользователя (2026-09-11) развернул именно эту часть решения**:
 *   тема вернулась сюда, на своё место рядом с языком ([languageTag]/[onLanguageTagChange]) —
 *   [themeMode]/[onThemeModeChange] снова параметры этого экрана, `AnixThemePicker` снова
 *   рисуется здесь (см. [SettingsPickerListItem] ниже, тот же паттерн, что у пункта языка).
 *
 * Из-за первого пункта экран больше не таб-рут — теперь это дочерний маршрут со своим `TopAppBar`
 * (заголовок + кнопка «назад» на [onBack]), тем же паттерном, что [NotificationSettingsScreen]/
 * `TokenGalleryScreen`.
 *
 * Язык и тема читаются/пишутся через параметры экрана ([languageTag]/[onLanguageTagChange],
 * [themeMode]/[onThemeModeChange]), а не через свой Koin-инжект `LocaleStore`/`ThemeStore` внутри
 * `SettingsViewModel` — экран остаётся тонким прокси без собственного стейта (см. критерий
 * миграции на MVI-контракт в журнале Фазы 5: `SettingsViewModel` НЕ мигрирует).
 */
@Suppress("LongParameterList", "LongMethod") // 9 опциональных колбэков/параметров одного плоского
// экрана без собственного стейта (см. KDoc выше про критерий немиграции на MVI) — группировка
// в data class ради обхода линта добавила бы косвенность без пользы для читаемости; тело —
// линейный плоский список пунктов (ListItem), разбиение на приватную функцию-прокси добавило бы
// косвенность ради счётчика строк (тот же случай, что WatchAndFavoriteRow в ReleaseHeaderSection).
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
    val appIconStore = koinInject<AppIconStore>()
    val appIconHelper = koinInject<AppIconHelper>()

    Scaffold(
        modifier = modifier.testTag(AnixTestTags.SETTINGS_SCREEN_ROOT),
        topBar = { SettingsTopBar(onBack = onBack) },
    ) { innerPadding ->
        // Подтверждено на устройстве (Фаза 11, T9): M3 ListItem не сливает headlineContent в свой
        // кликабельный узел (тот же паттерн, что и остальные M3-компоненты этой фазы) —
        // clearAndSetSemantics на каждом пункте, кроме языка (у него нет своего onClick — переключение
        // происходит через AnixLanguagePicker внутри, уже озвученный отдельно).
        // Surface без color заливал бы весь контент `colorScheme.surface` (белым) поверх серого
        // фона страницы — прозрачный, Scaffold под ним уже красит `colorScheme.background`.
        Surface(modifier = Modifier.fillMaxSize().padding(innerPadding), color = Color.Transparent) {
            // Дизайн-leftovers (фазы 14/15): chrome-роуты на desktop не должны растягивать строки
            // на всю ширину окна — повторяем паттерн ProfileScreen: Box(TopCenter) +
            // Column(widthIn(max = contentMaxWidth)).
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .widthIn(max = dimens.contentMaxWidth)
                            .verticalScroll(rememberScrollState()),
                ) {
                    ListItem(
                        headlineContent = { Text(text = strings.settingsDesignGallery) },
                        modifier =
                            Modifier
                                .clickable(onClick = onDesignGalleryClick)
                                .clearAndSetSemantics {
                                    contentDescription = strings.settingsDesignGallery
                                },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = { Text(text = strings.settingsNotificationsSection) },
                        modifier =
                            Modifier
                                .clickable(onClick = onNotificationsClick)
                                .clearAndSetSemantics {
                                    contentDescription = strings.settingsNotificationsSection
                                },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    // Возвращено сюда по запросу пользователя (2026-09-11) — было перенесено на
                    // ProfileScreen в P13.T2, теперь снова стоит рядом с языком, на прежнем месте
                    // (см. KDoc SettingsScreen).
                    SettingsPickerListItem(headline = strings.settingsTheme) {
                        AnixThemePicker(
                            currentMode = themeMode,
                            onSelect = onThemeModeChange,
                            modifier = Modifier.padding(top = dimens.spaceXs),
                        )
                    }
                    SettingsPickerListItem(headline = strings.settingsLanguage) {
                        AnixLanguagePicker(
                            currentTag = languageTag,
                            onSelect = onLanguageTagChange,
                            modifier = Modifier.padding(top = dimens.spaceXs),
                        )
                    }
                    // P16.T21: секция скрыта, когда платформа не поддерживает несколько иконок
                    // лаунчера (Desktop/iOS — `NoOpAppIconHelper.supportedIcons` пуст).
                    if (appIconHelper.supportedIcons.isNotEmpty()) {
                        val appIconKey by appIconStore.iconKey.collectAsStateWithLifecycle()
                        SettingsPickerListItem(headline = strings.settingsAppIcon) {
                            ChipRow(
                                items = appIconHelper.supportedIcons,
                                isSelected = { it == appIconKey },
                                label = { key ->
                                    when (key) {
                                        "classic" -> strings.appIconClassic
                                        "dream" -> strings.appIconDream
                                        "ice" -> strings.appIconIce
                                        else -> strings.appIconMain
                                    }
                                },
                                onClick = { key ->
                                    appIconStore.setIconKey(key)
                                    appIconHelper.apply(key)
                                },
                                modifier = Modifier.padding(top = dimens.spaceXs),
                            )
                        }
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
                                .clearAndSetSemantics {
                                    contentDescription = strings.settingsSignOut
                                },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

/**
 * `TopAppBar` экрана настроек — вынесена из [SettingsScreen] отдельной функцией (detekt
 * `LongMethod`: экран стал дочерним маршрутом со своим `TopAppBar` в P13.T2, тело функции
 * перестало укладываться в лимит).
 *
 * Живой фидбек пользователя (2026-09-11, тот же «белая полоска» баг, что и на `ProfileScreen` —
 * см. KDoc её `TopAppBar`): дефолтный `containerColor` M3 `TopAppBar` (surface) не совпадает с
 * фоном тела экрана (`colorScheme.background`) — явно красим шапку в тот же фон, чтобы она
 * визуально сливалась со страницей.
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
                AnixIcon(name = "arrow_back", contentDescription = null)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/**
 * Строка настроек «заголовок + чипы выбора» — общий каркас для пунктов темы и языка (та же
 * функция обслуживала пункт темы и до P13.T2, и после её разворота по запросу пользователя
 * 2026-09-11 — см. KDoc [SettingsScreen]). У пункта нет своего `onClick` — переключение
 * происходит внутри [content] (см. `AnixThemePicker`/`AnixLanguagePicker`), поэтому
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
        // Дефолтный containerColor M3 ListItem в используемой версии — surface (белый), на серой
        // странице настроек это читается белой простынёй (живой фидбек 2026-09-17) — прозрачный.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
