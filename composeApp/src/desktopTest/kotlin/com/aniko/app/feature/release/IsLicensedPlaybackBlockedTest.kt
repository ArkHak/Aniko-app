package com.aniko.app.feature.release

import com.aniko.model.Release
import com.aniko.model.ReleaseDetails
import com.aniko.model.ReleaseStreamingPlatform
import com.aniko.model.UserRegion
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Предикат блокировки воспроизведения легализованного тайтла
 * ([ReleaseDetailsUiState.isLicensedPlaybackBlocked], см. его KDoc): три независимых серверных
 * сигнала (флаг `isThirdPartyPlatformsDisabled`, `note`, непустой список легальных площадок) —
 * достаточно любого, НО блок применяется только при сетевом регионе [UserRegion.RUSSIA]
 * (geo-IP, VPN меняет результат). Предикат намеренно fail-open: ни сигналов, ни региона — либо
 * сигналы без RU-региона — обычный флоу выбора источника продолжает работать (сбой сети или
 * поездка за VPN не должны ломать воспроизведение, решение пользователя 2026-09-26).
 */
class IsLicensedPlaybackBlockedTest {
    private val release = Release(id = 1, title = "Test title")

    private fun licensedState(): ReleaseDetailsUiState =
        ReleaseDetailsUiState(
            details =
                ReleaseDetails(
                    release = release,
                    isThirdPartyPlatformsDisabled = false,
                    note = "Данный материал лицензирован на территории вашей страны.",
                ),
        )

    @Test
    fun thirdPartyPlatformsDisabledFlag_blocksPlaybackInRussia() {
        val state =
            ReleaseDetailsUiState(
                details = ReleaseDetails(release = release, isThirdPartyPlatformsDisabled = true),
                userRegion = UserRegion.RUSSIA,
            )
        assertTrue(state.isLicensedPlaybackBlocked)
    }

    @Test
    fun licensedNoteWithoutFlag_blocksPlaybackInRussia() {
        // Живые ответы на 2026-09-23: флага нет вовсе, приходит только note (см. KDoc предиката).
        val state =
            ReleaseDetailsUiState(
                details =
                    ReleaseDetails(
                        release = release,
                        isThirdPartyPlatformsDisabled = false,
                        note = "Данный материал лицензирован на территории вашей страны.",
                    ),
                userRegion = UserRegion.RUSSIA,
            )
        assertTrue(state.isLicensedPlaybackBlocked)
    }

    @Test
    fun nonEmptyStreamingPlatforms_blockPlaybackInRussia() {
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
                userRegion = UserRegion.RUSSIA,
            )
        assertTrue(state.isLicensedPlaybackBlocked)
    }

    @Test
    fun licensedSignalsDoNotBlockOutsideRussia() {
        // Сервер шлёт note/площадки по своему решению и не знает реальный регион клиента:
        // пользователь за VPN (egress-IP не РФ) смотрит обычный флоу.
        assertFalse(licensedState().copy(userRegion = UserRegion.OTHER).isLicensedPlaybackBlocked)
        assertFalse(
            licensedState()
                .copy(
                    streamingPlatforms =
                        listOf(
                            ReleaseStreamingPlatform(
                                id = 1,
                                name = "Kinopoisk",
                                iconUrl = null,
                                url = "https://example.com/1",
                            ),
                        ),
                    userRegion = UserRegion.OTHER,
                ).isLicensedPlaybackBlocked,
        )
    }

    @Test
    fun licensedSignalsDoNotBlockWhenRegionUnknown() {
        // Geo-запросы ещё идут или упали — fail-open: «не знаем регион» не блокирует доступ
        // (решение пользователя 2026-09-26, см. KDoc UserRegion).
        assertFalse(licensedState().isLicensedPlaybackBlocked)
        assertFalse(licensedState().copy(userRegion = UserRegion.UNKNOWN).isLicensedPlaybackBlocked)
    }

    @Test
    fun russiaWithoutSignals_isNotBlocked() {
        // Регион подтверждён, но ни один серверный сигнал легализации не пришёл (запросы ещё
        // идут или упали молча) — fail-open по сигналам, как и раньше.
        val state =
            ReleaseDetailsUiState(
                details = ReleaseDetails(release = release),
                streamingPlatforms = emptyList(),
                userRegion = UserRegion.RUSSIA,
            )
        assertFalse(state.isLicensedPlaybackBlocked)
    }

    @Test
    fun emptyData_failOpen() {
        // Ни сигналов, ни региона — обычный флоу выбора источника продолжает работать.
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
        assertFalse(ReleaseDetailsUiState(userRegion = UserRegion.RUSSIA).isLicensedPlaybackBlocked)
    }
}
