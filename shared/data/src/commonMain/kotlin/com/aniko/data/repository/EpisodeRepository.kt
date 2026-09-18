package com.aniko.data.repository

import com.aniko.data.api.EpisodeApi
import com.aniko.data.mapper.toDomain
import com.aniko.data.sync.SyncQueueWorker
import com.aniko.database.store.EpisodeProgressStore
import com.aniko.database.store.SyncQueueStore
import com.aniko.database.sync.SyncOperation
import com.aniko.database.sync.SyncOperationKind
import com.aniko.model.AnixError
import com.aniko.model.Episode
import com.aniko.model.EpisodeSource
import com.aniko.model.VideoHost
import com.aniko.model.VoiceType
import com.aniko.player.PlaybackSource
import com.aniko.player.isKodikEmbedUrl
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/**
 * Цепочка резолвинга плеера из `docs/api/ENDPOINTS.md`:
 * types → sources → episodes → target.
 *
 * P4.T7 (S3 — интеграция): [markWatched]/[markUnwatched] теперь пишут прогресс оптимистично в
 * [episodeProgressStore] и уходят на сервер через [SyncQueueStore]/[syncQueueWorker], а не бьют
 * в [episodeApi] напрямую — переживают офлайн (P4.T5), как и мутации `LibraryRepository`.
 *
 * `TooManyFunctions`: вся цепочка резолвинга плеера (types→sources→episodes→target) + watched-
 * мутации + P13.T10 (`matchPosition`, `resolveEpisodeTarget`) держатся в одном репозитории
 * намеренно — это один связный домен (`docs/api/ENDPOINTS.md`), резать по произвольной границе
 * ради счётчика хуже, чем чуть более длинный класс.
 */
