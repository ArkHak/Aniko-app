package com.aniko.data.cache

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Fake реализация [Clock] для тестов — по образцу `FakeSecureTokenStorage`.
 *
 * Хранит фиксированное "текущее" время в памяти, чтобы тесты `CachePolicy`-зависимой логики
 * (например, [cacheFirstFlow]) не зависели от реального времени выполнения.
 */
class FakeClock(
    var now: Instant,
) : Clock {
    override fun now(): Instant = now
}
