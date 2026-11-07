package dev.titus.sentinel.monitor

import org.junit.Assert.*
import org.junit.Test

class HealthModelTest {
    @Test fun interruptedHealthMustRetryButSecurityFailuresMustStop() {
        assertFalse(Phase.NoData.stopsAutomaticRetry())
        assertFalse(Phase.Unreachable.stopsAutomaticRetry())
        assertTrue(Phase.SecurityError.stopsAutomaticRetry())
        assertTrue(Phase.Pairing.stopsAutomaticRetry())
    }

    @Test fun staleHealthDoesNotKeepOnlineBadgeAfterTimeoutOrClockReset() {
        val ui=HealthUi(phase=Phase.Online,receivedElapsed=1000)
        assertEquals(Phase.Online,ui.aged(21000).phase)
        assertEquals(Phase.Stale,ui.aged(21001).phase)
        assertEquals(Phase.Stale,ui.aged(999).phase)
        assertEquals(Phase.Unreachable,ui.copy(phase=Phase.Unreachable).aged(30000).phase)
    }
}
