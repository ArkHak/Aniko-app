package com.aniko.data.update

import io.ktor.http.Url

/** Хосты, с которых разрешено читать релизы и скачивать установщики (GitHub и его CDN). */
private val TRUSTED_EXACT_HOSTS = setOf("github.com", "api.github.com")
private const val TRUSTED_CDN_SUFFIX = ".githubusercontent.com"

/**
 * `true` только для `https` и хостов GitHub (`github.com`, `api.github.com`, `*.githubusercontent.com`).
 *
 * Адреса приходят из ответа внешнего API и формально untrusted; этот allowlist проверяется и для
 * ассетов из списка релизов, и для КОНЕЧНОГО адреса после редиректов загрузки (GitHub отдаёт файлы
 * с `objects.githubusercontent.com`/`release-assets.githubusercontent.com`).
 */
fun isTrustedUpdateUrl(url: String): Boolean {
    val parsed = runCatching { Url(url) }.getOrNull()
    val host = parsed?.host?.lowercase()
    val isHttps = parsed?.protocol?.name.equals("https", ignoreCase = true)
    return isHttps && host != null && (host in TRUSTED_EXACT_HOSTS || host.endsWith(TRUSTED_CDN_SUFFIX))
}
