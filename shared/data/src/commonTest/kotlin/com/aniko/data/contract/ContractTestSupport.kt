package com.aniko.data.contract

import com.aniko.network.AnixJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

/**
 * Общая инфраструктура контрактных тестов (P11.T1, `docs/REELWAVE_PLAN.md`).
 *
 * Каждый тест в этом пакете гоняет РЕАЛЬНУЮ фикстуру (`com.aniko.data.fixtures.ApiFixtures`,
 * зашитые build-time JSON-файлы из `docs/api/samples`) через `MockEngine` → реальный Api-класс →
 * реальный маппер → доменную модель, и делает конкретные `assertEquals` на значения полей.
 *
 * ВАЖНО: `AnixJson` сконфигурирован с `ignoreUnknownKeys = true` и `explicitNulls = false` —
 * тест вида «распарсилось без исключения» ничего не проверяет (пропавшее поле сервера тихо
 * подставит `null`/дефолт). Каждый тест здесь обязан сверять реальные значения полей, а не
 * только факт успешного парсинга.
 */
internal fun mockAnixClient(
    expectedPath: String,
    fixtureJson: String,
): HttpClient {
    val mockEngine =
        MockEngine { request ->
            check(request.url.encodedPath == expectedPath) {
                "Unexpected path: ${request.url.encodedPath}, expected: $expectedPath"
            }
            respond(
                content = fixtureJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
    return HttpClient(mockEngine) { install(ContentNegotiation) { json(AnixJson) } }
}
