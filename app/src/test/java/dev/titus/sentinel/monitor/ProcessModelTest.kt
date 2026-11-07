package dev.titus.sentinel.monitor
import org.junit.Assert.*
import org.junit.Test
class ProcessModelTest {
    @Test fun processQueryContainsOnlyTypedUidSortAndScope() {
        assertEquals("sentinel-processes-v2 all cpu user",ProcessQuery().command())
        assertEquals("sentinel-processes-v2 1234 ram all",ProcessQuery(1234,ProcessSort.RAM,true).command())
        assertTrue(runCatching { ProcessQuery(-1).command() }.isFailure)
    }

    private fun row() = ProcessRow(123,"python3",1001,"skyler","S",123,4.2,1048576,120.0)
    @Test fun cpuAndRamSortsHaveDifferentLeadersAndUnknownValuesGoLast() {
        val cpu=row().copy(pid=1,cpuPercent=70.0,rssBytes=1)
        val ram=row().copy(pid=2,cpuPercent=1.0,rssBytes=5000)
        val unknown=row().copy(pid=3,cpuPercent=null,rssBytes=null)
        assertEquals(listOf(1,2,3),sortProcessRows(listOf(unknown,ram,cpu),ProcessSort.CPU).map { it.pid })
        assertEquals(listOf(2,1,3),sortProcessRows(listOf(cpu,unknown,ram),ProcessSort.RAM).map { it.pid })
    }
    @Test fun rejectsInvalidCounterValuesAndOwners() {
        row().validate()
        for(p in listOf(row().copy(pid=0),row().copy(cpuPercent=Double.NaN),row().copy(cpuPercent=101.0),row().copy(rssBytes=-1),row().copy(name="bad\nname")))
            assertTrue(runCatching { p.validate() }.isFailure)
        val snapshot=ProcessSnapshot("Titus","2026-10-27T00:00:00Z",ProcessQuery(1001),0.4,1,false,false,0,0,listOf(row()),listOf(PcUser(1001,"skyler")))
        snapshot.validate()
        assertTrue(runCatching { snapshot.copy(query=ProcessQuery(1000)).validate() }.isFailure)
        assertTrue(runCatching { snapshot.copy(rows=listOf(row(),row()),totalMatching=2).validate() }.isFailure)
    }
    @Test fun staleProcessesCannotLookLiveAndRunningIsNotAWindowClaim() {
        val ui=ProcessUi(Phase.Online,receivedElapsed=1000)
        assertEquals(Phase.Online,ui.aged(21000).phase)
        assertEquals(Phase.Stale,ui.aged(21001).phase)
        assertEquals(Phase.Stale,ui.aged(500).phase)
        assertEquals("Sleeping",row().stateLabel)
        assertEquals("Running",row().copy(state="R").stateLabel)
    }
}
