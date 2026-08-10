package com.aniko.database.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.aniko.database.AnikoDatabase
import com.aniko.database.sync.SyncOperation
import com.aniko.database.sync.SyncOperationKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import com.aniko.database.SyncOperation as SyncOperationRow

/**
 * `syncOperation` (P4.T1/T5, трек A) — коалесcинг по `entity_key` (`ON CONFLICT`, см. KDoc
 * `SyncQueue.sq`) делает сам SQL-запрос [enqueue]; здесь только читаем итоговый id обратно внутри
 * той же транзакции, т.к. `ON CONFLICT DO UPDATE` не сообщает id вставленной/обновлённой строки
 * напрямую.
 */
class SqlDelightSyncQueueStore(
    private val database: AnikoDatabase,
    private val dispatcher: CoroutineDispatcher,
) : SyncQueueStore {
    override fun observePending(): Flow<List<SyncOperation>> =
        database.syncQueueQueries
            .selectAllPending()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun enqueue(operation: SyncOperation): Long =
        withContext(dispatcher) {
            database.transactionWithResult {
                database.syncQueueQueries.enqueue(
                    kind = operation.kind.name,
                    entity_key = operation.entityKey,
                    release_id = operation.releaseId.toLong(),
                    source_id = operation.sourceId?.toLong(),
                    position = operation.position?.toLong(),
                    status_api_value = operation.statusApiValue?.toLong(),
                    bool_arg = operation.boolArg,
                    created_at = operation.createdAt.toEpochMilliseconds(),
                    updated_at = operation.updatedAt.toEpochMilliseconds(),
                )
                database.syncQueueQueries.selectIdByEntityKey(operation.entityKey).executeAsOne()
            }
        }

    override suspend fun dueOperations(now: Instant): List<SyncOperation> =
        withContext(dispatcher) {
            database.syncQueueQueries
                .selectDue(now.toEpochMilliseconds())
                .executeAsList()
                .map { it.toDomain() }
        }

    override suspend fun recordAttempt(
        id: Long,
        nextAttemptAt: Instant?,
        lastError: String?,
    ) {
        withContext(dispatcher) {
            database.syncQueueQueries.recordAttempt(
                next_attempt_at = nextAttemptAt?.toEpochMilliseconds(),
                last_error = lastError,
                id = id,
            )
        }
    }

    override suspend fun remove(id: Long) {
        withContext(dispatcher) {
            database.syncQueueQueries.remove(id)
        }
    }
}

private fun SyncOperationRow.toDomain(): SyncOperation =
    SyncOperation(
        id = id,
        kind = SyncOperationKind.valueOf(kind),
        entityKey = entity_key,
        releaseId = release_id.toInt(),
        sourceId = source_id?.toInt(),
        position = position?.toInt(),
        statusApiValue = status_api_value?.toInt(),
        boolArg = bool_arg,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
        attemptCount = attempt_count.toInt(),
        nextAttemptAt = next_attempt_at?.let(Instant::fromEpochMilliseconds),
        lastError = last_error,
    )
