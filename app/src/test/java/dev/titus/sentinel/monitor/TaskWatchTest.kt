package dev.titus.sentinel.monitor

import org.junit.Assert.*
import org.junit.Test

class TaskWatchTest {
    @Test fun unknownOutcomeIsTerminalAndNeverBecomesSuccess() {
        val watch=TaskWatch("12:34:00000000-0000-0000-0000-000000000000","job").observe("running",10)
            .observe("unavailable",20).observe("ended_unknown",30)
        assertEquals(30L,watch.observedMillis)
        assertTrue(watch.terminal)
        assertEquals(watch,watch.observe("running",40))
        assertEquals("Ended · outcome unknown",watch.label)
    }
    @Test fun disconnectKeepsLastEvidenceAndHistoryIsBounded() {
        var watch=TaskWatch("identity","job").observe("running",10).observe("unavailable",20)
        assertEquals(10L,watch.observedMillis)
        repeat(100) { watch=watch.observe(if(it%2==0) "running" else "unavailable",30L+it) }
        assertEquals(20,watch.events.size)
    }
    @Test(expected=IllegalArgumentException::class) fun arbitrarySuccessIsRejected() {
        TaskWatch("identity","job").observe("succeeded",1)
    }
}
