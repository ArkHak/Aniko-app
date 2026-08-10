package com.aniko.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Билд-спайк (P4.S0): подтверждает, что кодогенерация SQLDelight реально работает — сгенерированный
 * `AnikoDatabase` компилируется, а платформенный in-memory драйвер умеет создать схему и выполнить
 * простой insert/select через сгенерированные `smokeEntityQueries`.
 *
 * iOS-`actual` для [createTestDriver] намеренно не добавлен в S0 (сборка под iOS не гоняется в
 * этом окружении) — есть только desktopTest/androidUnitTest.
 */
expect fun createTestDriver(): SqlDriver

class SmokeDriverTest {
    @Test
    fun insertAndSelectSmokeEntity() {
        val driver = createTestDriver()
        val database = AnikoDatabase(driver)

        database.smokeTestQueries.insertSmoke(1, "hello")
        val rows = database.smokeTestQueries.selectAllSmoke().executeAsList()

        assertEquals(1, rows.size)
        assertEquals("hello", rows.first().value_)
    }
}
