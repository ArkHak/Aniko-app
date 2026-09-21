package com.aniko.data.voicepreference

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** «Запоминание озвучки»: дефолт «нет выбора», персистентность, перезапись, очистка, стойкость к мусору. */
class TitleVoicePreferenceStoreTest {
    @Test
    fun noSavedChoiceByDefault() {
        assertNull(TitleVoicePreferenceStore(MapSettings()).load(releaseId = 1))
    }

    @Test
    fun savedPairRoundTripsAcrossInstances() {
        val settings = MapSettings()
        val store = TitleVoicePreferenceStore(settings)

        store.save(releaseId = 1, typeId = 7, sourceId = 42)

        assertEquals(
            TitleVoicePreferenceStore.SavedVoice(typeId = 7, sourceId = 42),
            store.load(releaseId = 1),
        )
        // Новый инстанс на тех же Settings — как перезапуск приложения.
        assertEquals(
            TitleVoicePreferenceStore.SavedVoice(typeId = 7, sourceId = 42),
            TitleVoicePreferenceStore(settings).load(releaseId = 1),
        )
    }

    @Test
    fun choicesAreIndependentPerRelease() {
        val store = TitleVoicePreferenceStore(MapSettings())

        store.save(releaseId = 1, typeId = 7, sourceId = 42)
        store.save(releaseId = 2, typeId = 3, sourceId = 9)

        assertEquals(TitleVoicePreferenceStore.SavedVoice(typeId = 7, sourceId = 42), store.load(releaseId = 1))
        assertEquals(TitleVoicePreferenceStore.SavedVoice(typeId = 3, sourceId = 9), store.load(releaseId = 2))
    }

    @Test
    fun reSaveOverwritesPreviousChoice() {
        val store = TitleVoicePreferenceStore(MapSettings())
        store.save(releaseId = 1, typeId = 7, sourceId = 42)

        store.save(releaseId = 1, typeId = 8, sourceId = 43)

        assertEquals(TitleVoicePreferenceStore.SavedVoice(typeId = 8, sourceId = 43), store.load(releaseId = 1))
    }

    @Test
    fun clearRemovesTheChoice() {
        val settings = MapSettings()
        val store = TitleVoicePreferenceStore(settings)
        store.save(releaseId = 1, typeId = 7, sourceId = 42)

        store.clear(releaseId = 1)

        assertNull(store.load(releaseId = 1))
        assertNull(TitleVoicePreferenceStore(settings).load(releaseId = 1))
    }

    @Test
    fun clearWithoutSavedChoiceIsNoOp() {
        TitleVoicePreferenceStore(MapSettings()).clear(releaseId = 1)
    }

    @Test
    fun garbageInSettingsIsReadAsNoChoice() {
        assertNull(TitleVoicePreferenceStore(MapSettings("title.voice.1" to "abc")).load(releaseId = 1))
        assertNull(TitleVoicePreferenceStore(MapSettings("title.voice.1" to "7")).load(releaseId = 1))
        assertNull(TitleVoicePreferenceStore(MapSettings("title.voice.1" to "7:x")).load(releaseId = 1))
        assertNull(TitleVoicePreferenceStore(MapSettings("title.voice.1" to 42)).load(releaseId = 1))
    }
}
