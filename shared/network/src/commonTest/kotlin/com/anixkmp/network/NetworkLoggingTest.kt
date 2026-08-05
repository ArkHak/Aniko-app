package com.anixkmp.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NetworkLoggingTest {

    private val secret = "b3f1c9d2e8a74f60"

    @Test
    fun redactsTokenInQueryString() {
        val message = "REQUEST: https://api-s.anixsekai.com/release/1?extended_mode=true&token=$secret"
        val redacted = redactSecrets(message)

        assertFalse(secret in redacted, "Токен не должен попадать в лог: $redacted")
        assertEquals(
            "REQUEST: https://api-s.anixsekai.com/release/1?extended_mode=true&token=$REDACTED",
            redacted,
        )
    }

    @Test
    fun redactsTokenWhenItIsFirstQueryParam() {
        val redacted = redactSecrets("GET /profile/list/all/1/0?token=$secret&sort=0")

        assertFalse(secret in redacted)
        assertEquals("GET /profile/list/all/1/0?token=$REDACTED&sort=0", redacted)
    }

    @Test
    fun redactsTokenInJsonBody() {
        val redacted = redactSecrets("""{"code":0,"profileToken":{"id":42,"token":"$secret"}}""")

        assertFalse(secret in redacted)
        assertEquals("""{"code":0,"profileToken":{"id":42,"token":"$REDACTED"}}""", redacted)
    }

    @Test
    fun keepsUnrelatedContentIntact() {
        val message = "REQUEST: https://api-s.anixsekai.com/episode/1/2/3?sort=0"
        assertEquals(message, redactSecrets(message))
    }
}
