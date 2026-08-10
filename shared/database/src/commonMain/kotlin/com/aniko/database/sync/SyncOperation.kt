package com.aniko.database.sync

import com.aniko.model.ReleaseId
import kotlin.time.Instant

/**
 * Вид отложенной записи в офлайн-очереди (P4.T5). Один кейс на каждую мутирующую операцию
 * `LibraryRepository`/`EpisodeRepository`, которая должна пережить временную недоступность сети.
 */
enum class SyncOperationKind {
    /** `LibraryRepository.addToList` — установить статус релиза в списке. */
    LIST_SET_STATUS,

    /** `LibraryRepository.removeFromList` — убрать релиз из списка. */
    LIST_REMOVE,

    /** `LibraryRepository.addFavorite`/`removeFavorite` — [SyncOperation.boolArg] несёт целевое состояние. */
    FAVORITE_SET,

    /** `EpisodeRepository.markWatched`/`markUnwatched` — [SyncOperation.boolArg] несёт целевое состояние. */
    EPISODE_SET_WATCHED,

    /** `LibraryRepository.addHistory`. */
    HISTORY_ADD,

    /** `LibraryRepository.removeFromHistory`. */
    HISTORY_REMOVE,
}

/**
 * Одна запись офлайн-очереди (P4.T5) — сериализованное представление мутации, которая должна
 * быть отправлена на сервер, когда появится сеть.
 *
 * Поля намеренно плоские (не sealed-иерархия per-kind) — так строка ложится 1:1 на таблицу
 * SQLDelight без дополнительного слоя (де)сериализации; какие поля релевантны, определяется
 * значением [kind] (см. комментарии у самих полей).
 *
 * @param id первичный ключ записи в очереди (генерируется стором при [SyncQueueStore]-вставке).
 * @param kind вид операции, см. [SyncOperationKind].
 * @param entityKey ключ дедупликации — если в очереди уже есть необработанная операция с тем же
 * [entityKey], конфликт решается last-write-wins по [updatedAt] (P4.T6): новая замещает старую,
 * а не добавляется второй строкой. Пример: `"list:$releaseId"` для LIST_SET_STATUS/LIST_REMOVE
 * одного и того же релиза.
 * @param releaseId релиз, к которому относится операция — есть у всех видов.
 * @param sourceId источник озвучки — только для [SyncOperationKind.EPISODE_SET_WATCHED]/
 * [SyncOperationKind.HISTORY_ADD]/[SyncOperationKind.HISTORY_REMOVE].
 * @param position номер серии — только там же, где [sourceId].
 * @param statusApiValue см. `ListStatus.apiValue` — только для [SyncOperationKind.LIST_SET_STATUS].
 * @param boolArg целевое булево состояние — для [SyncOperationKind.FAVORITE_SET]/
 * [SyncOperationKind.EPISODE_SET_WATCHED].
 * @param createdAt когда операция впервые попала в очередь.
 * @param updatedAt когда запись последний раз заменена по [entityKey] (используется при
 * last-write-wins разрешении конфликтов, P4.T6) либо когда была последняя попытка отправки.
 * @param attemptCount сколько раз воркер уже пытался отправить эту операцию.
 * @param nextAttemptAt не пытаться раньше этого момента (backoff после неудачи), `null` — можно
 * пытаться сразу.
 * @param lastError технический текст последней ошибки отправки, для диагностики/debug-экрана.
 */
data class SyncOperation(
    val id: Long,
    val kind: SyncOperationKind,
    val entityKey: String,
    val releaseId: ReleaseId,
    val sourceId: Int? = null,
    val position: Int? = null,
    val statusApiValue: Int? = null,
    val boolArg: Boolean? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val attemptCount: Int = 0,
    val nextAttemptAt: Instant? = null,
    val lastError: String? = null,
)
