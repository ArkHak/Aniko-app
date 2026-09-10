package com.aniko.data.profileshowcase

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Тесты [LocalProfilePinnedSectionStore] — локальный пин секции витрины профиля (P16.T13).
 *
 * Точки риска: значение не переживает новый инстанс стора (round-trip через [Settings]),
 * `toggle` дважды на той же секции не возвращает к «пина нет», и битое/устаревшее значение в
 * `Settings` валит чтение вместо честного `null` — тот же набор рисков, что у
 * `LocalCatalogFilterStoreTest`.
 */
class LocalProfilePinnedSectionStoreTest {
    @Test
    fun pinnedSection_isNullByDefault() =
        runTest {
            val store = LocalProfilePinnedSectionStore(MapSettings())

            assertNull(store.pinnedSection().first())
        }

    @Test
    fun newStoreInstance_readsPreviouslyPinnedSection() =
        runTest {
            // Один и тот же `Settings` (как на устройстве между запусками процесса), но новый
            // инстанс стора — так проверяется персистентность, а не in-memory кэш.
            val settings = MapSettings()
            LocalProfilePinnedSectionStore(settings).pin(ProfileShowcaseSection.ACHIEVEMENTS)

            val restored = LocalProfilePinnedSectionStore(settings).pinnedSection().first()

            assertEquals(ProfileShowcaseSection.ACHIEVEMENTS, restored)
        }

    @Test
    fun pin_replacesPreviousPin() =
        runTest {
            val store = LocalProfilePinnedSectionStore(MapSettings())
            store.pin(ProfileShowcaseSection.FAVORITE_GENRES)

            store.pin(ProfileShowcaseSection.RECENTLY_WATCHED)

            assertEquals(ProfileShowcaseSection.RECENTLY_WATCHED, store.pinnedSection().first())
        }

    @Test
    fun toggle_sameSectionTwice_clearsPin() =
        runTest {
            val store = LocalProfilePinnedSectionStore(MapSettings())

            store.toggle(ProfileShowcaseSection.STATISTICS)
            store.toggle(ProfileShowcaseSection.STATISTICS)

            assertNull(store.pinnedSection().first())
        }

    @Test
    fun toggle_differentSection_switchesPin() =
        runTest {
            val store = LocalProfilePinnedSectionStore(MapSettings())

            store.toggle(ProfileShowcaseSection.STATISTICS)
            store.toggle(ProfileShowcaseSection.ACHIEVEMENTS)

            assertEquals(ProfileShowcaseSection.ACHIEVEMENTS, store.pinnedSection().first())
        }

    @Test
    fun clear_removesSavedPin() =
        runTest {
            val store = LocalProfilePinnedSectionStore(MapSettings())
            store.pin(ProfileShowcaseSection.RECENTLY_WATCHED)

            store.clear()

            assertNull(store.pinnedSection().first())
        }

    @Test
    fun corruptedValue_degradesToNoPin_insteadOfCrashing() =
        runTest {
            val settings = MapSettings()
            settings.putString("profile.pinned_section", "NOT_A_REAL_SECTION")

            val store = LocalProfilePinnedSectionStore(settings)

            assertNull(store.pinnedSection().first())
        }
}
