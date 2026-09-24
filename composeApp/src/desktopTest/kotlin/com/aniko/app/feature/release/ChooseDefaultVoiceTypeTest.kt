package com.aniko.app.feature.release

import com.aniko.model.VoiceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Табличный тест приоритетов выбора типа озвучки для кнопки "Смотреть" ([chooseDefaultVoiceType],
 * см. её KDoc): явный выбор чипами → серверный `VoiceType.pinned` → локальный пин
 * `LocalVoicePinStore` → dubbing memory → первый в списке. Пины намеренно выше dubbing memory
 * (решение пользователя 2026-09-24).
 */
class ChooseDefaultVoiceTypeTest {
    private val anilibria = VoiceType(id = 1, name = "Anilibria")
    private val anidub = VoiceType(id = 2, name = "AniDUB")
    private val shiza = VoiceType(id = 3, name = "SHIZA Project")
    private val types = listOf(anilibria, anidub, shiza)

    /** Возвращает id выбранного типа (сравнение по id: выбранный элемент может отличаться
     *  значением `pinned` от исходных констант, т.к. серверный флаг подставляется в сценарии). */
    private fun choose(
        selectedTypeId: Int? = null,
        serverPinnedId: Int? = null,
        localPinnedIds: Set<Int> = emptySet(),
        savedTypeId: Int? = null,
    ): Int? =
        chooseDefaultVoiceType(
            types = types.map { it.copy(pinned = it.id == serverPinnedId) },
            selectedTypeId = selectedTypeId,
            localPinnedIds = localPinnedIds,
            savedTypeId = savedTypeId,
        )?.id

    @Test
    fun emptyTypes_returnsNull() {
        assertNull(
            chooseDefaultVoiceType(
                types = emptyList(),
                selectedTypeId = null,
                localPinnedIds = emptySet(),
                savedTypeId = null,
            ),
        )
    }

    @Test
    fun noSignals_firstInList() {
        assertEquals(anilibria.id, choose())
    }

    @Test
    fun explicitSelection_winsOverEverything() {
        assertEquals(
            shiza.id,
            choose(selectedTypeId = 3, serverPinnedId = 2, localPinnedIds = setOf(1), savedTypeId = 2),
        )
    }

    @Test
    fun serverPinned_winsOverLocalPinAndMemory() {
        assertEquals(anidub.id, choose(serverPinnedId = 2, localPinnedIds = setOf(3), savedTypeId = 3))
    }

    @Test
    fun localPin_winsOverDubbingMemory() {
        assertEquals(shiza.id, choose(localPinnedIds = setOf(3), savedTypeId = 2))
    }

    @Test
    fun dubbingMemory_winsOverFirstInList() {
        assertEquals(anidub.id, choose(savedTypeId = 2))
    }

    @Test
    fun staleSignals_fallThrough() {
        // Выбранный/запиненный/запомненный тип отсутствует в свежем списке — берём первый.
        assertEquals(
            anilibria.id,
            choose(selectedTypeId = 40, localPinnedIds = setOf(41), savedTypeId = 42),
        )
    }

    @Test
    fun multipleServerPinned_firstInServerOrder() {
        // Несколько pinned одновременно — порядок серверного списка (тот же, что в чипах).
        val pinned = types.map { it.copy(pinned = it.id != 1) }
        assertEquals(
            anidub.id,
            chooseDefaultVoiceType(
                types = pinned,
                selectedTypeId = null,
                localPinnedIds = emptySet(),
                savedTypeId = null,
            )?.id,
        )
    }
}
