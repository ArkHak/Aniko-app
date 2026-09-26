package com.aniko.data.geo

import com.aniko.model.UserRegion
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Кэш сетевого региона ([GeoRegionStore], см. его KDoc): маппинг кода страны, TTL-гейтинг
 * (свежий персист — без запроса, протухший — с запросом), fail-open на сбое API (значение и
 * персист не меняются). Подход к фейкам — как в соседних тестах сторов (`MapSettings` +
 * простой фейк интерфейса вместо мока инфраструктуры).
 */
class GeoRegionStoreTest {
    private val settings = MapSettings()

    @Test
    fun ruCountryCodeMapsToRussia() =
        runTest {
            val api = FakeGeoRegionApi("RU")
            val store = GeoRegionStore(settings, api, clock = MutableClock(NOW_MS))

            store.refreshIfStale()

            assertEquals(UserRegion.RUSSIA, store.region.value)
            assertEquals(1, api.calls)
            // Результат персистится с новым штампом — перезапуск приложения увидит его без сети.
            val restarted = GeoRegionStore(settings, api, clock = MutableClock(NOW_MS))
            assertEquals(UserRegion.RUSSIA, restarted.region.value)
        }

    @Test
    fun nonRuCountryCodeMapsToOther() =
        runTest {
            val store = GeoRegionStore(settings, FakeGeoRegionApi("NL"), clock = MutableClock(NOW_MS))

            store.refreshIfStale()

            assertEquals(UserRegion.OTHER, store.region.value)
        }

    @Test
    fun countryCodeIsCaseInsensitiveAndTrimmed() =
        runTest {
            val store = GeoRegionStore(settings, FakeGeoRegionApi(" ru "), clock = MutableClock(NOW_MS))

            store.refreshIfStale()

            assertEquals(UserRegion.RUSSIA, store.region.value)
        }

    @Test
    fun freshPersistSkipsNetwork() =
        runTest {
            settings.putString(GeoRegionStore.KEY_COUNTRY_CODE, "NL")
            settings.putLong(GeoRegionStore.KEY_CHECKED_AT, NOW_MS - TTL_MILLIS + 1)
            val api = FakeGeoRegionApi("RU")
            val store = GeoRegionStore(settings, api, clock = MutableClock(NOW_MS))

            store.refreshIfStale()

            assertEquals(0, api.calls)
            assertEquals(UserRegion.OTHER, store.region.value)
        }

    @Test
    fun stalePersistTriggersRequest() =
        runTest {
            settings.putString(GeoRegionStore.KEY_COUNTRY_CODE, "RU")
            settings.putLong(GeoRegionStore.KEY_CHECKED_AT, NOW_MS - TTL_MILLIS - 1)
            val api = FakeGeoRegionApi("NL")
            val store = GeoRegionStore(settings, api, clock = MutableClock(NOW_MS))

            store.refreshIfStale()

            assertEquals(1, api.calls)
            assertEquals(UserRegion.OTHER, store.region.value)
        }

    @Test
    fun noPersistStartsAsUnknown() {
        val api = FakeGeoRegionApi("RU")
        val store = GeoRegionStore(settings, api, clock = MutableClock(NOW_MS))

        assertEquals(UserRegion.UNKNOWN, store.region.value)
        assertEquals(0, api.calls)
    }

    @Test
    fun apiFailureKeepsCurrentValueAndPersist() =
        runTest {
            settings.putString(GeoRegionStore.KEY_COUNTRY_CODE, "RU")
            settings.putLong(GeoRegionStore.KEY_CHECKED_AT, NOW_MS - TTL_MILLIS - 1)
            val checkedAtBefore = settings.getLong(GeoRegionStore.KEY_CHECKED_AT, 0L)
            val store = GeoRegionStore(settings, FakeGeoRegionApi(null), clock = MutableClock(NOW_MS))

            store.refreshIfStale()

            // Fail-open: сбой определения региона не меняет ни значение, ни персист.
            assertEquals(UserRegion.RUSSIA, store.region.value)
            assertEquals("RU", settings.getStringOrNull(GeoRegionStore.KEY_COUNTRY_CODE))
            assertEquals(checkedAtBefore, settings.getLong(GeoRegionStore.KEY_CHECKED_AT, 0L))
        }

    private class FakeGeoRegionApi(
        private val result: String?,
    ) : GeoRegionApi {
        var calls = 0
            private set

        override suspend fun countryCode(): String? {
            calls++
            return result
        }
    }

    private class MutableClock(
        private val nowMs: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
    }

    private companion object {
        const val NOW_MS = 1_757_500_000_000L // 2026-09-11, произвольная «сейчас» для тестов.
        const val TTL_MILLIS = GeoRegionStore.TTL_MILLIS
    }
}
