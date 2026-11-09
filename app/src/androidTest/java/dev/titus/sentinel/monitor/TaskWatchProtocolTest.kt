package dev.titus.sentinel.monitor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskWatchProtocolTest {
    private val identity="123:456:00000000-0000-0000-0000-000000000000"
    @Test fun exactIdentityAndOutcomeValidation() {
        val watch=TaskWatch(identity,"test")
        val raw="""{"protocol":1,"kind":"watches","sampledAt":"2026-11-07T12:00:00Z","watches":[{"identity":"$identity","state":"ended_unknown"}]}"""
        val result=decodeWatchResult(raw,listOf(watch),100)
        assertEquals("ended_unknown",result.single().state)
        assertEquals(result.single(),TaskWatch.decode(result.single().encode()))
        assertThrows(IllegalArgumentException::class.java) { decodeWatchResult(raw.replace("123:456:","123:457:"),listOf(watch),100) }
        assertThrows(IllegalArgumentException::class.java) { decodeWatchResult(raw.replace("ended_unknown","succeeded"),listOf(watch),100) }
    }
    @Test fun verifiedSuccessRequiresZeroExitAndValidIdentity() {
        val raw="""{"protocol":1,"kind":"jobs","sampledAt":"2026-11-07T12:00:00Z","jobs":[{"id":"00000000-0000-0000-0000-000000000001","label":"Test","state":"succeeded","exitCode":0,"startedMillis":100,"endedMillis":200}]}"""
        assertEquals(0,decodeJobs(raw).single().exitCode)
        assertThrows(IllegalArgumentException::class.java) { decodeJobs(raw.replace("\"exitCode\":0","\"exitCode\":7")) }
    }
    @Test fun encryptedWatchesAreSeparatedByPinnedHostAndSurviveRecreation() {
        // Target UID owns this isolated preferences namespace; never use the real vault.
        val context=object : android.content.ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                super.getSharedPreferences("task-protocol-test-"+name,mode)
        }
        val prefs=context.getSharedPreferences("sentinel-status",0)
        val prior=prefs.getString("vault",null)
        try {
            val a=ConnectionSettings("one.test",22,"test","SHA256:"+"A".repeat(43))
            val b=a.copy(host="two.test")
            val store=MonitorStore(context)
            val watch=TaskWatch(identity,"job").observe("running",100)
            store.saveWatches(a,listOf(watch))
            assertEquals(listOf(watch),MonitorStore(context).watches(a))
            assertTrue(store.watches(b).isEmpty())
            assertFalse(prefs.getString("vault","")!!.contains(identity))
            store.saveWatches(a,emptyList())
            assertTrue(MonitorStore(context).watches(a).isEmpty())
        } finally {
            prefs.edit().clear().apply()
            if(prior!=null) prefs.edit().putString("vault",prior).commit()
        }
    }
}
