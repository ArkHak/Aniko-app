package com.aniko.player

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Issue #108 — сбой загрузки libVLC (VLC не установлен) превращается в [Result.failure], а не в
 * падение экрана. Настоящий `CallbackMediaPlayerComponent` здесь не создаётся никогда: на машине без
 * libVLC его `NativeDiscovery` может зациклиться (см. KDoc `EmbedPlayerView`), поэтому фабрика — фейк.
 */
class VlcAvailabilityTest {
    @Test
    fun unsatisfiedLinkErrorBecomesFailure() {
        val result = createMediaPlayerComponent { throw UnsatisfiedLinkError("Unable to load library 'libvlc'") }
        assertTrue(result.isFailure)
        assertIs<UnsatisfiedLinkError>(result.exceptionOrNull())
    }

    @Test
    fun noClassDefFoundErrorAfterFirstFailureBecomesFailure() {
        // Повторная попытка после неудачной инициализации vlcj — это `NoClassDefFoundError` (LinkageError).
        val result = createMediaPlayerComponent { throw NoClassDefFoundError("Could not initialize class LibVlc") }
        assertTrue(result.isFailure)
    }

    @Test
    fun runtimeExceptionFromVlcjBecomesFailure() {
        val result = createMediaPlayerComponent { throw IllegalStateException("libvlc not found") }
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun unrelatedErrorsAreNotSwallowed() {
        assertFailsWith<OutOfMemoryError> { createMediaPlayerComponent { throw OutOfMemoryError() } }
    }
}
