package com.aniko.data.api

import com.aniko.data.dto.ApiCodeAware
import com.aniko.model.AnixError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Единая точка маппинга сетевых исключений в [AnixError].
 *
 * Ни один слой выше `:shared:data` не должен видеть исключения Ktor или kotlinx.serialization.
 */
internal suspend fun <T> apiCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AnixError) {
        // `requireOk()` бросает `AnixError.Api` изнутри этого же блока (см. ниже) — не даём
        // общему `catch (Throwable)` перезаворачивать уже классифицированную ошибку в Unknown.
        throw e
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.Unauthorized ||
            e.response.status == HttpStatusCode.Forbidden
        ) {
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

/** Anixart отдаёт HTTP 200 даже на ошибки — реальный статус лежит в `code`. */
internal fun <T : ApiCodeAware> T.requireOk(): T {
    if (code != CODE_OK) throw AnixError.Api(code)
    return this
}

internal const val CODE_OK: Int = 0
