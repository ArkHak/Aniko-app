package com.aniko.app.feature.release

import com.aniko.model.Release
import com.aniko.model.ReleaseDetails
import com.aniko.model.ReleaseStreamingPlatform
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Предикат блокировки воспроизведения легализованного тайтла
 * ([ReleaseDetailsUiState.isLicensedPlaybackBlocked], см. его KDoc): три независимых серверных
 * сигнала (флаг `isThirdPartyPlatformsDisabled`, `note`, непустой список легальных площадок) —
 * достаточно любого; при пустых данных предикат намеренно fail-open (сбой сети не должен
 * блокировать воспроизведение у обычных релизов).
 */
class IsLicensedPlaybackBlockedTest {
    private val release = Release(id = 1, title = "Test title")

    @Test
    fun thirdPartyPlatformsDisabledFlag_blocksPlayback() {
        val state =
            ReleaseDetailsUiState(
                details = ReleaseDetails(release = release, isThirdPartyPlatformsDisabled = true),
            )
        assertTrue(state.isLicensedPlaybackBlocked)
    }

    @Test
    fun licensedNoteWithoutFlag_blocksPlayback() {
        // Живые ответы на 2026-09-23: флага нет вовсе, приходит только note (см. KDoc предиката).
        val state =
            ReleaseDetailsUiState(
                details =
                    ReleaseDetails(
                        release = release,
                        isThirdPartyPlatformsDisabled = false,
                        note = "Данный материал лицензирован на территории вашей страны.",
                    ),
            )
        assertTrue(state.isLicensedPlaybackBlocked)
    }

    @Test
    fun nonEmptyStreamingPlatforms_blockPlayback() {
        val state =
            ReleaseDetailsUiState(
                details = ReleaseDetails(release = release),
                streamingPlatforms =
                    listOf(
                        ReleaseStreamingPlatform(
                            id = 1,
                            name = "Kinopoisk",
                            iconUrl = null,
                            url = "https://example.com/1",
                        ),
                    ),
            )
        assertTrue(state.isLicensedPlaybackBlocked)
    }

    @Test
    fun emptyData_failOpen() {
        // Ни один сигнал не подтверждён (запросы details/platforms ещё идут или упали молча) —
        // обычный флоу выбора источника продолжает работать.
        val state =
            ReleaseDetailsUiState(
                details = ReleaseDetails(release = release),
                streamingPlatforms = emptyList(),
            )
        assertFalse(state.isLicensedPlaybackBlocked)
    }

    @Test
    fun noDetailsAtAll_failOpen() {
        assertFalse(ReleaseDetailsUiState().isLicensedPlaybackBlocked)
    }
}
