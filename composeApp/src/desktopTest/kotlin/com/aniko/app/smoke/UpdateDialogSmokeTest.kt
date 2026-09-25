package com.aniko.app.smoke

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.aniko.app.feature.update.UpdateDialog
import com.aniko.app.feature.update.UpdateDialogActions
import com.aniko.app.feature.update.UpdateSettingsRow
import com.aniko.data.update.AppRelease
import com.aniko.data.update.AppVersion
import com.aniko.data.update.UpdateError
import com.aniko.data.update.UpdateState
import com.aniko.ui.i18n.EnStrings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Диалог обновления и строка настроек — по состояниям [UpdateState], без Koin и без сети. */
@OptIn(ExperimentalTestApi::class)
class UpdateDialogSmokeTest {
    private val release =
        AppRelease(
            version = AppVersion(0, 2, 0),
            tag = "v0.2.0",
            title = "Aniko v0.2.0",
            notes = "## What's new\n- Faster catalog\n- Fixed the player\n\n## Download\n| a | b |",
            pageUrl = "https://github.com/ArkHak/Aniko-app/releases/tag/v0.2.0",
            publishedAt = null,
            assets = emptyList(),
        )

    private class Recorder {
        val calls = mutableListOf<String>()

        fun actions() =
            UpdateDialogActions(
                onInstall = { calls += "install" },
                onLater = { calls += "later" },
                onSkip = { calls += "skip" },
                onCancel = { calls += "cancel" },
            )
    }

    private fun dialog(
        state: UpdateState,
        canInstallInApp: Boolean = true,
        recorder: Recorder = Recorder(),
        block: androidx.compose.ui.test.SkikoComposeUiTest.() -> Unit,
    ) = runSkikoComposeUiTest(size = Size(900f, 900f), density = Density(1f)) {
        setContent {
            AppTheme(darkTheme = false) { UpdateDialog(state, canInstallInApp, recorder.actions()) }
        }
        block()
    }

    @Test
    fun availableShowsOnlyTheWhatsNewSectionAndAllThreeActions() {
        val recorder = Recorder()
        dialog(UpdateState.Available(release), recorder = recorder) {
            onNodeWithText("Version 0.2.0 is available").assertExists()
            onNodeWithText(EnStrings.updateWhatsNew).assertExists()
            onNodeWithText("Faster catalog", substring = true).assertExists()
            onNodeWithText("Download", substring = true).assertDoesNotExist() // секция «Download» на страницу релиза

            onNodeWithText(EnStrings.updateActionInstall).performClick()
            onNodeWithText(EnStrings.updateActionLater).performClick()
            onNodeWithText(EnStrings.updateActionSkip).performClick()
        }
        assertEquals(listOf("install", "later", "skip"), recorder.calls)
    }

    @Test
    fun withoutInAppInstallOffersTheReleasePageAndAManualHint() {
        dialog(UpdateState.Available(release), canInstallInApp = false) {
            onNodeWithText(EnStrings.updateActionOpenPage).assertExists()
            onNodeWithText(EnStrings.updateManualInstallHint).assertExists()
            onNodeWithText(EnStrings.updateActionInstall).assertDoesNotExist()
        }
    }

    @Test
    fun downloadingShowsPercentAndCanBeCancelled() {
        val recorder = Recorder()
        dialog(UpdateState.Downloading(release, 0.42f), recorder = recorder) {
            onNodeWithText("Downloading the update… 42%").assertExists()
            onNodeWithText(EnStrings.updateActionCancel).performClick()
        }
        assertEquals(listOf("cancel"), recorder.calls)
    }

    @Test
    fun installingHasNoActions() {
        dialog(UpdateState.Installing(release)) {
            onNodeWithText(EnStrings.updateInstalling).assertExists()
            onNodeWithText(EnStrings.updateActionCancel).assertDoesNotExist()
        }
    }

    @Test
    fun failureShowsTheReasonAndOffersRetry() {
        val recorder = Recorder()
        dialog(UpdateState.Failed(UpdateError.ChecksumMismatch, release), recorder = recorder) {
            onNodeWithText(EnStrings.updateErrorChecksum).assertExists()
            onNodeWithText(EnStrings.commonRetry).performClick()
        }
        assertEquals(listOf("install"), recorder.calls)
    }

    @Test
    fun androidAsksForTheInstallPermission() {
        dialog(UpdateState.NeedsInstallPermission(release)) {
            onNodeWithText(EnStrings.updatePermissionRequired).assertExists()
        }
    }

    @Test
    fun idleAndUpToDateHaveNoDialog() {
        dialog(UpdateState.Idle) { onNodeWithTag(AnixTestTags.UPDATE_DIALOG).assertDoesNotExist() }
        dialog(UpdateState.UpToDate(AppVersion(0, 1, 0))) { onNodeWithTag(AnixTestTags.UPDATE_DIALOG).assertDoesNotExist() }
    }

    @Test
    fun settingsRowReflectsTheState() {
        fun row(
            state: UpdateState,
            expected: String?,
        ) = runSkikoComposeUiTest(size = Size(600f, 200f), density = Density(1f)) {
            setContent { AppTheme(darkTheme = false) { UpdateSettingsRow(state) {} } }
            val description = listOfNotNull(EnStrings.settingsCheckForUpdates, expected).joinToString(". ")
            onNodeWithContentDescription(description).assertExists()
        }
        row(UpdateState.Idle, null)
        row(UpdateState.Checking, EnStrings.settingsUpdateChecking)
        row(UpdateState.UpToDate(AppVersion(0, 1, 0)), EnStrings.settingsUpdateUpToDate)
        row(UpdateState.Available(release), "Version 0.2.0 is available")
        row(UpdateState.Failed(UpdateError.Network, null), EnStrings.updateErrorNetwork)
    }
}
