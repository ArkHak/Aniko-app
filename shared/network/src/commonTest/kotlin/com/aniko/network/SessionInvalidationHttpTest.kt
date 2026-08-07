package com.aniko.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SessionInvalidationHttpTest {
    @Test
    fun http401OnNonAuthPath_callsOnUnauthorized_noAutoRetry() =
        runTest {
            val spy = SessionInvalidatorSpy()
            val requestLog = mutableListOf<String>()

            val mockEngine =
                MockEngine { request ->
                    requestLog.add(request.url.encodedPath)
                    respond("Unauthorized", HttpStatusCode.Unauthorized)
                }

            val httpClient =
                HttpClient(mockEngine) {
                    expectSuccess = true
                    HttpResponseValidator {
                        handleResponseExceptionWithRequest { cause, request ->
                            val responseException = cause as? ResponseException ?: return@handleResponseExceptionWithRequest
                            val status = responseException.response.status
                            if (status != HttpStatusCode.Unauthorized && status != HttpStatusCode.Forbidden) {
                                return@handleResponseExceptionWithRequest
                            }

                            val path = request.url.encodedPath.removePrefix("/")
                            if (path.startsWith("auth/")) {
                                return@handleResponseExceptionWithRequest
                            }

                            spy.callCount++
                        }
                    }
                }

            val exception =
                runCatching {
                    httpClient.get("/some/path")
                }

            assertEquals(1, requestLog.size, "Should make exactly 1 request, no retry")
            assertEquals("/some/path", requestLog[0])
            assertEquals(1, spy.callCount, "onUnauthorized should be called once")
            assertFalse(exception.isSuccess, "Exception should propagate")
        }

    @Test
    fun http401OnAuthPath_doesNotCallOnUnauthorized() =
        runTest {
            val spy = SessionInvalidatorSpy()
            val requestLog = mutableListOf<String>()

            val mockEngine =
                MockEngine { request ->
                    requestLog.add(request.url.encodedPath)
                    respond("Unauthorized", HttpStatusCode.Unauthorized)
                }

            val httpClient =
                HttpClient(mockEngine) {
                    expectSuccess = true
                    HttpResponseValidator {
                        handleResponseExceptionWithRequest { cause, request ->
                            val responseException = cause as? ResponseException ?: return@handleResponseExceptionWithRequest
                            val status = responseException.response.status
                            if (status != HttpStatusCode.Unauthorized && status != HttpStatusCode.Forbidden) {
                                return@handleResponseExceptionWithRequest
                            }

                            val path = request.url.encodedPath.removePrefix("/")
                            if (path.startsWith("auth/")) {
                                return@handleResponseExceptionWithRequest
                            }

                            spy.callCount++
                        }
                    }
                }

            val exception =
                runCatching {
                    httpClient.get("/auth/signIn")
                }

            assertEquals(1, requestLog.size)
            assertEquals("/auth/signIn", requestLog[0])
            assertEquals(0, spy.callCount, "onUnauthorized should NOT be called for auth/* paths")
            assertFalse(exception.isSuccess)
        }

    @Test
    fun http403OnNonAuthPath_callsOnUnauthorized() =
        runTest {
            val spy = SessionInvalidatorSpy()
            val requestLog = mutableListOf<String>()

            val mockEngine =
                MockEngine { request ->
                    requestLog.add(request.url.encodedPath)
                    respond("Forbidden", HttpStatusCode.Forbidden)
                }

            val httpClient =
                HttpClient(mockEngine) {
                    expectSuccess = true
                    HttpResponseValidator {
                        handleResponseExceptionWithRequest { cause, request ->
                            val responseException = cause as? ResponseException ?: return@handleResponseExceptionWithRequest
                            val status = responseException.response.status
                            if (status != HttpStatusCode.Unauthorized && status != HttpStatusCode.Forbidden) {
                                return@handleResponseExceptionWithRequest
                            }

                            val path = request.url.encodedPath.removePrefix("/")
                            if (path.startsWith("auth/")) {
                                return@handleResponseExceptionWithRequest
                            }

                            spy.callCount++
                        }
                    }
                }

            val exception =
                runCatching {
                    httpClient.get("/user/profile")
                }

            assertEquals(1, requestLog.size)
            assertEquals(1, spy.callCount, "403 should trigger invalidation outside auth/*")
            assertFalse(exception.isSuccess)
        }

    private class SessionInvalidatorSpy : SessionInvalidator {
        var callCount = 0

        override suspend fun onUnauthorized() {
            callCount++
        }
    }
}
