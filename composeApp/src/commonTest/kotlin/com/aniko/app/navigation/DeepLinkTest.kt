package com.aniko.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Граничные случаи [parseDeepLink] (P10.T7) — см. развёрнутое обоснование схемы и правил
 * деградации в KDoc `DeepLink.kt`.
 */
class DeepLinkTest {
    @Test
    fun parseDeepLink_releaseLink_returnsReleaseDetails() {
        val destination = parseDeepLink("aniko://release/42")
        assertEquals(AnixDestination.ReleaseDetails(releaseId = 42), destination)
    }

    @Test
    fun parseDeepLink_episodeLink_returnsReleaseDetailsWithPendingEpisode() {
        val destination = parseDeepLink("aniko://release/42/episode/7/3")
        assertEquals(
            AnixDestination.ReleaseDetails(releaseId = 42, pendingEpisodeSourceId = 7, pendingEpisodePosition = 3),
            destination,
        )
    }

    @Test
    fun parseDeepLink_episodeLinkWithZeroPosition_returnsPendingEpisode() {
        val destination = parseDeepLink("aniko://release/1/episode/2/0")
        assertEquals(
            AnixDestination.ReleaseDetails(releaseId = 1, pendingEpisodeSourceId = 2, pendingEpisodePosition = 0),
            destination,
        )
    }

    @Test
    fun parseDeepLink_trailingQueryAndFragment_areIgnored() {
        val destination = parseDeepLink("aniko://release/42?utm_source=share#top")
        assertEquals(AnixDestination.ReleaseDetails(releaseId = 42), destination)
    }

    @Test
    fun parseDeepLink_schemeIsCaseInsensitive() {
        val destination = parseDeepLink("ANIKO://release/42")
        assertEquals(AnixDestination.ReleaseDetails(releaseId = 42), destination)
    }

    @Test
    fun parseDeepLink_unknownScheme_returnsNull() {
        assertNull(parseDeepLink("https://release/42"))
    }

    @Test
    fun parseDeepLink_blankUrl_returnsNull() {
        assertNull(parseDeepLink(""))
    }

    @Test
    fun parseDeepLink_unknownFirstSegment_returnsNull() {
        assertNull(parseDeepLink("aniko://profile/42"))
    }

    @Test
    fun parseDeepLink_missingReleaseId_returnsNull() {
        assertNull(parseDeepLink("aniko://release"))
        assertNull(parseDeepLink("aniko://release/"))
    }

    @Test
    fun parseDeepLink_nonNumericReleaseId_returnsNull() {
        assertNull(parseDeepLink("aniko://release/abc"))
    }

    @Test
    fun parseDeepLink_nonPositiveReleaseId_returnsNull() {
        assertNull(parseDeepLink("aniko://release/0"))
        assertNull(parseDeepLink("aniko://release/-5"))
    }

    @Test
    fun parseDeepLink_episodeTailMissingPosition_degradesToReleaseDetails() {
        val destination = parseDeepLink("aniko://release/42/episode/7")
        assertEquals(AnixDestination.ReleaseDetails(releaseId = 42), destination)
    }

    @Test
    fun parseDeepLink_episodeTailNonNumericSourceId_degradesToReleaseDetails() {
        val destination = parseDeepLink("aniko://release/42/episode/abc/3")
        assertEquals(AnixDestination.ReleaseDetails(releaseId = 42), destination)
    }

    @Test
    fun parseDeepLink_episodeTailNonNumericPosition_degradesToReleaseDetails() {
        val destination = parseDeepLink("aniko://release/42/episode/7/abc")
        assertEquals(AnixDestination.ReleaseDetails(releaseId = 42), destination)
    }

    @Test
    fun parseDeepLink_episodeTailNonPositiveSourceId_degradesToReleaseDetails() {
        val destination = parseDeepLink("aniko://release/42/episode/0/3")
        assertEquals(AnixDestination.ReleaseDetails(releaseId = 42), destination)
    }

    @Test
    fun parseDeepLink_unrelatedPathAfterReleaseId_degradesToReleaseDetails() {
        val destination = parseDeepLink("aniko://release/42/comments")
        assertEquals(AnixDestination.ReleaseDetails(releaseId = 42), destination)
    }
}
