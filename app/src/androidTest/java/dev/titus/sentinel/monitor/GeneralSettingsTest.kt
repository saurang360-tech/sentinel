package dev.titus.sentinel.monitor

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeneralSettingsTest {
    @Test fun freshSettingsAndLegacyUpgradeDoNotAffectExistingPhoneConnection() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val name="sentinel-settings-regression"
        val isolated=object: ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String,mode: Int)=base.getSharedPreferences(name,mode)
        }
        val prefs=base.getSharedPreferences(name,Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store=MonitorStore(isolated)
            assertEquals("",store.settings().host)
            assertNotNull(store.settings().validationError())
            prefs.edit().putString("sample","{}").commit()
            assertEquals("",store.settings().host)
            assertEquals(22,store.settings().port)
            val custom=ConnectionSettings("new-pc.example.ts.net",2223,"alice",("SHA256:" + "B".repeat(43)),"Office")
            store.saveSettings(custom)
            assertEquals(custom,store.settings())
            assertNull(store.cached().snapshot)
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun legacyHelperSampleCanSortByRamWithoutChangingTheServer() {
        val raw="""{"protocol":1,"kind":"processes","host":"pc","sampledAt":"2026-10-28T00:00:00Z","ownerFilter":"all","cpuBasis":"total-machine","sampleSeconds":0.4,"totalMatching":3,"limited":true,"scanLimited":false,"denied":0,"vanished":0,"processes":[{"pid":1,"name":"cpu-task","uid":1001,"owner":"alice","state":"S","startTicks":1,"cpuPercent":70,"rssBytes":100,"runtimeSeconds":10},{"pid":2,"name":"ram-task","uid":1001,"owner":"alice","state":"S","startTicks":1,"cpuPercent":1,"rssBytes":5000,"runtimeSeconds":10},{"pid":3,"name":"system","uid":0,"owner":"root","state":"S","startTicks":1,"cpuPercent":80,"rssBytes":8000,"runtimeSeconds":10}]}"""
        val ram=decodeLegacyProcesses(raw,ProcessQuery(sort=ProcessSort.RAM))
        assertEquals(listOf(2,1),ram.rows.map { it.pid })
        assertFalse(ram.serverSorted);assertTrue(ram.limited)
        assertEquals("alice",ram.users.single().name)
        val cpu=decodeLegacyProcesses(raw,ProcessQuery(sort=ProcessSort.CPU,includeSystem=true))
        assertEquals(listOf(3,1,2),cpu.rows.map { it.pid })
    }
    @Test fun discoveredUsersAndQueryContractRejectDuplicateOrMismatchedUids() {
        val duplicate=org.json.JSONObject("""{"users":[{"uid":1001,"name":"alice"},{"uid":1001,"name":"bob"}]}""")
        assertTrue(runCatching { decodeUsers(duplicate) }.isFailure)
        val raw="""{"protocol":2,"kind":"processes","host":"pc","sampledAt":"2026-10-28T00:00:00Z","ownerFilter":"all","sort":"ram","scope":"user","cpuBasis":"total-machine","sampleSeconds":0.4,"totalMatching":0,"limited":false,"scanLimited":false,"denied":0,"vanished":0,"users":[{"uid":1001,"name":"alice"}],"processes":[]}"""
        val sample=decodeProcesses(raw,ProcessQuery(sort=ProcessSort.RAM))
        assertEquals("alice",sample.users.single().name)
        assertTrue(runCatching { decodeProcesses(raw,ProcessQuery(sort=ProcessSort.CPU)) }.isFailure)
    }
}
