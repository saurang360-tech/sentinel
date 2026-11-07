package dev.titus.sentinel.monitor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly invoked on the owner's connected phone after pairing approval.
 * Only status reads and a deliberately rejected wrong-host-pin handshake occur.
 */
@RunWith(AndroidJUnit4::class)
class LiveStatusTest {
    @Test fun authenticatedStatusFromTitusUsingDeviceKeystoreKey() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val transport=SshTransport()
        try {
            val result=withTimeout(15_000) { transport.probe(MonitorStore(context).settings(),PhoneIdentity(context)) }
            assertTrue("Expected authenticated status, got $result",result is ProbeResult.Success)
            val sample=(result as ProbeResult.Success).snapshot
            assertTrue(sample.host.isNotBlank())
            assertTrue(sample.uptimeSeconds > 0)
            sample.validate()
            // Verify reuse of the same SSH session for a second read.
            val again=withTimeout(10_000) { transport.probe(MonitorStore(context).settings(),PhoneIdentity(context)) }
            assertTrue(again is ProbeResult.Success)
            assertEquals(sample.bootId,(again as ProbeResult.Success).snapshot.bootId)
        } finally { transport.close() }
    }
    @Test fun liveProcessesRespectOwnerFiltersAndContainOnlyMetadata() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val transport=SshTransport()
        try {
            val settings=MonitorStore(context).settings();val identity=PhoneIdentity(context)
            val status=withTimeout(15_000) { transport.probe(settings,identity) } as ProbeResult.Success
            val users=status.snapshot.users.ifEmpty {
                (transport.probe(settings,identity,ProcessQuery()) as ProbeResult.Processes).snapshot.users
            }
            assertTrue("Users must be available from the helper or its process sample",users.isNotEmpty())
            for(sort in ProcessSort.entries) {
                for(uid in listOf<Int?>(null)+users.map { it.uid }) {
                    val query=ProcessQuery(uid,sort)
                    val result=withTimeout(15_000) { transport.probe(settings,identity,query) }
                    assertTrue("Expected live processes for $query, got $result",result is ProbeResult.Processes)
                    val sample=(result as ProbeResult.Processes).snapshot
                    sample.validate();assertEquals(query,sample.query)
                    if(uid!=null) assertTrue(sample.rows.all { it.uid==uid })
                    val values=sample.rows.map { if(sort==ProcessSort.CPU) it.cpuPercent ?: -1.0 else it.rssBytes?.toDouble() ?: -1.0 }
                    assertTrue("Rows must be sorted before the cap",values.zipWithNext().all { (a,b)->a>=b })
                }
            }
            val all=transport.probe(settings,identity,ProcessQuery(includeSystem=true)) as ProbeResult.Processes
            assertTrue(all.snapshot.rows.any { row->users.none { it.uid==row.uid } })

        } finally { transport.close() }
    }
    @Test fun liveHealthUsesRealLinuxCountersAndNullableSensors() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val transport=SshTransport()
        try {
            val result=withTimeout(15_000) { transport.probe(MonitorStore(context).settings(),PhoneIdentity(context),health=true) }
            assertTrue("Expected live health, got $result",result is ProbeResult.Health)
            val j=(result as ProbeResult.Health).snapshot.data
            assertTrue(j.getString("host").isNotBlank())
            assertTrue(j.getJSONObject("memory").getLong("totalBytes")>1_073_741_824)
            assertTrue(j.getJSONObject("cpu").getDouble("utilisationPercent") in 0.0..100.0)
            assertTrue(j.getJSONObject("cpu").getInt("logicalCores")>0)
            assertTrue(j.getJSONArray("volumes").getJSONObject(0).getBoolean("mounted"))
            assertTrue(j.getJSONArray("networks").length()>0)
            assertTrue(j.getJSONArray("temperatures").length()>0)
            // Decoder rejects malformed measurements rather than rendering them.
            val invalid=org.json.JSONObject(j.toString())
            invalid.getJSONObject("cpu").put("utilisationPercent",101)
            assertTrue(runCatching { decodeHealth(invalid.toString()) }.isFailure)
        } finally { transport.close() }
    }
    @Test fun healthSurvivesPollingIntervalsAndReconnects() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val transport=SshTransport()
        val identity=PhoneIdentity(context)
        try {
            repeat(8) { index ->
                if(index>0) kotlinx.coroutines.delay(10_000)
                val result=withTimeout(15_000) { transport.probe(MonitorStore(context).settings(),identity,health=true) }
                assertTrue("Health poll $index failed: $result",result is ProbeResult.Health)
                if(index==4) transport.disconnect()
            }
        } finally { transport.close() }
    }
    @Test fun wrongHostPinIsRejectedBeforeAuthentication() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val wrong=MonitorStore(context).settings().copy(fingerprint="SHA256:"+"A".repeat(43))
        val transport=SshTransport()
        try {
            val result=withTimeout(15_000) { transport.probe(wrong,PhoneIdentity(context)) }
            assertTrue("Expected a security failure, got $result",result is ProbeResult.Failed)
            assertEquals(Phase.SecurityError,(result as ProbeResult.Failed).phase)
        } finally { transport.close() }
    }
}
