package com.aniko.data.librarypreferences

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Вид «Мои списки»: дефолт «не выбирал», персистентность между запусками, устойчивость к мусору. */
class LibraryPreferencesStoreTest {
    @Test
    fun defaultIsNoExplicitChoice() {
        assertNull(LibraryPreferencesStore(MapSettings()).viewMode.value)
    }

    @Test
    fun selectedModeIsExposedAndPersisted() {
        val settings = MapSettings()
        val store = LibraryPreferencesStore(settings)

        store.setViewMode(LibraryViewMode.Grid)

        assertEquals(LibraryViewMode.Grid, store.viewMode.value)
        // Новый инстанс на тех же Settings — как перезапуск приложения.
        assertEquals(LibraryViewMode.Grid, LibraryPreferencesStore(settings).viewMode.value)
    }

    @Test
    fun everyModeRoundTrips() {
        val settings = MapSettings()
        val store = LibraryPreferencesStore(settings)
        LibraryViewMode.entries.forEach { mode ->
            store.setViewMode(mode)
            assertEquals(mode, store.viewMode.value)
            assertEquals(mode, LibraryPreferencesStore(settings).viewMode.value)
        }
    }

    @Test
    fun resettingToNull_removesTheStoredValue() {
        val settings = MapSettings()
        val store = LibraryPreferencesStore(settings)
        store.setViewMode(LibraryViewMode.List)

        store.setViewMode(null)

        assertNull(store.viewMode.value)
        assertNull(LibraryPreferencesStore(settings).viewMode.value)
        assertNull(settings.getStringOrNull("library.view_mode"))
    }

    @Test
    fun garbageInSettings_isReadAsNoChoice() {
        assertNull(LibraryPreferencesStore(MapSettings("library.view_mode" to "carousel")).viewMode.value)
        assertNull(LibraryPreferencesStore(MapSettings("library.view_mode" to "")).viewMode.value)
    }

    @Test
    fun storedValueIsAStableString_notAnOrdinal() {
        val settings = MapSettings()
        val store = LibraryPreferencesStore(settings)

        store.setViewMode(LibraryViewMode.List)
        assertEquals("list", settings.getStringOrNull("library.view_mode"))

        store.setViewMode(LibraryViewMode.Grid)
        assertEquals("grid", settings.getStringOrNull("library.view_mode"))
    }

    @Test
    fun toggledSwapsTheTwoModes() {
        assertEquals(LibraryViewMode.Grid, LibraryViewMode.List.toggled())
        assertEquals(LibraryViewMode.List, LibraryViewMode.Grid.toggled())
    }
}
