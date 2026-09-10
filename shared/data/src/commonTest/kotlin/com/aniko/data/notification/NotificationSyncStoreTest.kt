package com.aniko.data.notification

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Тесты [NotificationSyncStore.lastSeenCount] (P16.T18) — бейдж непрочитанных на экране
 * уведомлений/колокольчике `ProfileScreen`.
 *
 * `lastCount`/`lastNotifiedId` (P10.T6) не покрыты здесь намеренно — вне объёма этой задачи, их
 * поведение не менялось.
 */
class NotificationSyncStoreTest {
    @Test
    fun lastSeenCount_isNullByDefault() {
        val store = NotificationSyncStore(MapSettings())

        assertNull(store.lastSeenCount)
    }

    @Test
    fun lastSeenCount_roundTripsThroughSameInstance() {
        val store = NotificationSyncStore(MapSettings())

        store.lastSeenCount = 12L

        assertEquals(12L, store.lastSeenCount)
    }

    @Test
    fun lastSeenCount_survivesNewInstance_sameUnderlyingSettings() {
        // Тот же `Settings` (как на устройстве между запусками процесса), но новый инстанс стора.
        val settings = MapSettings()
        NotificationSyncStore(settings).lastSeenCount = 8L

        val restored = NotificationSyncStore(settings).lastSeenCount

        assertEquals(8L, restored)
    }

    @Test
    fun clear_removesLastSeenCount() {
        val store = NotificationSyncStore(MapSettings())
        store.lastSeenCount = 5L

        store.clear()

        assertNull(store.lastSeenCount)
    }

    @Test
    fun settingLastSeenCountToNull_removesKey() {
        val store = NotificationSyncStore(MapSettings())
        store.lastSeenCount = 5L

        store.lastSeenCount = null

        assertNull(store.lastSeenCount)
    }
}
