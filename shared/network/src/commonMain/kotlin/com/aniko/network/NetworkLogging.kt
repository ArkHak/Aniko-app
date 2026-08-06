package com.aniko.network

import io.ktor.client.plugins.logging.Logger

/**
 * ПРАВИЛО АРХИТЕКТУРЫ: секреты никогда не попадают в логи.
 *
 * Anixart передаёт токен **query-параметром** `?token=` (см. `docs/api/ENDPOINTS.md`),
 * то есть он является частью URL, а URL печатает даже [io.ktor.client.plugins.logging.LogLevel.HEADERS].
 * Обычного понижения уровня логирования тут НЕ достаточно — нужен санитайзер.
 *
 * Дополнительно маскируется JSON-поле `"token"`: оно приходит в теле ответа
 * `auth/signIn` (`profileToken.token`), а это тот же самый секрет.
 *
 * Правило действует и дальше по плану — на фазе auth и на фазе списков,
 * где `?token=` уходит в каждый запрос. Любой новый способ передачи секрета
 * (заголовок, поле тела) обязан быть добавлен сюда же, а не обойдён точечно.
 */
internal const val REDACTED: String = "***"

private val queryTokenRegex = Regex("""([?&]${ApiConfig.TOKEN_QUERY_PARAM}=)[^&\s]*""", RegexOption.IGNORE_CASE)

private val jsonTokenRegex = Regex(""""token"\s*:\s*"[^"]*"""", RegexOption.IGNORE_CASE)

/** Заменяет значение токена на [REDACTED] в query-строке и в JSON-теле. */
internal fun redactSecrets(message: String): String = message
    .replace(queryTokenRegex) { match -> match.groupValues[1] + REDACTED }
    .replace(jsonTokenRegex) { "\"token\":\"$REDACTED\"" }

/** Обёртка над любым [Logger], пропускающая сообщения через [redactSecrets]. */
internal class RedactingLogger(private val delegate: Logger) : Logger {
    override fun log(message: String) {
        delegate.log(redactSecrets(message))
    }
}
