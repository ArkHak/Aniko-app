package com.aniko.data.sync

import com.aniko.database.store.SyncQueueStore
import com.aniko.database.sync.SyncOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

/**
 * In-memory [SyncQueueStore] для тестов трека C, по образцу [FakeSecureTokenStorage][com.aniko.data.session.FakeSecureTokenStorage].
 *
 * Воспроизводит реальную семантику `ON CONFLICT(entity_key)` SQL-реализации (трек A, вне зоны
 * этого фейка): [enqueue] с уже занятым [SyncOperation.entityKey] заменяет содержимое
 * существующей записи, но сохраняет её исходный [SyncOperation.id] — то есть её позицию в
 * FIFO-порядке. Порядок в [dueOperations]/[observePending] — порядок вставки НОВЫХ id
 * ([LinkedHashMap] не переупорядочивает запись при повторной записи по тому же ключу), что и даёт
 * `id ASC`.
 */
class FakeSyncQueueStore : SyncQueueStore {
    private val operations = LinkedHashMap<Long, SyncOperation>()
    private var nextId = 1L
    private val pending = MutableStateFlow<List<SyncOperation>>(emptyList())

    override fun observePending(): Flow<List<SyncOperation>> = pending.asStateFlow()

    override suspend fun enqueue(operation: SyncOperation): Long {
        val existingId = operations.values.firstOrNull { it.entityKey == operation.entityKey }?.id
        val id = existingId ?: nextId++
        operations[id] = operation.copy(id = id)
        publish()
        return id
    }

    override suspend fun dueOperations(now: Instant): List<SyncOperation> =
        operations.values
            .filter {
                val nextAttemptAt = it.nextAttemptAt
                nextAttemptAt == null || nextAttemptAt <= now
            }.toList()

    override suspend fun recordAttempt(
        id: Long,
        nextAttemptAt: Instant?,
        lastError: String?,
    ) {
        val current = operations[id] ?: return
        operations[id] =
            current.copy(
                attemptCount = current.attemptCount + 1,
                nextAttemptAt = nextAttemptAt,
                lastError = lastError,
            )
        publish()
    }

    override suspend fun remove(id: Long) {
        operations.remove(id)
        publish()
    }

    /** Тестовый доступ ко всему текущему содержимому очереди, без фильтра по [dueOperations]. */
    fun snapshot(): List<SyncOperation> = operations.values.toList()

    private fun publish() {
        pending.value = operations.values.toList()
    }
}
