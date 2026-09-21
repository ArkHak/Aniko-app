package com.aniko.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.ktor.client.HttpClient
import okio.Path

/**
 * Coil 3 поверх отдельного `image`-клиента (`createAnixImageHttpClient`): без `?token=`,
 * без валидатора сессии и с короткими таймаутами — URL картинок CDN не зависит от токена,
 * поэтому ключи кэша Coil стабильны между сменами сессии.
 *
 * Кэши (T10a, Фаза 11): фундамент собирал `ImageLoader` вовсе без `memoryCache`/`diskCache`,
 * из-за чего на iOS/Desktop не было персистентного дискового кэша постеров — каждый холодный
 * старт заново скачивал всю графику каталога.
 *
 * - **memoryCache — 25% доступной памяти процесса.** У Reelwave/Aniko насыщенный постерами UI
 *   (домашние рейлы, каталог-сетка, детальная страница с похожими тайтлами) — на экране
 *   одновременно держатся десятки декодированных битмапов. 25% — верхняя граница из
 *   рекомендаций самого Coil для медиатяжёлых приложений: даёт плавный скролл без повторного
 *   декодирования, но оставляет достаточно памяти под остальной UI и видеоплеер ([shared:player]
 *   держит свои буферы отдельно от Coil).
 * - **diskCache — 250 МБ, платформенный cache-каталог.** Постеры Anixart — это WebP/JPEG
 *   обычно 20-80 КБ (не оригиналы, а уже сжатые превью с CDN), 250 МБ хватает на несколько
 *   тысяч разных тайтлов офлайн между запусками — с запасом покрывает всю глубину каталога,
 *   которую реально пролистает один пользователь между установками приложения, но не
 *   разрастается бесконтрольно на телефонах с малым хранилищем. Директория — platform cache dir
 *   (см. [imageCacheDirectory]), который ОС вправе очистить при нехватке места: то, что нужно
 *   для картинок (в отличие от офлайн-БД в `shared:database`, которая специально живёт в
 *   Application Support/аналоге и не может быть внезапно стёрта системой).
 */
fun createAnixImageLoader(
    context: PlatformContext,
    httpClient: HttpClient,
): ImageLoader =
    ImageLoader
        .Builder(context)
        .components {
            add(KtorNetworkFetcherFactory(httpClient = { httpClient }))
        }.crossfade(true)
        .memoryCache {
            MemoryCache
                .Builder()
                .maxSizePercent(context, percent = MEMORY_CACHE_PERCENT)
                .build()
        }.diskCache {
            DiskCache
                .Builder()
                .directory(imageCacheDirectory(context))
                .maxSizeBytes(DISK_CACHE_MAX_SIZE_BYTES)
                .build()
        }.build()

private const val MEMORY_CACHE_PERCENT = 0.25
private const val DISK_CACHE_MAX_SIZE_BYTES = 250L * 1024 * 1024

/**
 * Платформенный каталог для дискового кэша Coil.
 *
 * Реализации: `androidMain` — `context.cacheDir`, `iosMain` — `Library/Caches` в песочнице
 * приложения, `desktopMain` — ОС-специфичный cache-каталог (см. `DesktopDatabaseDriverFactory`
 * в `shared:database` для аналогичного разбора по ОС, только там `Application Support`/данные,
 * а тут — именно `Caches`, потому что содержимое одноразовое и восстановимое из сети).
 */
expect fun imageCacheDirectory(context: PlatformContext): Path
