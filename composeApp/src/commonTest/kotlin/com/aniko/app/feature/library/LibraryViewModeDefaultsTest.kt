package com.aniko.app.feature.library

import com.aniko.data.librarypreferences.LibraryViewMode
import com.aniko.ui.adaptive.AnixWindowSize
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Вид «Мои списки» по умолчанию для размера окна — пока пользователь ничего не выбирал. Это ровно
 * то, что экран показывал на каждом размере окна до появления переключателя, поэтому у тех, кто им
 * не пользуется, ничего не меняется (см. [defaultLibraryViewMode]).
 */
class LibraryViewModeDefaultsTest {
    @Test
    fun compactDefaultsToList() {
        assertEquals(LibraryViewMode.List, AnixWindowSize.Compact.defaultLibraryViewMode())
    }

    @Test
    fun mediumDefaultsToGrid() {
        assertEquals(LibraryViewMode.Grid, AnixWindowSize.Medium.defaultLibraryViewMode())
    }

    @Test
    fun expandedDefaultsToList() {
        assertEquals(LibraryViewMode.List, AnixWindowSize.Expanded.defaultLibraryViewMode())
    }

    @Test
    fun everyWindowSizeHasADefault() {
        AnixWindowSize.entries.forEach { size -> size.defaultLibraryViewMode() }
    }
}
