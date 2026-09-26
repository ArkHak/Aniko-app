package com.aniko.data.geo

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException

/**
 * Провайдер кода страны по СЕТИ (geo-IP по egress-IP пользователя), а не по локали устройства.
 *
 * Интерфейс (fun interface) — ради тестов `GeoRegionStore`: подставляется фейк со счётчиком
 * вызовов, без мока HttpClient.
 */
fun interface GeoRegionApi {
    /**
     * ISO 3166-1 alpha-2 код страны egress-IP ("RU", "NL", ...) или `null`, если определить
     * не удалось. Реализации не бросают сетевые исключения наружу: сбой = `null` (fail-open,
     * см. KDoc `UserRegion`); [CancellationException] пробрасывается как есть.
     */
    suspend fun countryCode(): String?
}

/**
 * [GeoRegionApi] поверх двух публичных geo-сервисов: основной `GET https://ipwho.is/`
 * (JSON, поле `country_code`, учитываем флаг `success` — у битых/подозрительных запросов он
 * `false`, и тело может нести пустой `country_code`) и фолбэк `GET
 * https://www.cloudflare.com/cdn-cgi/trace` (текст, строка `loc=XX`). Второй вызывается ТОЛЬКО
 * если первый упал или ответ невалиден — лишний запрос на сторонний хост не делаем без нужды.
 *
 * Парсинг — минимальными regex по телу ответа, без DTO/`ContentNegotiation`: из JSON нужен ровно
 * один строковый код, тащить сериализатор ради двух полей избыточно (тот же аргумент, по
 * которому клиент сознательно без `ContentNegotiation`, см. KDoc `createGeoHttpClient` в
 * `shared/network`).
 *
 * VPN учитывается естественно: запрос уходит через egress-интерфейс пользователя, поэтому
 * VPN Нидерландов возвращает "NL", а не "RU".
 */
class PublicGeoRegionApi(
    private val client: HttpClient,
    private val primaryUrl: String = PRIMARY_URL,
    private val fallbackUrl: String = FALLBACK_URL,
) : GeoRegionApi {
    override suspend fun countryCode(): String? =
        try {
            primaryCountryCode() ?: traceCountryCode()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception,
        ) {
            // Fail-open намеренно: geo-регион — гейтинг для лицензионной блокировки, сбой сети
            // или недоступность сервиса не должны закрывать доступ (см. KDoc `UserRegion`).
            null
        }

    private suspend fun primaryCountryCode(): String? {
        val body = client.get(primaryUrl).bodyAsText()
        if (!SUCCESS_REGEX.containsMatchIn(body)) return null
        return COUNTRY_CODE_REGEX.find(body)?.groupValues?.get(COUNTRY_CODE_GROUP)
    }

    private suspend fun traceCountryCode(): String? {
        val body = client.get(fallbackUrl).bodyAsText()
        return body
            .lineSequence()
            .firstOrNull { it.startsWith(TRACE_LOCATION_PREFIX) }
            ?.removePrefix(TRACE_LOCATION_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val PRIMARY_URL: String = "https://ipwho.is/"
        const val FALLBACK_URL: String = "https://www.cloudflare.com/cdn-cgi/trace"
    }
}

private val SUCCESS_REGEX = Regex(""""success"\s*:\s*true""")
private val COUNTRY_CODE_REGEX = Regex(""""country_code"\s*:\s*"([A-Za-z]{2})""")
private const val COUNTRY_CODE_GROUP = 1
private const val TRACE_LOCATION_PREFIX = "loc="
