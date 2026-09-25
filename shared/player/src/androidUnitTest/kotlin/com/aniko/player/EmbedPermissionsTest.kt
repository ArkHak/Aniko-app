package com.aniko.player

import android.webkit.PermissionRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Политика разрешений WebView embed-плеера (issue #109, п. 4): выдаём только `PROTECTED_MEDIA_ID`. */
class EmbedPermissionsTest {
    @Test
    fun protectedMediaId_isGranted() {
        assertTrue(isEmbedPermissionGrantable(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)))
    }

    @Test
    fun emptyRequest_isNotGranted() {
        assertFalse(isEmbedPermissionGrantable(emptyArray()))
    }

    @Test
    fun cameraMicrophoneAndMidi_areDenied() {
        assertFalse(isEmbedPermissionGrantable(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)))
        assertFalse(isEmbedPermissionGrantable(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)))
        assertFalse(isEmbedPermissionGrantable(arrayOf(PermissionRequest.RESOURCE_MIDI_SYSEX)))
        assertFalse(
            isEmbedPermissionGrantable(
                arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE),
            ),
        )
    }

    @Test
    fun protectedMediaIdMixedWithOtherResources_isDeniedAsAWhole() {
        assertFalse(
            isEmbedPermissionGrantable(
                arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID, PermissionRequest.RESOURCE_AUDIO_CAPTURE),
            ),
        )
    }

    @Test
    fun unknownResource_isDenied() {
        assertFalse(isEmbedPermissionGrantable(arrayOf("android.webkit.resource.SOMETHING_NEW")))
    }
}