@Suppress("TooManyFunctions")
class EpisodeRepository(
    private val episodeApi: EpisodeApi,
    private val episodeProgressStore: EpisodeProgressStore,
    private val syncQueueStore: SyncQueueStore,
    private val syncQueueWorker: SyncQueueWorker,
    private val clock: Clock,
) {
    suspend fun voiceTypes(releaseId: Int): List<VoiceType> = episodeApi.types(releaseId).types.map { it.toDomain() }

    suspend fun sources(
        releaseId: Int,
        typeId: Int,
    ): List<EpisodeSource> = episodeApi.sources(releaseId, typeId).sources.map { it.toDomain() }

    suspend fun episodes(
        releaseId: Int,
        typeId: Int,
        sourceId: Int,
    ): List<Episode> = episodeApi.episodes(releaseId, typeId, sourceId).episodes.map { it.toDomain() }

    /**
     * Резолвит серию в проигрываемый источник для WebView.
     *
     * `host` приходит от вызывающей стороны (экран плеера получает его из навигации,
     * см. `AnixDestination.Player.hostKey`) — репозиторий больше не хранит его сам между
     * вызовами `sources()`/`resolvePlaybackSource()`, это раньше было гонкой состояния между
     * параллельными экранами (см. код-ревью Фазы 5).
     *
     * Архитектурное решение (не пересматривать в рамках Фазы 5): всё воспроизведение идёт через
     * embed (WebView), независимо от хоста и от [com.aniko.model.EpisodeTarget.iframe] —
     * нативный `PlayerController`/`PlaybackSource.Direct` из `:shared:player` остаются заделом
     * на будущее и здесь не используются.
     *
     * Тонкая обёртка над [resolveEpisodeTarget] ради обратной совместимости сигнатуры (тесты и
     * старые вызывающие стороны просят только [PlaybackSource], без имени серии) — сам сетевой
     * запрос ровно один, второй раз `episode/target` не бьётся.
     */
    suspend fun resolvePlaybackSource(
        releaseId: Int,
        sourceId: Int,
        position: Int,
        host: VideoHost,
    ): PlaybackSource = resolveEpisodeTarget(releaseId, sourceId, position, host).source

    /**
     * Результат [resolveEpisodeTarget]: сам проигрываемый источник + человекочитаемое имя серии
     * (`"1 серия"`), которое приходит в том же ответе `episode/target`, но раньше отбрасывалось.
     * Имя нужно P13.T10 (переключение озвучки прямо в плеере) — см. [matchPosition], почему по
     * нему, а не по `position`, ищется та же серия в другом источнике.
     */
    data class ResolvedEpisode(
        val source: PlaybackSource,
        val episodeName: String?,
    )

    suspend fun resolveEpisodeTarget(
        releaseId: Int,
        sourceId: Int,
        position: Int,
        host: VideoHost,
    ): ResolvedEpisode {
        val target = episodeApi.target(releaseId, sourceId, position).episode?.toDomain()
        val url =
            target?.url?.takeIf { it.isNotBlank() }
                ?: throw AnixError.PlaybackResolve(host)
        // Хост из навигации опознан только по имени источника (`VideoHost.fromKey`) — теперь, когда
        // на руках реальный URL, уточняем его по домену: имя на стороне Anixart меняется
        // («Libria» → «Liberty»), домен — нет. Если домен не опознан, остаётся хост из навигации.
        val resolvedHost = VideoHost.fromUrl(url).takeIf { it != VideoHost.UNKNOWN } ?: host
        val source =
            if (isKodikEmbedUrl(url)) {
                // Query-параметры `?d=/&s=/&ip=` из ответа API рассчитаны на referer из исходного
                // запроса и с нашим WebView не совпадают — Kodik отдаёт `500 "Error code: ds"`
                // (проверено вживую). Если вместо этого загрузить страницу без query, но с
                // `Referer: https://anixmirai.com/`, Kodik сам генерирует корректные подписи
                // (d_sign/pd_sign/ref_sign) на основе заголовка и отдаёт настоящую страницу плеера —
                // 200, а не 500 (проверено вживую через curl). anixmirai.com — авторизованный домен
                // партнёра на стороне Kodik (тот же, что видно в оригинальном приложении).
                PlaybackSource.Embed(
                    url = url.substringBefore('?'),
                    host = resolvedHost,
                    referer = "https://anixmirai.com/",
                )
            } else {
                PlaybackSource.Embed(url = url, host = resolvedHost, referer = url)
            }
        return ResolvedEpisode(source = source, episodeName = target?.name)
    }

    /**
     * Подбирает `position` той же серии в другом источнике/озвучке — нужен переключению
     * аудиодорожки прямо в плеере (P13.T10), чтобы после смены озвучки воспроизведение осталось
     * на той же серии, а не сбросилось в начало.
     *
     * **Живая проверка перед реализацией (2026-09-03, `api-s.anixsekai.com`, без токена — три этих
     * эндпоинта его не требуют)** — `position` НЕ совпадает 1:1 между источниками одного релиза,
     * даже внутри одной и той же озвучки:
     * - `releaseId=186`, тип «AniDUB» (`typeId=1`): источник Sibnet (`sourceId=1`) нумерует с `0`
     *   (`"1 серия"` → `position=0`), источник Kodik (`sourceId=8`) — с `1` (`"1 серия"` →
     *   `position=1`). Тот же релиз, та же озвучка, offset различается на единицу.
     * - `releaseId=186`: у типа «SHIZA Project» (`typeId=17`) 16 серий, у «AniDUB» (`typeId=1`) —
     *   только 12. Количество серий у разных озвучек одного релиза может не совпадать вовсе.
     * - `releaseId=1` — контрпример, а не общее правило: там «AniDUB» (`sourceId=8`) и
     *   «Субтитры» (`sourceId=24`) СОВПАДАЮТ 1:1 (`position=1..104` у обоих, те же имена). То есть
     *   поведение непредсказуемо от релиза к релизу — полагаться на голый `position` как на
     *   стабильный номер серии нельзя в принципе (сырые сэмплы — `docs/api/samples/p13_*.json`).
     *
     * Поэтому матчинг идёт по человекочитаемому номеру серии из [Episode.name] (во всех проверенных
     * источниках формат `"N серия"`, число парсится regex'ом), а не по `position`: `position` из
     * ответа берётся только у эпизода с тем же числом. Если по имени найти не удалось (пустое имя,
     * другой формат у стороннего источника, серии с таким номером в новой озвучке вовсе нет) —
     * честный фолбэк на `currentPosition`, зажатый в границы списка нового источника: это не всегда
     * та же серия, но всегда существующая позиция этого источника, а не 404 на `episode/target`.
     *
     * `ReturnCount`: guard clause на пустой список + матч по имени + фолбэк по индексу — три
     * содержательно разных случая, вложенный `when`/`let` тут читался бы хуже линейной цепочки.
     */
    @Suppress("ReturnCount")
    suspend fun matchPosition(
        releaseId: Int,
        typeId: Int,
        sourceId: Int,
        currentEpisodeName: String?,
        currentPosition: Int,
    ): Int {
        val list = episodes(releaseId, typeId, sourceId)
        if (list.isEmpty()) return currentPosition
        val currentNumber = episodeNumberOf(currentEpisodeName)
        if (currentNumber != null) {
            list.firstOrNull { episodeNumberOf(it.name) == currentNumber }?.let { return it.position }
        }
        val fallbackIndex = list.indices.minBy { index -> kotlin.math.abs(list[index].position - currentPosition) }
        return list[fallbackIndex].position
    }

    /**
     * Существует ли серия с такой позицией в этом источнике — вопрос «есть ли следующая серия»
     * для баннера P8.T4 и кнопки «Следующая серия» на Desktop.
     *
     * Почему через `episode/target`, а не через [episodes]: маршрут плеера
     * (`AnixDestination.Player`) несёт `sourceId`, но НЕ `typeId`, а `episode/{releaseId}/{typeId}/
     * {sourceId}` без типа озвучки не вызвать. Тащить `typeId` через всю навигацию ради одного
     * булева ответа дороже, чем один запрос, который к тому же отвечает на вопрос точно.
     *
     * Живая проверка (2026-08-17, `releaseId=1`, `sourceId=8`, всего 104 серии):
     * `episode/target/1/8/104` → `code: 0` + непустой `episode.url`, `episode/target/1/8/105`
     * и `.../999` → `{"code":1,"episode":null}`. То есть API честно различает существующую и
     * несуществующую позицию, «последняя серия вместо запрошенной» не отдаётся — ложного
     * «следующая серия есть» на последней серии не будет.
     *
     * Сетевую ошибку намеренно проглатываем в `false`: не знать про следующую серию — это просто
     * отсутствие баннера, ронять из-за этого экран плеера незачем.
     */
    suspend fun hasEpisode(
        releaseId: Int,
        sourceId: Int,
        position: Int,
    ): Boolean =
        runCatching {
            episodeApi
                .target(releaseId, sourceId, position)
                .episode
                ?.url
                ?.isNotBlank() == true
        }.getOrDefault(false)

    /** Отмечена ли конкретная серия просмотренной — прямой passthrough локальной истины, TTL не нужен. */
    fun observeWatched(
        releaseId: Int,
        sourceId: Int,
        position: Int,
    ): Flow<Boolean> = episodeProgressStore.observeWatched(releaseId, sourceId, position)

    /**
     * Весь известный локальный статус позиций релиза в рамках источника (позиция → is_watched),
     * включая явный unwatched — прямой passthrough [episodeProgressStore].
     */
    fun observeWatchedPositions(
        releaseId: Int,
        sourceId: Int,
    ): Flow<Map<Int, Boolean>> = episodeProgressStore.observeWatchedPositions(releaseId, sourceId)

    suspend fun markWatched(
        releaseId: Int,
        sourceId: Int,
        position: Int,
    ) {
        setWatchedAndEnqueue(releaseId, sourceId, position, isWatched = true)
    }

    suspend fun markUnwatched(
        releaseId: Int,
        sourceId: Int,
        position: Int,
    ) {
        setWatchedAndEnqueue(releaseId, sourceId, position, isWatched = false)
    }

    private suspend fun setWatchedAndEnqueue(
        releaseId: Int,
        sourceId: Int,
        position: Int,
        isWatched: Boolean,
    ) {
        val now = clock.now()
        episodeProgressStore.setWatched(releaseId, sourceId, position, isWatched, now)
        syncQueueStore.enqueue(
            SyncOperation(
                id = 0,
                kind = SyncOperationKind.EPISODE_SET_WATCHED,
                entityKey = "episode:$releaseId:$sourceId:$position",
                releaseId = releaseId,
                sourceId = sourceId,
                position = position,
                statusApiValue = null,
                boolArg = isWatched,
                createdAt = now,
                updatedAt = now,
                attemptCount = 0,
                nextAttemptAt = null,
                lastError = null,
            ),
        )
        syncQueueWorker.drain()
    }

    /*
     * Мёртвая заметка (не публичный API): изначально предполагалось ветвление
     * Direct/Embed по хосту — карта ниже перечисляла хосты, которые заведомо требуют
     * embed-страницы. Решение MVP — всё через embed, поэтому карта не нужна как код, но
     * оставлена как справка на случай будущего возврата к нативному воспроизведению:
     *
     * KODIK, SIBNET, RUTUBE, VK_VIDEO, OK_RU, MAIL_RU, MYVI, ALLVIDEO, SOVET_ROMANTICA,
     * STUDIO_MIR, TORLOOK, UNKNOWN -> требуют embed; ANILIBRIA -> предположительно прямой поток.
     */
}

/**
 * Ведущее число из `"12 серия"` → `12`. `null`, если имени нет или числа в нём нет
 * (см. [EpisodeRepository.matchPosition]).
 */
private fun episodeNumberOf(name: String?): Int? = name?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }
