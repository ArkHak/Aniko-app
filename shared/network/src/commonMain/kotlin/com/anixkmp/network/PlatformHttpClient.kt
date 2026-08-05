package com.anixkmp.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * Платформенный engine Ktor:
 * - Android / Desktop (JVM) → OkHttp,
 * - iOS → Darwin (NSURLSession).
 *
 * Единственная точка `expect/actual` в сетевом модуле: всё остальное — общий код.
 */
expect fun createPlatformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient
