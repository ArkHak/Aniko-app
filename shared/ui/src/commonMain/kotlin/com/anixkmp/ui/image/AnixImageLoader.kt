package com.anixkmp.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.ktor.client.HttpClient

/**
 * Coil 3 поверх того же Ktor-клиента, что и API: один пул соединений,
 * один набор таймаутов, и постеры автоматически получают `?token=` там, где он нужен.
 */
fun createAnixImageLoader(
    context: PlatformContext,
    httpClient: HttpClient,
): ImageLoader = ImageLoader.Builder(context)
    .components {
        add(KtorNetworkFetcherFactory(httpClient = { httpClient }))
    }
    .crossfade(true)
    .build()
