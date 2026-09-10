package com.aniko.app.navigation

import com.aniko.model.CatalogContentType
import com.aniko.model.CatalogFilter
import com.aniko.model.CatalogSort
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

    // ---- Ссылка на набор фильтров каталога (P16.T2, «Моя вкладка») -----------------------------

    @Test
    fun parseCatalogFilterLink_defaultFilter_roundTripsThroughFormat() {
        val filter = CatalogFilter()

        val link = formatCatalogFilterLink(filter)
        val parsed = parseCatalogFilterLink(link)

        assertEquals("aniko://catalog", link)
        assertEquals(filter, parsed)
    }

    @Test
    fun parseCatalogFilterLink_fullFilter_roundTripsThroughFormat() {
        val filter =
            CatalogFilter(
                contentType = CatalogContentType.DONGHUA,
                sort = CatalogSort.RATING,
                statusId = 2,
                genres = setOf("экшен", "драма"),
                genresExcludeMode = true,
                startYear = 2010,
                endYear = 2020,
            )

        val parsed = parseCatalogFilterLink(formatCatalogFilterLink(filter))

        assertEquals(filter, parsed)
    }

    @Test
    fun parseCatalogFilterLink_unknownHost_returnsNull() {
        assertNull(parseCatalogFilterLink("aniko://release/42"))
    }

    @Test
    fun parseCatalogFilterLink_unknownScheme_returnsNull() {
        assertNull(parseCatalogFilterLink("https://catalog?type=donghua"))
    }

    @Test
    fun parseCatalogFilterLink_unknownParams_areIgnored() {
        val parsed = parseCatalogFilterLink("aniko://catalog?type=donghua&bogus=1&status=oops")

        assertEquals(CatalogContentType.DONGHUA, parsed?.contentType)
        assertNull(parsed?.statusId)
    }

    @Test
    fun parseCatalogFilterLink_genreIndexOutOfRange_isSkipped() {
        val parsed = parseCatalogFilterLink("aniko://catalog?genres=0,9999")

        assertEquals(1, parsed?.genres?.size)
    }

    @Test
    fun parseCatalogFilterLink_excludeWithoutGenres_isIgnored() {
        val parsed = parseCatalogFilterLink("aniko://catalog?exclude=1")

        assertEquals(false, parsed?.genresExcludeMode)
    }
}
