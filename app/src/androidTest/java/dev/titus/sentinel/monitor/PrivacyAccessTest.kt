package dev.titus.sentinel.monitor

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyAccessTest {
    @Test fun privacyAcknowledgementIsVersionedAndRequiredBeforeAccess() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=base.getSharedPreferences("privacy-access-regression",Context.MODE_PRIVATE)
        val isolated=object: ContextWrapper(base) {
            override fun getSharedPreferences(name: String,mode: Int)=prefs
        }
        prefs.edit().clear().commit()
        try {
            assertFalse(AppAccess.acknowledged(isolated))
            assertFalse(AppAccess.canConnect(isolated))
            AppAccess.acknowledge(isolated)
            assertTrue(AppAccess.acknowledged(isolated))
            prefs.edit().putInt("noticeVersion",-1).commit()
            assertFalse(AppAccess.canConnect(isolated))
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun upgradeRemovesOldReadingsWithoutLosingExplicitConnectionSettings() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=base.getSharedPreferences("privacy-upgrade-regression",Context.MODE_PRIVATE)
        val isolated=object: ContextWrapper(base) {
            override fun getSharedPreferences(name: String,mode: Int)=prefs
        }
        prefs.edit().clear().putString("sample","old private PC status")
            .putLong("lastContact",123).putString("host","example.invalid")
            .putString("username","example").putInt("port",2200).commit()
        try {
            val store=MonitorStore(isolated)
            assertFalse(prefs.contains("sample"))
            assertFalse(prefs.contains("lastContact"))
            assertNull(store.cached().snapshot)
            assertEquals("example.invalid",store.settings().host)
            assertEquals("example",store.settings().username)
            assertEquals(2200,store.settings().port)
            assertEquals("",store.settings().fingerprint)
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun demoSamplesAreValidAndSortingUsesSelectedMetric() {
        DemoData.status.snapshot!!.validate()
        decodeHealth(DemoData.health.snapshot!!.data.toString())
        val cpu=DemoData.processes(ProcessQuery()).snapshot!!
        val ram=DemoData.processes(ProcessQuery(sort=ProcessSort.RAM)).snapshot!!
        cpu.validate();ram.validate()
        assertEquals("blender",cpu.rows.first().name)
        assertEquals("firefox",ram.rows.first().name)
        assertTrue(DemoData.processes(ProcessQuery(uid=1001)).snapshot!!.rows.all { it.uid==1001 })
    }
}
