package com.aniko.app.feature.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.aniko.player.EmbedVideoState
import com.aniko.player.QualitySwitchFailure
import com.aniko.ui.testing.AnixTestTags
import kotlin.test.Test

/**
 * Индикатор смены качества и уведомление о сбое (Desktop): что видит пользователь, пока libVLC
 * перезапускает поток, и после отката. Реальный плеер не нужен — композабл читает только
 * [EmbedVideoState], то есть тест на фейковом состоянии контроллера (строки — дефолтная EN-локаль).
 */
@OptIn(ExperimentalTestApi::class)
class PlayerQualitySwitchStatusTest {
    private var state by mutableStateOf(EmbedVideoState())

    private fun runStatusTest(body: SkikoComposeUiTest.() -> Unit) =
        runSkikoComposeUiTest(size = Size(400f, 300f), density = Density(1f)) {
            setContent { PlayerQualitySwitchStatus(state = state, modifier = Modifier.fillMaxSize()) }
            body()
        }

    private fun SkikoComposeUiTest.assertNoStatus() = onAllNodesWithTag(AnixTestTags.PLAYER_QUALITY_SWITCH_STATUS).assertCountEquals(0)

    @Test
    fun idle_showsNothing() =
        runStatusTest {
            assertNoStatus()
        }

    @Test
    fun switching_showsIndicatorWithTargetQuality_thenDisappears() =
        runStatusTest {
            state = EmbedVideoState(switchingQualityTo = "480p")
            waitForIdle()
            onNodeWithTag(AnixTestTags.PLAYER_QUALITY_SWITCH_STATUS).assertContentDescriptionEquals("Switching quality: 480p")

            state = EmbedVideoState(currentQuality = "480p")
            waitForIdle()
            assertNoStatus()
        }

    @Test
    fun rollback_isAnnouncedOnce_thenAutoDismissed() =
        runStatusTest {
            state = EmbedVideoState(qualitySwitchFailure = QualitySwitchFailure(id = 1, requested = "480p", restoredTo = "720p"))
            waitForIdle()
            onNodeWithTag(AnixTestTags.PLAYER_QUALITY_SWITCH_STATUS)
                .assertContentDescriptionEquals("Couldn't switch to 480p — back to 720p")

            mainClock.advanceTimeBy(FAILURE_NOTICE_WAIT_MS)
            waitForIdle()
            assertNoStatus()
        }

    @Test
    fun anotherFailure_isShownAgain() =
        runStatusTest {
            state = EmbedVideoState(qualitySwitchFailure = QualitySwitchFailure(id = 1, requested = "480p", restoredTo = "720p"))
            waitForIdle()
            mainClock.advanceTimeBy(FAILURE_NOTICE_WAIT_MS)
            waitForIdle()
            assertNoStatus()

            // Новый сбой = новый id: уведомление обязано появиться снова, а не «съесться» прошлым.
            state = EmbedVideoState(qualitySwitchFailure = QualitySwitchFailure(id = 2, requested = "360p", restoredTo = "720p"))
            waitForIdle()
            onNodeWithTag(AnixTestTags.PLAYER_QUALITY_SWITCH_STATUS)
                .assertContentDescriptionEquals("Couldn't switch to 360p — back to 720p")
        }

    @Test
    fun rollbackImpossible_messageStaysUntilUserActs() =
        runStatusTest {
            state = EmbedVideoState(qualitySwitchFailure = QualitySwitchFailure(id = 1, requested = "480p", restoredTo = null))
            waitForIdle()
            mainClock.advanceTimeBy(FAILURE_NOTICE_WAIT_MS)
            waitForIdle()

            // Плеер без картинки не должен молча «замолчать»: причина остаётся на экране.
            onNodeWithTag(AnixTestTags.PLAYER_QUALITY_SWITCH_STATUS).assertContentDescriptionEquals("Couldn't switch to 480p")
        }

    private companion object {
        /** Больше длительности показа уведомления (4 с) в композабле. */
        const val FAILURE_NOTICE_WAIT_MS = 4_500L
    }
}
