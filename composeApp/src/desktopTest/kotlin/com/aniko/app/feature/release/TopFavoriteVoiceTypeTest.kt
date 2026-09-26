package com.aniko.app.feature.release

import com.aniko.model.VoiceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Табличный тест [topFavoriteVoiceType] — выбор «верхней любимой озвучки» для шеринга
 * (`?voice={typeId}` в deep link, см. её KDoc): первый локальный пин → первый серверный
 * `VoiceType.pinned`, при нескольких кандидатах в группе выигрывает порядок списка (тот же,
 * что в чипах на экране — намеренно НЕ как у `chooseDefaultVoiceType` для кнопки «Смотреть»).
 */
class TopFavoriteVoiceTypeTest {
    private val anilibria = VoiceType(id = 1, name = "Anilibria")
    private val anidub = VoiceType(id = 2, name = "AniDUB")
    private val shiza = VoiceType(id = 3, name = "SHIZA Project")
    private val types = listOf(anilibria, anidub, shiza)

    private fun top(
        localPinnedIds: Set<Int> = emptySet(),
        serverPinnedId: Int? = null,
    ): Int? =
        topFavoriteVoiceType(
            types = types.map { it.copy(pinned = it.id == serverPinnedId) },
            localPinnedIds = localPinnedIds,
        )?.id

    @Test
    fun emptyTypes_returnsNull() {
        assertNull(topFavoriteVoiceType(types = emptyList(), localPinnedIds = setOf(1)))
    }

    @Test
    fun noFavorites_returnsNull() {
        assertNull(top())
    }

    @Test
    fun localPin_winsOverServerPinned() {
        // Порядок чипов на экране: локальный пин выше серверного pinned.
        assertEquals(anilibria.id, top(localPinnedIds = setOf(1), serverPinnedId = 2))
    }

    @Test
    fun serverPinned_winsWhenNoLocalPin() {
        assertEquals(shiza.id, top(serverPinnedId = 3))
    }

    @Test
    fun multipleLocalPins_firstInListOrder() {
        assertEquals(anidub.id, top(localPinnedIds = setOf(2, 3)))
    }

    @Test
    fun multipleServerPinned_firstInListOrder() {
        val pinned = types.map { it.copy(pinned = it.id != 1) }
        assertEquals(anidub.id, topFavoriteVoiceType(types = pinned, localPinnedIds = emptySet())?.id)
    }

    @Test
    fun staleLocalPin_fallsThroughToServerPinned() {
        // Запиненного id нет в списке — берём серверный pinned, а не падаем в null.
        assertEquals(anidub.id, top(localPinnedIds = setOf(99), serverPinnedId = 2))
    }

    @Test
    fun staleFavorites_fallThroughToNull() {
        assertNull(top(localPinnedIds = setOf(99)))
    }
}
