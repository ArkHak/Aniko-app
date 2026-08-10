package com.aniko.data.sync

import com.aniko.data.api.EpisodeApi
import com.aniko.data.api.FavoriteApi
import com.aniko.data.api.HistoryApi
import com.aniko.data.api.ProfileListApi
import com.aniko.database.store.EpisodeProgressStore
import com.aniko.database.store.ListMembershipStore
import com.aniko.database.store.SyncQueueStore
import com.aniko.database.sync.SyncOperation
import com.aniko.database.sync.SyncOperationKind
import com.aniko.model.AnixError
import com.aniko.model.ListStatus
import kotlinx.coroutines.flow.first
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Не-2xx статусы, при которых имеет смысл повторить попытку позже, а не сдаваться сразу. */
private const val HTTP_SERVER_ERROR_MIN = 500
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429

/** `min(15 минут, 1s * 2^attemptCount) + джиттер` — экспоненциальный backoff с кэпом (P4.T6). */
private val BASE_BACKOFF = 1.seconds
private val MAX_BACKOFF = 15.minutes
private const val MAX_BACKOFF_SHIFT = 10
private const val JITTER_BOUND_MS = 250L

private fun backoffFor(attemptCount: Int): Duration {
    val shift = attemptCount.coerceIn(0, MAX_BACKOFF_SHIFT)
    val exponential = BASE_BACKOFF * (1 shl shift)
    val capped = minOf(exponential, MAX_BACKOFF)
    val jitter = Random.nextLong(0, JITTER_BOUND_MS).milliseconds
    return capped + jitter
}

/** Как классифицировать ошибку отправки одной операции, см. [SyncQueueWorker.drain]. */
private enum class ErrorClassification {
    UNAUTHORIZED,
    RETRYABLE,
    PERMANENT,
}

private fun classify(error: AnixError): ErrorClassification =
    when (error) {
        is AnixError.Unauthorized -> ErrorClassification.UNAUTHORIZED
        is AnixError.Network -> ErrorClassification.RETRYABLE
        is AnixError.Http ->
            if (error.statusCode >= HTTP_SERVER_ERROR_MIN ||
                error.statusCode == HTTP_REQUEST_TIMEOUT ||
                error.statusCode == HTTP_TOO_MANY_REQUESTS
            ) {
                ErrorClassification.RETRYABLE
            } else {
                ErrorClassification.PERMANENT
            }
        is AnixError.Api -> ErrorClassification.PERMANENT
        is AnixError.Parsing -> ErrorClassification.RETRYABLE
        // Не должно происходить из вызовов этого воркера (кейс относится к резолву плеера) —
        // консервативно считаем перманентной, чтобы не зациклить ретраи на заведомо неприменимой
        // к синхронизации ошибке.
        is AnixError.PlaybackResolve -> ErrorClassification.PERMANENT
        // Неопознанная ошибка — безопасный дефолт ретраить, а не молча терять операцию из очереди.
        is AnixError.Unknown -> ErrorClassification.RETRYABLE
    }

/**
 * Данные операции нарушают собственный контракт [SyncOperation] (например, для
 * [SyncOperationKind.EPISODE_SET_WATCHED] не задан обязательный `position`) — такое не должно
 * происходить, ответственность стороны, вызывающей `enqueue`, вне трека C. Сообщение — только для
 * логов/debug, поэтому по конвенции [AnixError] пишем его по-английски.
 */
private class InvalidSyncOperationException(
    message: String,
) : Exception(message)

private fun invalidOperation(
    operation: SyncOperation,
    reason: String,
): InvalidSyncOperationException = InvalidSyncOperationException("${operation.kind} id=${operation.id}: $reason")

private fun earliestOf(
    current: Duration?,
    candidate: Duration,
): Duration = if (current == null || candidate < current) candidate else current

/** Внутренняя разметка исхода одной операции — не публичный тип, [SyncQueueWorker.drain] сразу его разбирает. */
private sealed interface Outcome {
    data object Success : Outcome

    data class Failure(
        val error: AnixError,
    ) : Outcome

    /** Операция структурно не может быть отправлена — не сетевая/доменная ошибка, поэтому не
     * участвует в [classify], всегда терминальна (операция просто удаляется из очереди). */
    data class Invalid(
        val reason: String,
    ) : Outcome
}

