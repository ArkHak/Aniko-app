package com.aniko.database.store

import com.aniko.database.sync.SyncOperation
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Офлайн-очередь неотправленных мутаций (P4.T5). Воркер синхронизации (трек C) читает
 * [observePending]/[dueOperations], отправляет операции на сервер и по результату вызывает
 * [remove] (успех) или [recordAttempt] (временная неудача — операция остаётся в очереди с
 * backoff'ом до [SyncOperation.nextAttemptAt]).
 */
interface SyncQueueStore {
    /** Все ещё не отправленные операции, в порядке [SyncOperation.createdAt]. */
    fun observePending(): Flow<List<SyncOperation>>

    /**
     * Добавляет операцию в очередь. Если уже есть необработанная операция с тем же
     * [SyncOperation.entityKey], реализация обязана заменить её (last-write-wins по
     * [SyncOperation.updatedAt], P4.T6), а не хранить обе. Возвращает id итоговой записи.
     */
    suspend fun enqueue(operation: SyncOperation): Long

    /** Операции, чей backoff (см. [SyncOperation.nextAttemptAt]) уже истёк к моменту [now]. */
    suspend fun dueOperations(now: Instant): List<SyncOperation>

    /** Фиксирует неудачную попытку отправки: инкремент счётчика, новый backoff, текст ошибки. */
    suspend fun recordAttempt(
        id: Long,
        nextAttemptAt: Instant?,
        lastError: String?,
    )

    /** Операция успешно отправлена — убрать из очереди. */
    suspend fun remove(id: Long)
}
