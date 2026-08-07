package com.aniko.data.session

import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * INTEGRATION TEST: touches the real macOS Keychain of the current user.
 *
 * Интеграционный тест для [MacKeychainTokenStorage] — системного хранилища токенов на macOS.
 *
 * **Почему вариант (b) а не (a)?**
 * Вариант (a) потребовал бы выделения построения ProcessBuilder/аргументов в отдельную
 * testable-функцию, что требует рефакторинга продакшн-кода MacKeychainTokenStorage.kt.
 * Так как это не входит в scope текущей задачи (нужны именно тесты, а не рефакторинг),
 * выбираем интеграционный тест с @Ignore, чтобы он НЕ запускался в стандартном `./gradlew test`,
 * но остался документацией и был доступен для ручного прогона при необходимости.
 *
 * **Как запустить вручную:**
 * 1. Снять @Ignore с методов ниже
 * 2. `./gradlew :shared:data:desktopTest`
 *
 * **Что этот тест проверяет:**
 * - Сохранение и чтение токена из macOS Keychain через `security` CLI
 * - Удаление токена (clear)
 * - Обработка случая когда токен не найден (null вместо ошибки)
 */
@Ignore("Integration test: не входит в стандартный ./gradlew test, только для ручного прогона")
class MacKeychainTokenStorageIntegrationTest {
    private val storage = MacKeychainTokenStorage()

    @Test
    fun setAndGet_savesTokenToKeychain_andRetrievesIt() =
        runTest {
            // Arrange: используем уникальный токен, чтобы не конфликтовать с реальным
            val testToken = "test-token-${System.currentTimeMillis()}"

            // Act & Assert: сохраняем и получаем
            storage.set(testToken)
            val retrieved = storage.get()

            assertEquals(testToken, retrieved, "Токен должен быть сохранён и получен из Keychain")

            // Cleanup
            storage.clear()
        }

    @Test
    fun clear_removesTokenFromKeychain() =
        runTest {
            // Arrange: сохраняем токен
            val testToken = "test-token-${System.currentTimeMillis()}"
            storage.set(testToken)

            // Act: удаляем
            storage.clear()

            // Assert: токен больше не доступен
            val retrieved = storage.get()
            assertNull(retrieved, "После clear() токен должен быть удалён из Keychain")
        }

    @Test
    fun get_returnsNull_whenTokenNotFound() =
        runTest {
            // Arrange: очищаем любой существующий токен
            storage.clear()

            // Act: пытаемся получить несуществующий токен
            val retrieved = storage.get()

            // Assert: null вместо ошибки
            assertNull(retrieved, "get() должен вернуть null когда токен не найден, не бросать исключение")
        }

    @Test
    fun setWithSpecialCharacters_escapesCorrectly() =
        runTest {
            // Arrange: токен с экранируемыми символами (пробелы, кавычки, бэкслэши)
            val testToken = """test "token" with \ special chars"""
            val uniqueToken = "$testToken-${System.currentTimeMillis()}"

            // Act
            storage.set(uniqueToken)
            val retrieved = storage.get()

            // Assert: специальные символы должны быть обработаны корректно
            assertEquals(
                uniqueToken,
                retrieved,
                "Токен со специальными символами должен быть сохранён и получен без потерь",
            )

            // Cleanup
            storage.clear()
        }
}