/**
 * Воркер офлайн-очереди (P4.T5, трек C).
 *
 * Забирает "созревшие" (см. [SyncQueueStore.dueOperations]) операции по одной, в порядке,
 * который вернул стор (FIFO по [SyncOperation.id] — это обеспечивает сам SQL-запрос трека A,
 * здесь мы только потребляем этот порядок, не переупорядочиваем сами), и применяет каждую через
 * соответствующий Api-класс из `:shared:data`.
 *
 * Архитектурное решение (P4.T6, зафиксировано, не подлежит пересмотру внутри этого класса):
 * очередь — не журнал событий, а таблица "последнее намерение пользователя на сущность"
 * (коалесцирование по [SyncOperation.entityKey] делает сам стор при `enqueue`). Поэтому здесь нет
 * и не должно быть попытки восстановить историю промежуточных состояний — только применить
 * финальное намерение, которое стор уже вернул.
 *
 * Два отступления от исходного дизайн-наброска трека C (расхождение между брифом и реальными S1
 * контрактами, см. итоговый отчёт агента):
 * 1. В [SyncOperation]/[ListMembershipStore]/[EpisodeProgressStore] (контракт S1) нет флага
 *    "pending" — оба стора хранят только последнее известное значение с `updatedAt`, без
 *    отдельного признака "ждёт подтверждения сервера" и без метода вроде `clearPending`. Поэтому
 *    при перманентной ошибке (см. [classify]) операция здесь только удаляется из очереди — снимать
 *    несуществующий "pending"-флаг нечем; значение само поправится при следующем обычном
 *    cache-first рефреше (`cacheFirstFlow`, трек B). По той же причине [progress] сейчас не
 *    используется ни разу внутри класса — параметр сохранён ради формы конструктора и на случай,
 *    если будущая итерация S1 добавит touch-метод.
 * 2. У [SyncOperationKind.LIST_REMOVE] нет собственного статуса в [SyncOperation]
 *    ([SyncOperation.statusApiValue] по KDoc S1 предназначен только для
 *    [SyncOperationKind.LIST_SET_STATUS]), а `ProfileListApi.removeFromList` требует [ListStatus]
 *    в URL (`profile/list/delete/{status}/{r_id}`). Разрешаем это чтением текущего известного
 *    статуса из [membership] в момент дрейна ([resolveRemovalStatus]); если локально тоже ничего
 *    не известно — считаем операцию тривиально выполненной (цель "не числиться в списке" уже
 *    достигнута) и убираем её из очереди без сетевого вызова.
 *
 * @param clock источник времени — параметр, а не `Clock.System` напрямую, чтобы тесты могли
 * подставить фиксированное/управляемое время (тот же приём, что и в `cacheFirstFlow`).
 *
 * `@Suppress("LongParameterList")`: 4 Api-класса (по одному на REST-неймспейс) + 3 стора + Clock —
 * намеренная агрегация зависимостей одного координирующего класса, не ветвящаяся логика внутри
 * конструктора; разбивать на под-объекты ради формального лимита детекта добавило бы косвенность
 * без пользы для читаемости.
 */
