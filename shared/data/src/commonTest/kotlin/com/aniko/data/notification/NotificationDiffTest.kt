package com.aniko.data.notification

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Тесты [unreadBadgeCount] (P16.T18) — бейдж непрочитанных на колокольчике `ProfileScreen`.
 *
 * `selectUnseen`/`highestIdOrNull` в этом же файле (P10.T6) не покрыты здесь намеренно — вне
 * объёма этой задачи, их поведение не менялось.
 */
class NotificationDiffTest {
    @Test
    fun firstEverOpen_showsFullCurrentCount() {
        // lastSeenCount == null — экран уведомлений ни разу не открывался на этом устройстве.
        assertEquals(5L, unreadBadgeCount(currentCount = 5L, lastSeenCount = null))
    }

    @Test
    fun countGrewSinceLastSeen_showsFullCurrentCount() {
        assertEquals(7L, unreadBadgeCount(currentCount = 7L, lastSeenCount = 3L))
    }

    @Test
    fun countUnchangedSinceLastSeen_hidesBadge() {
        assertEquals(0L, unreadBadgeCount(currentCount = 4L, lastSeenCount = 4L))
    }

    @Test
    fun countDroppedSinceLastSeen_hidesBadge() {
        // Например, пользователь прочитал уведомления в официальном клиенте между тиками —
        // отрицательного бейджа быть не должно.
        assertEquals(0L, unreadBadgeCount(currentCount = 1L, lastSeenCount = 4L))
    }
}
