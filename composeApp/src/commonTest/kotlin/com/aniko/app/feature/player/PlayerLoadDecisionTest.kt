package com.aniko.app.feature.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Решение «перезагружать ли плеер» ([shouldStartLoad]) таблицей случаев. Ключи — обычные строки: функция
 * дженерик по ключу, реальный `LoadKey` `PlayerViewModel` приватный.
 *
 * Исходная беда: `LaunchedEffect` экрана держит ИСХОДНЫЕ параметры маршрута, а `selectVoiceType`
 * перезагружает плеер другим `sourceId` без смены маршрута. Когда экран покидал композицию и входил
 * в неё снова (поворот/ресайз окна), `load(маршрут)` не совпадал с ключом ЗАГРУЖЕННОГО источника и
 * откатывал озвучку к исходной. Поэтому `PlayerViewModel.load` сверяется с последним принятым ключом
 * МАРШРУТА, а не с загруженным — и здесь это одна и та же функция с разными `acceptedKey`.
 */
class PlayerLoadDecisionTest {
    @Test
    fun firstRequestAlwaysStarts() {
        // Начальное состояние ViewModel — `isLoading = true`, но принятого ключа ещё нет.
        assertTrue(shouldStartLoad(acceptedKey = null, requestedKey = ROUTE, isLoading = true, hasSource = false))
        assertTrue(shouldStartLoad(acceptedKey = null, requestedKey = ROUTE, isLoading = false, hasSource = false))
    }

    @Test
    fun sameKeyWhileLoadingIsSkipped() {
        assertFalse(shouldStartLoad(acceptedKey = ROUTE, requestedKey = ROUTE, isLoading = true, hasSource = false))
    }

    @Test
    fun sameKeyWhenLoadedIsSkipped() {
        assertFalse(shouldStartLoad(acceptedKey = ROUTE, requestedKey = ROUTE, isLoading = false, hasSource = true))
    }

    @Test
    fun sameKeyAfterErrorStartsAgain() {
        // Ошибка: не грузится и источника нет — повторный вход на ту же серию обязан перезагрузить.
        assertTrue(shouldStartLoad(acceptedKey = ROUTE, requestedKey = ROUTE, isLoading = false, hasSource = false))
    }

    @Test
    fun otherKeyStartsInAnyState() {
        // Следующая серия — другой ключ маршрута: грузим и поверх загруженной, и поверх грузящейся, и после ошибки.
        assertTrue(shouldStartLoad(acceptedKey = ROUTE, requestedKey = NEXT, isLoading = false, hasSource = true))
        assertTrue(shouldStartLoad(acceptedKey = ROUTE, requestedKey = NEXT, isLoading = true, hasSource = false))
        assertTrue(shouldStartLoad(acceptedKey = ROUTE, requestedKey = NEXT, isLoading = false, hasSource = false))
    }

    @Test
    fun routeKeyRequestAfterVoiceSwitchIsSkipped() {
        // Озвучку сменили на VOICE_B (загружена/грузится), принятый ключ МАРШРУТА остался ROUTE: повторный
        // load(ROUTE) — no-op, пока источник грузится или загружен. Раньше сверка шла с ключом
        // загруженного источника (VOICE_B ≠ ROUTE) и плеер откатывался к исходной озвучке.
        assertFalse(shouldStartLoad(acceptedKey = ROUTE, requestedKey = ROUTE, isLoading = true, hasSource = false))
        assertFalse(shouldStartLoad(acceptedKey = ROUTE, requestedKey = ROUTE, isLoading = false, hasSource = true))
    }

    @Test
    fun internalReloadComparesWithLoadedKey() {
        // Внутренняя перезагрузка (смена озвучки/retry) сверяется с ключом загруженного источника:
        // смена озвучки — другой ключ, грузим...
        assertTrue(shouldStartLoad(acceptedKey = ROUTE, requestedKey = VOICE_B, isLoading = false, hasSource = true))
        // ...а retry по тому же ключу вне ошибки — no-op, после ошибки — перезагрузка ТЕКУЩЕГО источника.
        assertFalse(shouldStartLoad(acceptedKey = VOICE_B, requestedKey = VOICE_B, isLoading = false, hasSource = true))
        assertTrue(shouldStartLoad(acceptedKey = VOICE_B, requestedKey = VOICE_B, isLoading = false, hasSource = false))
    }

    private companion object {
        const val ROUTE = "release=1/source=8/position=1"
        const val NEXT = "release=1/source=8/position=2"
        const val VOICE_B = "release=1/source=24/position=1"
    }
}
