package com.aniko.app.feature.release.rating

import com.aniko.app.mvi.BaseViewModel
import com.aniko.app.mvi.UiEffect
import com.aniko.app.mvi.UiIntent
import com.aniko.app.mvi.UiState
import com.aniko.model.AnixError
import com.aniko.model.CommunityListCounts
import com.aniko.model.ReleaseDetails
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * Состояние секции рейтинга Title Detail (P7.T9/T10 плана, Трек D).
 *
 * [voteCounts] — ровно [ReleaseDetails.VOTE_BUCKET_COUNT] элементов (1★..5★, индекс 0 = 1
 * звезда), [communityLists] — распределение по спискам сообщества ([CommunityListCounts]).
 */
data class ReleaseRatingState(
    val isLoading: Boolean = false,
    val voteCounts: List<Int> = List(ReleaseDetails.VOTE_BUCKET_COUNT) { 0 },
    val yourVote: Int? = null,
    val communityLists: CommunityListCounts = CommunityListCounts(),
    val loadError: RatingLoadError? = null,
) : UiState

/**
 * Причина ошибки загрузки, без готового текста — текст выбирает экран через `LocalStrings`
 * (P2.T9), см. `ReleaseDetailsViewModel.LoadError` — тот же паттерн, отдельный тип, чтобы не
 * тянуть зависимость на файл Трека C.
 */
enum class RatingLoadError {
    NO_CONNECTION,
    UNAUTHORIZED,
    GENERIC,
}

sealed interface ReleaseRatingIntent : UiIntent {
    data class Load(
        val releaseId: Int,
    ) : ReleaseRatingIntent

    data class Rate(
        val releaseId: Int,
        val stars: Int,
    ) : ReleaseRatingIntent
}

/** См. `HomeEffect.ShowError` — тот же паттерн одноразового уведомления об ошибке. */
sealed interface ReleaseRatingEffect : UiEffect {
    data class ShowError(
        val error: AnixError,
    ) : ReleaseRatingEffect
}

/**
 * ViewModel секции рейтинга. `releaseId` не передаётся в конструктор через Koin — секция сама
 * дёргает [ReleaseRatingIntent.Load] из `LaunchedEffect(releaseId)`, как и `ReleaseDetailsViewModel`.
 *
 * ВАЖНО (пробел фундамента Фазы 7, см. отчёт Трека D): на момент написания в `ReleaseRepository`
 * НЕТ метода `releaseDetails(id)`, в `ReleaseApi` нет `voteAdd`/`voteDelete`, а `ReleaseDto` не
 * парсит расширенные поля (`vote_N_count`, `your_vote`, счётчики списков сообщества) — та
 * инфраструктура, которую бриф этого трека описывал как уже готовую. Поскольку общие
 * shared-модули заморожены на время Фазы 7 и вне территории этого трека, эта ViewModel сама ходит
 * в сеть через инжектированный [HttpClient] (тот же `single<HttpClient>` из `DataModule`, что использует и
 * `ReleaseApi`, — токен и base URL подставляются автоматически тем же `AnixTokenPlugin`) и сама
 * парсит нужные поля локальными DTO ниже, вместо повторного использования `ReleaseApi`/
 * `ReleaseRepository`. Это временный мост: как только фундамент добавит
 * `ReleaseRepository.releaseDetails()`/`ReleaseApi.voteAdd`/`voteDelete`, этот файл можно
 * переключить на них почти без изменений публичного API.
 */
