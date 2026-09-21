package com.aniko.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Выбор качества под настройку «по умолчанию»: точное → ближайшее нижнее → ближайшее верхнее;
 * «Авто» (`null`) ничего не переопределяет. Плюс однократное применение к хостовому меню
 * (Android/iOS) — [PreferredQualityApplier].
 */
class VideoQualityPreferenceTest {
    @Test
    fun qualityHeightOf_parsesLabelsWithOrWithoutSuffix() {
        assertEquals(720, qualityHeightOf("720p"))
        assertEquals(1080, qualityHeightOf("1080P"))
        assertEquals(480, qualityHeightOf(" 480 "))
        assertNull(qualityHeightOf("auto"))
        assertNull(qualityHeightOf("HD"))
        assertNull(qualityHeightOf(""))
    }

    @Test
    fun exactMatchWins() {
        assertEquals("720p", pickQualityForPreference(720, listOf("360p", "480p", "720p", "1080p")))
    }

    @Test
    fun missingQuality_fallsBackToNearestLower() {
        // Просили 1080, у источника максимум 720 → берём 720, а не 480.
        assertEquals("720p", pickQualityForPreference(1080, listOf("480p", "720p")))
        // Просили 720, есть 480 и 1080 → нижнее (480), а не «чуть чётче» 1080.
        assertEquals("480p", pickQualityForPreference(720, listOf("480p", "1080p")))
    }

    @Test
    fun noLowerQuality_fallsBackToNearestHigher() {
        // Просили 360, у источника 480 и 720 → ближайшее верхнее (480).
        assertEquals("480p", pickQualityForPreference(360, listOf("720p", "480p")))
    }

    @Test
    fun auto_overridesNothing() {
        assertNull(pickQualityForPreference(null, listOf("360p", "720p")))
    }

    @Test
    fun unparseableOrEmptyList_overridesNothing() {
        assertNull(pickQualityForPreference(720, emptyList()))
        assertNull(pickQualityForPreference(720, listOf("auto", "HD")))
    }

    @Test
    fun originalLabelIsReturnedNotNormalized() {
        // Меню/URL потом ищут по подписи ровно в том виде, в каком её отдал источник.
        assertEquals("720P", pickQualityForPreference(720, listOf("360P", "720P")))
        assertEquals("720", pickQualityForPreference(720, listOf("360", "720")))
    }

    @Test
    fun preferredHeights_areOrderedBestFirst() {
        assertEquals(PREFERRED_QUALITY_HEIGHTS.sortedDescending(), PREFERRED_QUALITY_HEIGHTS)
    }

    // ---- PreferredQualityApplier ----

    private fun bridgeState(
        found: Boolean = true,
        qualities: List<String> = listOf("360p", "480p", "720p"),
        current: String? = "720p",
    ) = EmbedVideoState(isVideoFound = found, availableQualities = qualities, currentQuality = current)

    @Test
    fun applier_switchesOnceWhenVideoFoundAndMenuKnown() {
        val applier = PreferredQualityApplier().apply { setPreferred(480) }

        assertEquals("480p", applier.onState(bridgeState()))
        // Повторные сообщения моста (раз в 500 мс) не должны перебивать ручной выбор пользователя.
        assertNull(applier.onState(bridgeState()))
        assertNull(applier.onState(bridgeState(current = "360p")))
    }

    @Test
    fun applier_waitsForVideoAndQualityMenu() {
        val applier = PreferredQualityApplier().apply { setPreferred(480) }

        assertNull(applier.onState(bridgeState(found = false)))
        assertNull(applier.onState(bridgeState(qualities = emptyList())))
        // Раньше ничего не «сгорело» — как только всё готово, применяется.
        assertEquals("480p", applier.onState(bridgeState()))
    }

    @Test
    fun applier_auto_neverSwitches() {
        val applier = PreferredQualityApplier().apply { setPreferred(null) }
        assertNull(applier.onState(bridgeState()))
    }

    @Test
    fun applier_alreadyOnPreferredQuality_doesNothing() {
        val applier = PreferredQualityApplier().apply { setPreferred(720) }
        assertNull(applier.onState(bridgeState(current = "720p")))
    }

    @Test
    fun applier_resetOnNewSource_appliesAgain() {
        val applier = PreferredQualityApplier().apply { setPreferred(480) }
        assertEquals("480p", applier.onState(bridgeState()))

        applier.reset()

        assertEquals("480p", applier.onState(bridgeState()))
    }

    @Test
    fun applier_preferenceChangedMidEpisode_doesNotReapply() {
        val applier = PreferredQualityApplier().apply { setPreferred(480) }
        assertEquals("480p", applier.onState(bridgeState()))

        applier.setPreferred(360)

        // Смена настройки посреди серии текущий просмотр не трогает — только следующий источник.
        assertNull(applier.onState(bridgeState(current = "480p")))
    }

    @Test
    fun applier_picksClosestWhenPreferredMissingInMenu() {
        val applier = PreferredQualityApplier().apply { setPreferred(1080) }
        assertEquals("720p", applier.onState(bridgeState(current = "480p")))
    }
}
