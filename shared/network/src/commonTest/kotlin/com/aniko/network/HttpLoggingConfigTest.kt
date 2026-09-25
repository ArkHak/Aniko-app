package com.aniko.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Предрелизный аудит безопасности (issue #109, п. 1): HTTP-логи не должны попадать в
 * stdout/logcat release-сборок. Тесты гоняют ровно продовую конфигурацию клиента
 * ([configureAnixClient]) поверх `MockEngine` — с накопителем вместо `Logger.SIMPLE`.
 */
class HttpLoggingConfigTest {
    private val secret = "b3f1c9d2e8a74f60"
    private val tokenProvider = TokenProvider { secret }

    private class CapturingLogger : Logger {
        val lines = mutableListOf<String>()

        override fun log(message: String) {
            lines += message
        }
    }

    private fun client(
        apiConfig: ApiConfig,
        logger: Logger,
    ): HttpClient =
        HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) }) {
            configureAnixClient(apiConfig, tokenProvider, NoOpSessionInvalidator, AnixJson, logger)
        }

    private suspend fun ApiConfig.requestWith(
        logger: Logger,
        path: String,
    ) {
        val httpClient = client(this, logger)
        try {
            httpClient.get(path)
        } finally {
            httpClient.close()
        }
    }

    @Test
    fun defaultApiConfig_logsNothing() =
        runTest {
            assertFalse(ApiConfig().enableLogging, "Логирование по умолчанию должно быть выключено")

            val logger = CapturingLogger()
            ApiConfig().requestWith(logger, "release/1")

            assertEquals(emptyList(), logger.lines, "В release (enableLogging = false) в лог не должно уйти ничего")
        }

    @Test
    fun loggingDisabled_logsNothing_evenWithTokenInRequest() =
        runTest {
            val logger = CapturingLogger()
            ApiConfig(enableLogging = false).requestWith(logger, "profile/1?token=$secret")

            assertEquals(emptyList(), logger.lines)
        }

    @Test
    fun loggingEnabled_logsRequest_butMasksToken() =
        runTest {
            val logger = CapturingLogger()
            ApiConfig(enableLogging = true).requestWith(logger, "release/1")

            val log = logger.lines.joinToString("\n")
            assertTrue("release/1" in log, "В debug запрос должен логироваться: $log")
            assertFalse(secret in log, "Токен не должен попадать в лог даже в debug: $log")
            assertTrue("token=$REDACTED" in log, "Токен должен быть замаскирован: $log")
        }
}
