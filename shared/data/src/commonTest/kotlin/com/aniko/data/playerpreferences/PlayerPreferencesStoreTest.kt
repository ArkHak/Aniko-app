package com.aniko.data.playerpreferences

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** «Предпочтительное качество по умолчанию»: дефолт «Авто», персистентность, валидация значений. */
class PlayerPreferencesStoreTest {
    @Test
    fun defaultIsAuto() {
        assertNull(PlayerPreferencesStore(MapSettings()).preferredQualityHeight.value)
    }

    @Test
    fun selectedQualityIsExposedAndPersisted() {
        val settings = MapSettings()
        val store = PlayerPreferencesStore(settings)

        store.setPreferredQualityHeight(720)

        assertEquals(720, store.preferredQualityHeight.value)
        // Новый инстанс на тех же Settings — как перезапуск приложения.
        assertEquals(720, PlayerPreferencesStore(settings).preferredQualityHeight.value)
    }

    @Test
    fun switchingBackToAuto_removesTheStoredValue() {
        val settings = MapSettings()
        val store = PlayerPreferencesStore(settings)
        store.setPreferredQualityHeight(480)

        store.setPreferredQualityHeight(null)

        assertNull(store.preferredQualityHeight.value)
        assertNull(PlayerPreferencesStore(settings).preferredQualityHeight.value)
    }

    @Test
    fun unsupportedHeightFallsBackToAuto() {
        val store = PlayerPreferencesStore(MapSettings())
        store.setPreferredQualityHeight(1080)

        store.setPreferredQualityHeight(240)

        assertNull(store.preferredQualityHeight.value)
    }

    @Test
    fun garbageInSettings_isReadAsAuto() {
        val settings = MapSettings("player.preferred_quality_height" to 999)

        assertNull(PlayerPreferencesStore(settings).preferredQualityHeight.value)
    }

    @Test
    fun everyOfferedQualityRoundTrips() {
        val store = PlayerPreferencesStore(MapSettings())
        listOf(1080, 720, 480, 360).forEach { height ->
            store.setPreferredQualityHeight(height)
            assertEquals(height, store.preferredQualityHeight.value)
        }
    }
}
