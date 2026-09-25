package com.aniko.app.feature.update

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.quitApplication
import com.aniko.data.update.UpdateCoordinator
import com.aniko.data.update.UpdateEffect
import com.aniko.data.update.UpdateState
import com.aniko.ui.i18n.LocalStrings
import org.koin.compose.koinInject

/**
 * Связывает [UpdateCoordinator] с интерфейсом: тихая проверка обновлений при старте (не чаще раза
 * в сутки, не блокирует запуск — уходит в фоновую корутину координатора), диалог обновления и
 * разовые действия (открыть страницу релиза, завершить приложение перед заменой на macOS).
 *
 * Ставится внутрь авторизованной части приложения: диалог поверх экрана входа не нужен, а сама
 * проверка от аккаунта не зависит.
 */
@Composable
fun UpdateHost(coordinator: UpdateCoordinator = koinInject()) {
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(coordinator) { coordinator.checkOnStart() }
    LaunchedEffect(coordinator, uriHandler) {
        coordinator.effects.collect { effect ->
            when (effect) {
                is UpdateEffect.OpenUrl -> uriHandler.openUri(effect.url)
                UpdateEffect.ExitApplication -> quitApplication()
            }
        }
    }
    val state by coordinator.state.collectAsStateWithLifecycle()
    val promptOpen by coordinator.promptOpen.collectAsStateWithLifecycle()
    if (promptOpen) {
        val actions =
            remember(coordinator) {
                UpdateDialogActions(
                    onInstall = coordinator::startUpdate,
                    onLater = coordinator::later,
                    onSkip = coordinator::skipVersion,
                    onCancel = coordinator::cancel,
                )
            }
        UpdateDialog(state = state, canInstallInApp = coordinator.canInstallInApp, actions = actions)
    }
}

/**
 * Пункт настроек «Проверить обновления» со статусом под заголовком. Тап по «Доступна версия X»
 * снова открывает диалог, в остальных случаях запускает ручную проверку.
 */
@Composable
fun UpdateSettingsItem(coordinator: UpdateCoordinator = koinInject()) {
    val state by coordinator.state.collectAsStateWithLifecycle()
    UpdateSettingsRow(state = state) {
        if (state is UpdateState.Available || state is UpdateState.NeedsInstallPermission) {
            coordinator.openPrompt()
        } else {
            coordinator.checkNow()
        }
    }
}

/** Строка настроек по состоянию: проверяем / последняя версия / доступна X / причина сбоя. Без Koin — ради тестов. */
@Composable
internal fun UpdateSettingsRow(
    state: UpdateState,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing
    val status: String? =
        when (state) {
            UpdateState.Checking -> strings.settingsUpdateChecking
            is UpdateState.UpToDate -> strings.settingsUpdateUpToDate
            is UpdateState.Available -> strings.settingsUpdateAvailable(state.release.version.toString())
            is UpdateState.Downloading -> strings.updateDownloading
            is UpdateState.Installing -> strings.updateInstalling
            is UpdateState.NeedsInstallPermission -> strings.settingsUpdateAvailable(state.release.version.toString())
            is UpdateState.Failed -> state.error.toMessage(strings)
            UpdateState.Idle -> null
        }
    val description = listOfNotNull(strings.settingsCheckForUpdates, status).joinToString(". ")
    ListItem(
        headlineContent = { Text(strings.settingsCheckForUpdates) },
        supportingContent = status?.let { text -> { Text(text) } },
        modifier =
            Modifier
                .clickable(enabled = !busy, onClick = onClick)
                .clearAndSetSemantics { contentDescription = description },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
