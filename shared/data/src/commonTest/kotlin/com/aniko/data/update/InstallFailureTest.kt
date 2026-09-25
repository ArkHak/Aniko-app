package com.aniko.data.update

import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InstallFailureTest {
    @Test
    fun outOfSpaceIsReportedAsInsufficientStorage() {
        assertEquals(
            InstallOutcome.Failed(UpdateError.InsufficientStorage),
            installFailureFor(IOException("write failed: ENOSPC (No space left on device)")),
        )
    }

    @Test
    fun otherFailuresAreInstallRejected() {
        assertEquals(InstallOutcome.Failed(UpdateError.InstallRejected), installFailureFor(SecurityException("denied")))
        assertEquals(InstallOutcome.Failed(UpdateError.InstallRejected), installFailureFor(IllegalStateException()))
    }

    @Test
    fun cancellationIsRethrown() {
        assertFailsWith<CancellationException> { installFailureFor(CancellationException("stop")) }
    }
}
