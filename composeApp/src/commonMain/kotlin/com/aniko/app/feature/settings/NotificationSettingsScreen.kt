package com.aniko.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.notification.NotificationPermissionState
import com.aniko.app.notification.rememberNotificationPermissionState
import com.aniko.model.NotificationPreferenceToggle
import com.aniko.model.NotificationPreferences
import com.aniko.ui.component.AnixErrorBox
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.AnixLoadingBox
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Секция уведомлений экрана настроек (P10.T6), открывается из [SettingsScreen].
 *
 * Серверные тумблеры ([NotificationPreferences]) не единственная часть экрана: сверху — блок про
 * runtime-разрешение ОС ([rememberNotificationPermissionState], `null` на платформах, где такого
 * разрешения не существует) и заметка про то, что доставка идёт опросом (polling), а не пушем —
 * см. решение P10.T4 в `docs/REELWAVE_PLAN.md` про отказ от FCM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val permission = rememberNotificationPermissionState()

    Scaffold(
        modifier = modifier.testTag(AnixTestTags.NOTIFICATION_SETTINGS_SCREEN_ROOT),
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsNotificationsSection) },
                navigationIcon = {
                    // Подтверждено на устройстве (Фаза 11, T9): IconButton не сливает
                    // Icon.contentDescription в свой кликабельный узел.
                    IconButton(
                        onClick = onBack,
                        modifier =
                            Modifier.clearAndSetSemantics { contentDescription = strings.backContentDescription },
                    ) {
                        AnixIcon(
                            name = "arrow_back",
                            contentDescription = null,
                        )
                    }
                },
                // Тот же «белая полоска» баг, что был на SettingsScreen/ProfileScreen (см. KDoc
                // SettingsTopBar): дефолтный containerColor M3 TopAppBar (surface, белый) не
                // совпадает с фоном страницы (background) — красим шапку в фон страницы.
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        NotificationSettingsContent(
            uiState = uiState,
            permission = permission,
            viewModel = viewModel,
            strings = strings,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
}

@Composable
private fun NotificationSettingsContent(
    uiState: NotificationSettingsUiState,
    permission: NotificationPermissionState?,
    viewModel: NotificationSettingsViewModel,
    strings: Strings,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        // Дизайн-leftovers (фазы 14/15): chrome-роут на desktop — ограничиваем ширину контента
        // по аналогии с ProfileScreen/SettingsScreen.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .widthIn(max = dimens.contentMaxWidth),
        ) {
            if (permission != null && !permission.isGranted) {
                ListItem(
                    headlineContent = { Text(strings.settingsNotificationsPermissionRequired) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    trailingContent = {
                        // Подтверждено на устройстве (Фаза 11, T9): M3 Button не сливает свой Text{}
                        // в озвучиваемый узел (тот же паттерн, что и остальные M3-компоненты фазы).
                        Button(
                            onClick = permission::request,
                            modifier =
                                Modifier.clearAndSetSemantics {
                                    contentDescription = strings.settingsNotificationsPermissionGrant
                                },
                        ) {
                            Text(strings.settingsNotificationsPermissionGrant)
                        }
                    },
                )
                HorizontalDivider()
            }

            Text(
                text = strings.settingsNotificationsPollingNote,
                style = MaterialTheme.typography.bodySmall,
                // Track A leftovers: вторичный текст chrome-роутов → точный t2-токен вместо
                // дефолтного onSurfaceVariant (полный on-surface), чтобы совпадать с остальными
                // экранами (hero-meta, section labels и т.д.).
                color = AnixThemeTokens.colors.textSecondary60,
                modifier = Modifier.padding(dimens.spaceM),
            )

            when {
                uiState.isLoading && uiState.preferences == null ->
                    AnixLoadingBox(modifier = Modifier.fillMaxSize())

                uiState.error != null && uiState.preferences == null ->
                    AnixErrorBox(
                        message = strings.settingsNotificationsLoadError,
                        onRetry = viewModel::load,
                        modifier = Modifier.fillMaxSize(),
                    )

                else -> {
                    val preferences = uiState.preferences
                    if (preferences != null) {
                        PreferenceToggles(
                            preferences = preferences,
                            enabled = !uiState.isLoading,
                            onToggle = viewModel::toggle,
                            strings = strings,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceToggles(
    preferences: NotificationPreferences,
    enabled: Boolean,
    onToggle: (NotificationPreferenceToggle) -> Unit,
    strings: Strings,
) {
    Column {
        ToggleRow(
            label = strings.settingsNotificationEpisodes,
            checked = preferences.episodes,
            enabled = enabled,
            onCheckedChange = { onToggle(NotificationPreferenceToggle.EPISODES) },
        )
        ToggleRow(
            label = strings.settingsNotificationFirstEpisode,
            checked = preferences.firstEpisode,
            enabled = enabled,
            onCheckedChange = { onToggle(NotificationPreferenceToggle.FIRST_EPISODE) },
        )
        ToggleRow(
            label = strings.settingsNotificationRelatedReleases,
            checked = preferences.relatedReleases,
            enabled = enabled,
            onCheckedChange = { onToggle(NotificationPreferenceToggle.RELATED_RELEASES) },
        )
        ToggleRow(
            label = strings.settingsNotificationArticles,
            checked = preferences.articles,
            enabled = enabled,
            onCheckedChange = { onToggle(NotificationPreferenceToggle.ARTICLES) },
        )
        ToggleRow(
            label = strings.settingsNotificationComments,
            checked = preferences.comments,
            enabled = enabled,
            onCheckedChange = { onToggle(NotificationPreferenceToggle.COMMENTS) },
        )
        ToggleRow(
            label = strings.settingsNotificationMyCollectionComments,
            checked = preferences.myCollectionComments,
            enabled = enabled,
            onCheckedChange = { onToggle(NotificationPreferenceToggle.MY_COLLECTION_COMMENTS) },
        )
        ToggleRow(
            label = strings.settingsNotificationMyArticleComments,
            checked = preferences.myArticleComments,
            enabled = enabled,
            onCheckedChange = { onToggle(NotificationPreferenceToggle.MY_ARTICLE_COMMENTS) },
        )
        ToggleRow(
            label = strings.settingsNotificationReportProcess,
            checked = preferences.reportProcess,
            enabled = enabled,
            onCheckedChange = { onToggle(NotificationPreferenceToggle.REPORT_PROCESS) },
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        // См. SettingsScreen — дефолтный containerColor (surface, белый) на серой странице
        // читается белой простынёй, поэтому прозрачный.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        trailingContent = {
            // Подтверждено на устройстве (Фаза 11, T9): M3 Switch не наследует имя от соседнего
            // ListItem.headlineContent — TalkBack озвучивал переключатель без указания, что
            // именно он включает/выключает.
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier =
                    Modifier.clearAndSetSemantics {
                        contentDescription = label
                        role = Role.Switch
                        toggleableState = ToggleableState(checked)
                    },
            )
        },
    )
}