@Suppress("LongParameterList")
class SyncQueueWorker(
    private val queue: SyncQueueStore,
    private val membership: ListMembershipStore,
    @Suppress("UnusedPrivateMember")
    // См. class-level KDoc, пункт 1: в текущем S1-контракте нет touch-метода для прогресса серий,
    // которым имело бы смысл здесь воспользоваться — параметр сохранён ради сигнатуры из брифа.
    private val progress: EpisodeProgressStore,
    private val profileListApi: ProfileListApi,
    private val favoriteApi: FavoriteApi,
    private val historyApi: HistoryApi,
    private val episodeApi: EpisodeApi,
    private val clock: Clock,
) {
    /** Результат одного прохода [drain]. */
    sealed interface DrainResult {
        /** Очередь не содержала операций, готовых к отправке прямо сейчас. */
        data object Idle : DrainResult

        /** Все обработанные операции завершились терминально (успех или перманентный отказ). */
        data object Completed : DrainResult

        /**
         * Хотя бы одна операция получила retryable-ошибку; [retryAfter] — минимальный интервал
         * до следующей осмысленной попытки среди всех неудачных операций этого прохода.
         */
        data class Backoff(
            val retryAfter: Duration,
        ) : DrainResult

        /** Первая же операция получила 401/403 — проход остановлен, остальные операции не тронуты. */
        data object Unauthorized : DrainResult
    }

    /**
     * Один проход по очереди. См. class-level KDoc про порядок обработки и про то, что
     * коалесцирование по `entityKey` — забота стора, не этого метода.
     */
    suspend fun drain(): DrainResult {
        val dueOperations = queue.dueOperations(clock.now())

        var minRetryAfter: Duration? = null
        for (operation in dueOperations) {
            when (val result = handle(operation)) {
                HandleResult.Handled -> Unit
                is HandleResult.Retryable -> minRetryAfter = earliestOf(minRetryAfter, result.retryAfter)
                HandleResult.Unauthorized -> return DrainResult.Unauthorized
            }
        }

        return when {
            dueOperations.isEmpty() -> DrainResult.Idle
            minRetryAfter != null -> DrainResult.Backoff(minRetryAfter)
            else -> DrainResult.Completed
        }
    }

    /** Итог обработки одной операции — сворачивает [Outcome] + [classify] в решение для [drain]. */
    private sealed interface HandleResult {
        data object Handled : HandleResult

        data class Retryable(
            val retryAfter: Duration,
        ) : HandleResult

        data object Unauthorized : HandleResult
    }

    private suspend fun handle(operation: SyncOperation): HandleResult =
        when (val outcome = process(operation)) {
            Outcome.Success -> {
                queue.remove(operation.id)
                HandleResult.Handled
            }
            is Outcome.Invalid -> {
                queue.remove(operation.id)
                HandleResult.Handled
            }
            is Outcome.Failure ->
                when (classify(outcome.error)) {
                    ErrorClassification.UNAUTHORIZED -> HandleResult.Unauthorized
                    ErrorClassification.PERMANENT -> {
                        queue.remove(operation.id)
                        HandleResult.Handled
                    }
                    ErrorClassification.RETRYABLE ->
                        HandleResult.Retryable(recordRetry(queue, clock, operation, outcome.error))
                }
        }

    private suspend fun process(operation: SyncOperation): Outcome =
        try {
            execute(operation)
            Outcome.Success
        } catch (error: AnixError) {
            Outcome.Failure(error)
        } catch (error: InvalidSyncOperationException) {
            Outcome.Invalid(error.message.orEmpty())
        }

    private suspend fun execute(operation: SyncOperation) {
        when (operation.kind) {
            SyncOperationKind.LIST_SET_STATUS -> executeListSetStatus(operation)
            SyncOperationKind.LIST_REMOVE -> executeListRemove(operation)
            SyncOperationKind.FAVORITE_SET -> executeFavoriteSet(operation)
            SyncOperationKind.EPISODE_SET_WATCHED -> executeEpisodeSetWatched(operation)
            SyncOperationKind.HISTORY_ADD -> executeHistoryAdd(operation)
            SyncOperationKind.HISTORY_REMOVE -> historyApi.delete(operation.releaseId)
        }
    }

    private suspend fun executeListSetStatus(operation: SyncOperation) {
        val status =
            ListStatus.fromApiValue(operation.statusApiValue)
                ?: throw invalidOperation(operation, "unrecognized statusApiValue=${operation.statusApiValue}")
        profileListApi.addToList(status, operation.releaseId)
    }

    private suspend fun executeListRemove(operation: SyncOperation) {
        val status = resolveRemovalStatus(operation) ?: return
        profileListApi.removeFromList(status, operation.releaseId)
    }

    private suspend fun executeFavoriteSet(operation: SyncOperation) {
        if (operation.boolArg == true) {
            favoriteApi.addFavorite(operation.releaseId)
        } else {
            favoriteApi.removeFavorite(operation.releaseId)
        }
    }

    private suspend fun executeEpisodeSetWatched(operation: SyncOperation) {
        val sourceId = operation.sourceId ?: throw invalidOperation(operation, "missing sourceId")
        val position = operation.position ?: throw invalidOperation(operation, "missing position")
        if (operation.boolArg == true) {
            episodeApi.markWatched(operation.releaseId, sourceId, position)
        } else {
            episodeApi.markUnwatched(operation.releaseId, sourceId, position)
        }
    }

    private suspend fun executeHistoryAdd(operation: SyncOperation) {
        val sourceId = operation.sourceId ?: throw invalidOperation(operation, "missing sourceId")
        val position = operation.position ?: throw invalidOperation(operation, "missing position")
        historyApi.add(operation.releaseId, sourceId, position)
    }

    /**
     * [SyncOperationKind.LIST_REMOVE] не несёт статус сам по себе (см. class-level KDoc, пункт 2)
     * — подстраховываемся полем самой операции (на случай, если сторона `enqueue` его всё же
     * заполнит) и иначе читаем текущий известный статус из [membership].
     */
    private suspend fun resolveRemovalStatus(operation: SyncOperation): ListStatus? =
        ListStatus.fromApiValue(operation.statusApiValue)
            ?: membership.observeStatus(operation.releaseId).first()
}

private suspend fun recordRetry(
    queue: SyncQueueStore,
    clock: Clock,
    operation: SyncOperation,
    error: AnixError,
): Duration {
    val retryAfter = backoffFor(operation.attemptCount)
    queue.recordAttempt(
        id = operation.id,
        nextAttemptAt = clock.now() + retryAfter,
        lastError = error.message,
    )
    return retryAfter
}
