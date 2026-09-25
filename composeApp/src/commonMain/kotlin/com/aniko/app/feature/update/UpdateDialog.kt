package com.aniko.app.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.aniko.data.update.AppRelease
import com.aniko.data.update.UpdateState
import com.aniko.data.update.formatReleaseNotes
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.testing.AnixTestTags

/**
 * Диалог обновления по состоянию [UpdateState]. Без состояния и без Koin — ради тестов: всё
 * управление приходит в [actions] (см. [UpdateHost], который связывает их с `UpdateCoordinator`).
 *
 * Показывается для `Available` (что нового + «Обновить»/«Позже»/«Пропустить эту версию»),
 * `Downloading`/`Installing` (прогресс), `NeedsInstallPermission` (Android) и `Failed` с релизом
 * (причина + «Повторить»). Остальные состояния диалога не имеют.
 */
@Composable
internal fun UpdateDialog(
    state: UpdateState,
    canInstallInApp: Boolean,
    actions: UpdateDialogActions,
) {
    val strings = LocalStrings.current
    when (state) {
        is UpdateState.Available -> AvailableDialog(state.release, canInstallInApp, actions)
        is UpdateState.Downloading -> {
            val percent = state.progress?.let { strings.updateDownloadingPercent((it * PERCENT).toInt()) }
            ProgressDialog(state.release, percent ?: strings.updateDownloading, state.progress, actions.onCancel)
        }
        is UpdateState.Installing -> ProgressDialog(state.release, strings.updateInstalling, null, null)
        is UpdateState.NeedsInstallPermission ->
            MessageDialog(
                release = state.release,
                message = strings.updatePermissionRequired,
                confirm = strings.updateActionInstall to actions.onInstall,
                dismiss = strings.updateActionLater to actions.onLater,
            )
        is UpdateState.Failed ->
            state.release?.let { release ->
                MessageDialog(
                    release = release,
                    message = state.error.toMessage(strings),
                    confirm = strings.commonRetry to actions.onInstall,
                    dismiss = strings.updateActionClose to actions.onLater,
                )
            }
        else -> Unit
    }
}

@Composable
private fun AvailableDialog(
    release: AppRelease,
    canInstallInApp: Boolean,
    actions: UpdateDialogActions,
) {
    val strings = LocalStrings.current
    val notes = formatReleaseNotes(release.notes)
    AlertDialog(
        onDismissRequest = actions.onLater,
        modifier = Modifier.testTag(AnixTestTags.UPDATE_DIALOG),
        title = { Text(strings.updateAvailableTitle(release.version.toString())) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = NOTES_MAX_HEIGHT).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (notes.isNotBlank()) {
                    Text(strings.updateWhatsNew, style = MaterialTheme.typography.titleSmall)
                    Text(notes, style = MaterialTheme.typography.bodyMedium)
                }
                if (!canInstallInApp) {
                    Text(strings.updateManualInstallHint, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = actions.onSkip) { Text(strings.updateActionSkip) }
            }
        },
        confirmButton = {
            Button(onClick = actions.onInstall) {
                Text(if (canInstallInApp) strings.updateActionInstall else strings.updateActionOpenPage)
            }
        },
        dismissButton = { TextButton(onClick = actions.onLater) { Text(strings.updateActionLater) } },
    )
}

@Composable
private fun ProgressDialog(
    release: AppRelease,
    text: String,
    progress: Float?,
    onCancel: (() -> Unit)?,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        modifier = Modifier.testTag(AnixTestTags.UPDATE_DIALOG),
        title = { Text(strings.updateAvailableTitle(release.version.toString())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.padding(top = 4.dp))
                } else {
                    LinearProgressIndicator(modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton =
            onCancel?.let { cancel ->
                { TextButton(onClick = cancel) { Text(strings.updateActionCancel) } }
            },
    )
}

@Composable
private fun MessageDialog(
    release: AppRelease,
    message: String,
    confirm: Pair<String, () -> Unit>,
    dismiss: Pair<String, () -> Unit>,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = dismiss.second,
        modifier = Modifier.testTag(AnixTestTags.UPDATE_DIALOG),
        title = { Text(strings.updateAvailableTitle(release.version.toString())) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { Button(onClick = confirm.second) { Text(confirm.first) } },
        dismissButton = { TextButton(onClick = dismiss.second) { Text(dismiss.first) } },
    )
}

private const val PERCENT = 100
private val NOTES_MAX_HEIGHT = 320.dp
