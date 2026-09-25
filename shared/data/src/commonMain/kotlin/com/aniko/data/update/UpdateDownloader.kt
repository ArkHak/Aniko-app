package com.aniko.data.update

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import okio.BufferedSink
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.buffer
import okio.use

/**
 * Скачивает файл обновления и проверяет его SHA-256 по `SHA256SUMS.txt` из того же релиза.
 *
 * Загрузка идёт во временный `*.part` с хэшированием на лету и переименовывается в итоговое имя
 * только после совпадения хэша: непроверенный файл под «настоящим» именем не появляется никогда, а
 * при любой ошибке (в том числе отмене) остаток удаляется. КОНЕЧНЫЙ адрес после редиректов обязан
 * быть на хосте из [isTrustedUpdateUrl].
 *
 * Важно: SHA-256 из того же релиза защищает от порчи при загрузке, но не от подмены самого релиза;
 * подлинность на Android обеспечивает подпись APK (система не поставит обновление с другим ключом).
 */
class UpdateDownloader(
    private val client: HttpClient,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    /** Имя файла → SHA-256 из `SHA256SUMS.txt` релиза. */
    suspend fun fetchChecksums(release: AppRelease): Map<String, String> {
        val asset = release.checksumsAsset ?: throw UpdateException(UpdateError.ChecksumMissing)
        val text = guarded { client.prepareGet(asset.downloadUrl).execute { it.checked().bodyAsText() } }
        if (text.length > MAX_CHECKSUMS_CHARS) throw UpdateException(UpdateError.ChecksumMissing)
        return Sha256Sums.parse(text)
    }

    /**
     * Скачивает [asset] в [directory] и возвращает путь к проверенному файлу.
     *
     * @param onProgress доля 0..1; `null` — размер неизвестен (индикатор без процентов).
     */
    suspend fun download(
        asset: ReleaseAsset,
        expectedSha256: String,
        directory: Path,
        onProgress: (Float?) -> Unit,
    ): Path {
        val target = directory / asset.name
        val partial = directory / "${asset.name}.part"
        var completed = false
        try {
            fileSystem.createDirectories(directory)
            fileSystem.delete(partial, mustExist = false)
            fileSystem.delete(target, mustExist = false)
            val actual = guarded { streamToFile(asset, partial, onProgress) }
            verify(actual, expectedSha256)
            fileSystem.atomicMove(partial, target)
            completed = true
            return target
        } finally {
            // Любой сбой и отмена оставляют после себя чистый каталог — недокачанный/непроверенный файл не живёт.
            if (!completed) fileSystem.delete(partial, mustExist = false)
        }
    }

    private fun verify(
        actual: String,
        expected: String,
    ) {
        if (!actual.equals(expected, ignoreCase = true)) throw UpdateException(UpdateError.ChecksumMismatch)
    }

    private suspend fun streamToFile(
        asset: ReleaseAsset,
        partial: Path,
        onProgress: (Float?) -> Unit,
    ): String =
        client.prepareGet(asset.downloadUrl).execute { raw ->
            val response = raw.checked()
            val total = response.contentLength()?.takeIf { it > 0 } ?: asset.sizeBytes.takeIf { it > 0 }
            onProgress(if (total == null) null else 0f)
            val hashing = HashingSink.sha256(fileSystem.sink(partial))
            hashing.buffer().use { out -> copy(response.bodyAsChannel(), out, total, onProgress) }
            hashing.hash.hex()
        }

    private suspend fun copy(
        channel: ByteReadChannel,
        out: BufferedSink,
        total: Long?,
        onProgress: (Float?) -> Unit,
    ) {
        val chunk = ByteArray(CHUNK_BYTES)
        var received = 0L
        var read = channel.readAvailable(chunk, 0, chunk.size)
        while (read >= 0) {
            if (read > 0) {
                out.write(chunk, 0, read)
                received += read
                if (total != null) onProgress((received.toFloat() / total).coerceIn(0f, 1f))
            }
            read = channel.readAvailable(chunk, 0, chunk.size)
        }
    }

    /** Успешный статус и доверенный конечный хост — иначе загрузка отклоняется. */
    private fun HttpResponse.checked(): HttpResponse {
        if (!status.isSuccess() || !isTrustedUpdateUrl(request.url.toString())) {
            throw UpdateException(UpdateError.DownloadFailed)
        }
        return this
    }

    /** Сетевые сбои становятся [UpdateException]; отмена и уже типизированные ошибки пробрасываются как есть. */
    private suspend fun <T> guarded(block: suspend () -> T): T {
        val result = runCatching { block() }
        return result.getOrElse { throw toFailure(it) }
    }

    private fun toFailure(cause: Throwable): Throwable =
        when {
            cause is CancellationException || cause is UpdateException -> cause
            cause is HttpRequestTimeoutException -> UpdateException(UpdateError.DownloadFailed, cause)
            cause is IOException -> UpdateException(ioError(cause), cause)
            else -> cause
        }

    private fun ioError(cause: IOException): UpdateError =
        if (cause.message.orEmpty().contains("No space left", ignoreCase = true)) {
            UpdateError.InsufficientStorage
        } else {
            UpdateError.DownloadFailed
        }

    private companion object {
        const val CHUNK_BYTES = 64 * 1024
        const val MAX_CHECKSUMS_CHARS = 64 * 1024
    }
}
