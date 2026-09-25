package com.aniko.data.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {
    @Test
    fun parsesTagsWithAndWithoutPrefix() {
        assertEquals(AppVersion(0, 1, 0), AppVersion.parse("v0.1.0"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("1.2.3"))
        assertEquals(AppVersion(1, 0, 0, "beta.1"), AppVersion.parse("v1.0.0-beta.1"))
        assertEquals(AppVersion(2, 0, 0), AppVersion.parse("2.0.0+build.5"))
    }

    @Test
    fun rejectsNonVersions() {
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1.2"))
        assertNull(AppVersion.parse("v1.2.x"))
        assertNull(AppVersion.parse(""))
    }

    @Test
    fun ordersByNumbersNotText() {
        assertTrue(AppVersion(0, 9, 0) < AppVersion(0, 10, 0))
        assertTrue(AppVersion(0, 1, 9) < AppVersion(0, 1, 10))
        assertTrue(AppVersion(0, 1, 0) < AppVersion(1, 0, 0))
    }

    @Test
    fun releaseIsNewerThanItsPreRelease() {
        assertTrue(AppVersion(1, 0, 0, "rc.1") < AppVersion(1, 0, 0))
        assertTrue(AppVersion(1, 0, 0, "beta.2") < AppVersion(1, 0, 0, "beta.10"))
        assertTrue(AppVersion(1, 0, 0, "alpha") < AppVersion(1, 0, 0, "beta"))
        assertTrue(AppVersion(1, 0, 0, "1") < AppVersion(1, 0, 0, "alpha"))
    }

    @Test
    fun toStringRoundTrips() {
        assertEquals("0.1.0", AppVersion(0, 1, 0).toString())
        assertEquals("1.0.0-beta.1", AppVersion(1, 0, 0, "beta.1").toString())
    }
}
