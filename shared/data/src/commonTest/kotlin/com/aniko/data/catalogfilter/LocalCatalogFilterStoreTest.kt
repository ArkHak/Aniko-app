package com.aniko.data.catalogfilter

import com.aniko.model.CatalogContentType
import com.aniko.model.CatalogFilter
import com.aniko.model.CatalogSort
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Тесты [LocalCatalogFilterStore] — «Моя вкладка» каталога (P16.T2).
 *
 * Точки риска: JSON round-trip теряет часть полей и пережившее рестарт значение приходит
 * усечённым (см. `savesAndRestoresFullFilter`), и битое/устаревшее значение в `Settings` валит
 * весь каталог вместо того, чтобы честно показать «вкладки нет» (см. `corruptedValue_...`).
 */
class LocalCatalogFilterStoreTest {
    @Test
    fun myTab_isNullByDefault() =
        runTest {
            val store = LocalCatalogFilterStore(MapSettings())

            assertNull(store.myTab().first())
        }

    @Test
    fun savesAndRestoresFullFilter() =
        runTest {
            val store = LocalCatalogFilterStore(MapSettings())
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

            store.save(filter)

            assertEquals(filter, store.myTab().first())
        }

    @Test
    fun clear_removesSavedTab() =
        runTest {
            val store = LocalCatalogFilterStore(MapSettings())
            store.save(CatalogFilter(contentType = CatalogContentType.DONGHUA))

            store.clear()

            assertNull(store.myTab().first())
        }

    @Test
    fun newStoreInstance_readsPreviouslySavedValue() =
        runTest {
            // Один и тот же `Settings` (как на устройстве между запусками процесса), но новый
            // инстанс стора — именно так проверяется персистентность, а не просто in-memory кэш.
            val settings = MapSettings()
            LocalCatalogFilterStore(settings).save(CatalogFilter(contentType = CatalogContentType.DONGHUA))

            val restored = LocalCatalogFilterStore(settings).myTab().first()

            assertEquals(CatalogContentType.DONGHUA, restored?.contentType)
        }

    @Test
    fun corruptedValue_degradesToNoSavedTab_insteadOfCrashing() =
        runTest {
            val settings = MapSettings()
            settings.putString("catalog.my_tab", "{not valid json")

            val store = LocalCatalogFilterStore(settings)

            assertNull(store.myTab().first())
        }
}