class ReleaseRatingViewModel(
    private val httpClient: HttpClient,
) : BaseViewModel<ReleaseRatingState, ReleaseRatingIntent, ReleaseRatingEffect>(
        initialState = ReleaseRatingState(),
    ) {
    private var loadedReleaseId: Int? = null

    override suspend fun handleIntent(intent: ReleaseRatingIntent) {
        when (intent) {
            is ReleaseRatingIntent.Load -> load(intent.releaseId)
            is ReleaseRatingIntent.Rate -> rate(intent.releaseId, intent.stars)
        }
    }

    /** Идемпотентна для одного и того же [releaseId], пока не было ошибки — см. `ReleaseDetailsViewModel.load`. */
    private suspend fun load(releaseId: Int) {
        if (loadedReleaseId == releaseId && state.value.loadError == null) return
        loadedReleaseId = releaseId

        updateState { copy(isLoading = true, loadError = null) }
        try {
            val snapshot = ratingApiCall { fetchRatingSnapshot(releaseId) }
            updateState {
                copy(
                    isLoading = false,
                    voteCounts = snapshot.voteCounts,
                    yourVote = snapshot.yourVote,
                    communityLists = snapshot.communityLists,
                    loadError = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AnixError) {
            loadedReleaseId = null
            updateState { copy(isLoading = false, loadError = e.toRatingLoadError()) }
        }
    }

    /**
     * D5-b: тап на уже выбранную звезду снимает оценку (`voteDelete`), любой другой тап
     * ставит/переставляет оценку (`voteAdd`) — сервер сам заменяет предыдущую оценку пользователя,
     * отдельного вызова "снять старую" перед новой не требуется.
     *
     * Локальный стейт обновляется оптимистично ДО ответа сети; при ошибке — откат к состоянию
     * до тапа и одноразовый [ReleaseRatingEffect.ShowError] (см. `HomeViewModel.loadNextAndReportIfMoreFailed`
     * — тот же приём: контент остаётся как есть, ошибка — отдельным эффектом, а не заменой контента).
     */
    private suspend fun rate(
        releaseId: Int,
        stars: Int,
    ) {
        val before = state.value
        val previousVoteCounts = before.voteCounts
        val previousYourVote = before.yourVote
        val isRemoving = previousYourVote == stars

        updateState { applyOptimisticVote(stars, isRemoving) }

        try {
            if (isRemoving) {
                ratingApiCall { requestVoteDelete(releaseId) }
            } else {
                ratingApiCall { requestVoteAdd(releaseId, stars) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AnixError) {
            updateState { copy(voteCounts = previousVoteCounts, yourVote = previousYourVote) }
            emitEffect(ReleaseRatingEffect.ShowError(e))
        }
    }

    // ---- Сеть — временный мост, см. KDoc класса выше -------------------------------------

    private suspend fun fetchRatingSnapshot(releaseId: Int): RatingSnapshot {
        val response =
            httpClient
                .get("release/$releaseId") { parameter("extended_mode", true) }
                .body<RatingReleaseResponseDto>()
        if (response.code != RATING_API_CODE_OK) throw AnixError.Api(response.code)
        val dto = response.release ?: throw AnixError.Parsing()

        return RatingSnapshot(
            voteCounts = listOf(dto.vote1Count, dto.vote2Count, dto.vote3Count, dto.vote4Count, dto.vote5Count),
            yourVote = dto.yourVote?.takeIf { it in MIN_VOTE..MAX_VOTE },
            communityLists =
                CommunityListCounts(
                    watching = dto.watchingCount,
                    plan = dto.planCount,
                    completed = dto.completedCount,
                    holdOn = dto.holdOnCount,
                    dropped = dto.droppedCount,
                    favorites = dto.favoritesCount,
                    collection = dto.collectionCount,
                ),
        )
    }

    /** `GET release/vote/add/{r_id}/{vote}` — см. `docs/api/ANIXART_API.md`, раздел 5. */
    private suspend fun requestVoteAdd(
        releaseId: Int,
        vote: Int,
    ) {
        val response = httpClient.get("release/vote/add/$releaseId/$vote").body<RatingActionResponseDto>()
        if (response.code != RATING_API_CODE_OK) throw AnixError.Api(response.code)
    }

    /** `GET release/vote/delete/{r_id}` — см. `docs/api/ANIXART_API.md`, раздел 5. */
    private suspend fun requestVoteDelete(releaseId: Int) {
        val response = httpClient.get("release/vote/delete/$releaseId").body<RatingActionResponseDto>()
        if (response.code != RATING_API_CODE_OK) throw AnixError.Api(response.code)
    }
}

private fun ReleaseRatingState.applyOptimisticVote(
    stars: Int,
    isRemoving: Boolean,
): ReleaseRatingState {
    if (isRemoving) {
        return copy(yourVote = null, voteCounts = voteCounts.decrementAt(stars - 1))
    }
    var counts = voteCounts
    val previousVote = yourVote
    if (previousVote != null) counts = counts.decrementAt(previousVote - 1)
    counts = counts.incrementAt(stars - 1)
    return copy(yourVote = stars, voteCounts = counts)
}

private fun List<Int>.incrementAt(i: Int): List<Int> = mapIndexed { j, v -> if (j == i) v + 1 else v }

private fun List<Int>.decrementAt(i: Int): List<Int> = mapIndexed { j, v -> if (j == i) maxOf(v - 1, 0) else v }

private fun AnixError.toRatingLoadError(): RatingLoadError =
    when (this) {
        is AnixError.Network -> RatingLoadError.NO_CONNECTION
        is AnixError.Unauthorized -> RatingLoadError.UNAUTHORIZED
        else -> RatingLoadError.GENERIC
    }

private data class RatingSnapshot(
    val voteCounts: List<Int>,
    val yourVote: Int?,
    val communityLists: CommunityListCounts,
)

/**
 * Мини-версия `com.aniko.data.api.apiCall` (`:shared:data`, `internal`, недоступна отсюда) — тот
 * же маппинг Ktor/serialization исключений в [AnixError]. Дублирование — прямое следствие того же
 * пробела фундамента, что описан в KDoc [ReleaseRatingViewModel]: как только вызовы этого файла
 * переедут на `ReleaseApi`/`ReleaseRepository`, эта функция станет не нужна.
 *
 * `TooGenericExceptionCaught`: тот же паттерн, что и у `shared/data` apiCall — последний catch
 * намеренный fallback на `AnixError.Unknown`, все специфичные случаи разобраны выше.
 */
@Suppress("TooGenericExceptionCaught")
private suspend fun <T> ratingApiCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AnixError) {
        throw e
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.Unauthorized || e.response.status == HttpStatusCode.Forbidden) {
            throw AnixError.Unauthorized(e)
        }
        throw AnixError.Http(e.response.status.value, e)
    } catch (e: ServerResponseException) {
        throw AnixError.Http(e.response.status.value, e)
    } catch (e: ResponseException) {
        throw AnixError.Http(e.response.status.value, e)
    } catch (e: HttpRequestTimeoutException) {
        throw AnixError.Network(e)
    } catch (e: IOException) {
        throw AnixError.Network(e)
    } catch (e: SerializationException) {
        throw AnixError.Parsing(e)
    } catch (e: Throwable) {
        throw AnixError.Unknown(e)
    }

/** `ReleaseResponse` для `release/{id}?extended_mode=true`, только поля, нужные этой секции. */
@Serializable
private data class RatingReleaseResponseDto(
    val code: Int = 0,
    val release: RatingReleaseDto? = null,
)

/**
 * Подмножество полей расширенной модели `Release` (см. `docs/api/ANIXART_API.md`, раздел
 * «Модель Release»: `vote_1_count`…`vote_5_count`, `your_vote` подтверждены документом; имена
 * полей списков сообщества (`watching_count`/`plan_count`/`completed_count`/`hold_on_count`/
 * `dropped_count`/`favorites_count`/`collection_count`) взяты из KDoc `CommunityListCounts`
 * (`shared/model`) — сам сэмпл `release_186_extended.json`, на который эта KDoc ссылается,
 * в репозитории отсутствует, живой проверкой в рамках этого трека не переподтверждались).
 */
@Serializable
private data class RatingReleaseDto(
    @SerialName("vote_1_count") val vote1Count: Int = 0,
    @SerialName("vote_2_count") val vote2Count: Int = 0,
    @SerialName("vote_3_count") val vote3Count: Int = 0,
    @SerialName("vote_4_count") val vote4Count: Int = 0,
    @SerialName("vote_5_count") val vote5Count: Int = 0,
    @SerialName("your_vote") val yourVote: Int? = null,
    @SerialName("watching_count") val watchingCount: Int = 0,
    @SerialName("plan_count") val planCount: Int = 0,
    @SerialName("completed_count") val completedCount: Int = 0,
    @SerialName("hold_on_count") val holdOnCount: Int = 0,
    @SerialName("dropped_count") val droppedCount: Int = 0,
    @SerialName("favorites_count") val favoritesCount: Int = 0,
    @SerialName("collection_count") val collectionCount: Int = 0,
)

/** Ответ `release/vote/add`/`release/vote/delete` — нужен только код результата. */
@Serializable
private data class RatingActionResponseDto(
    val code: Int = 0,
)

private const val RATING_API_CODE_OK = 0
private const val MIN_VOTE = 1
private const val MAX_VOTE = ReleaseDetails.VOTE_BUCKET_COUNT
