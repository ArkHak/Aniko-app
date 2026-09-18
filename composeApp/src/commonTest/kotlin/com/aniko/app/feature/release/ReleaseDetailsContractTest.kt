package com.aniko.app.feature.release

import com.aniko.model.Episode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [mergeWatchedOverrides] — мерж серверного `Episode.isWatched` с локальными оверрайдами (D4).
 *
 * Ядро регресса, который это исправление устраняет: раньше [ReleaseDetailsUiState.watchedOverrides]
 * был `Set<Int>` (только позиции с `is_watched = true`), из-за чего явный локальный "не
 * просмотрено" (пользователь долгим тапом снял отметку, изменение не успело уйти на сервер) был
 * неотличим от "локально вообще не трогали" — при повторном открытии экрана серия снова
 * показывалась просмотренной, откатывая явное действие пользователя. Теперь [watchedOverrides] —
 * `Map<Int, Boolean>`, несущая обе стороны, и явный `false` должен побеждать серверный `true`.
 */
class ReleaseDetailsContractTest {
    @Test
    fun explicitFalseInWatchedOverrides_winsOverServerIsWatchedTrue() {
        val episodes = listOf(Episode(position = 1, name = "1 серия", isWatched = true))

        val result =
            episodes.mergeWatchedOverrides(
                watchedOverrides = mapOf(1 to false),
                localToggleOverrides = emptyMap(),
            )

        assertEquals(false, result.single().isWatched)
    }

    @Test
    fun watchedOverridesTrue_winsOverServerIsWatchedFalse() {
        val episodes = listOf(Episode(position = 1, name = "1 серия", isWatched = false))

        val result =
            episodes.mergeWatchedOverrides(
                watchedOverrides = mapOf(1 to true),
                localToggleOverrides = emptyMap(),
            )

        assertEquals(true, result.single().isWatched)
    }

    @Test
    fun positionAbsentFromWatchedOverrides_fallsBackToServerIsWatched() {
        val episodes = listOf(Episode(position = 1, name = "1 серия", isWatched = true))

        val result =
            episodes.mergeWatchedOverrides(
                watchedOverrides = emptyMap(),
                localToggleOverrides = emptyMap(),
            )

        assertEquals(true, result.single().isWatched)
    }

    @Test
    fun localToggleOverride_winsOverWatchedOverridesEvenWhenBothPresent() {
        val episodes = listOf(Episode(position = 1, name = "1 серия", isWatched = true))

        val result =
            episodes.mergeWatchedOverrides(
                watchedOverrides = mapOf(1 to true),
                localToggleOverrides = mapOf(1 to false),
            )

        assertEquals(false, result.single().isWatched)
    }
}
