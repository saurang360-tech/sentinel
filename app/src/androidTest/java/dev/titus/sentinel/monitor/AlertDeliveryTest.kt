package dev.titus.sentinel.monitor

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertDeliveryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun quietQueueDrainsAndMutedOrLowSeverityEventsDoNotReplay() {
        val prefs=context.getSharedPreferences("sentinel-test-alert-policy",0)
        val receipts=context.getSharedPreferences("sentinel-test-alert-receipts",0)
        val manager=context.getSystemService(NotificationManager::class.java)
        prefs.edit().clear().commit();receipts.edit().clear().commit()
        try {
            val policy=AlertPreferences(context,"sentinel-test-alert-policy")
            val notifier=TaskNotifications(context,"sentinel-test-alert-receipts")
            policy.save(AlertPolicy(quiet=true,startHour=0,endHour=0,cooldownSeconds=0))
            val event=OutcomeEvent("synthetic:job:one:failed","Synthetic failure",3)
            assertTrue(notifier.handoff(listOf(event))!!.contains("deferred"))
            assertFalse(manager.activeNotifications.any { it.id==TaskNotifications.RESULT })
            policy.save(AlertPolicy(cooldownSeconds=0))
            assertTrue(notifier.handoff(emptyList())!!.contains("handed to Android"))
            assertNull(TaskNotifications(context,"sentinel-test-alert-receipts").handoff(listOf(event)))
            val muted=event.copy(key="synthetic:job:muted:failed",scope="synthetic:job:muted")
            policy.setMuted(muted.scope,true)
            assertNull(notifier.handoff(listOf(muted)))
            policy.setMuted(muted.scope,false)
            assertNull(notifier.handoff(listOf(muted)))
            policy.save(AlertPolicy(minimumSeverity=3,cooldownSeconds=0))
            val success=event.copy(key="synthetic:job:info:succeeded",severity=1)
            assertNull(notifier.handoff(listOf(success)))
            policy.save(AlertPolicy(cooldownSeconds=0))
            assertNull(notifier.handoff(listOf(success)))
            assertFalse(prefs.getStringSet("muted",emptySet())!!.any { it.contains("synthetic") })
        } finally {
            manager.activeNotifications.filter { it.id==TaskNotifications.RESULT }.forEach { manager.cancel(it.tag,it.id) }
            prefs.edit().clear().commit();receipts.edit().clear().commit()
        }
    }
    @Test fun resolvedHealthOrAssuranceDoesNotReplayAnObsoleteQueuedWarning() {
        val prefs=context.getSharedPreferences("sentinel-test-alert-policy",0)
        val receipts=context.getSharedPreferences("sentinel-test-alert-receipts",0)
        val manager=context.getSystemService(NotificationManager::class.java)
        prefs.edit().clear().commit();receipts.edit().clear().commit()
        try {
            val policy=AlertPreferences(context,"sentinel-test-alert-policy")
            policy.save(AlertPolicy(quiet=true,startHour=0,endHour=0,minimumSeverity=2,cooldownSeconds=0,health=true))
            val notifier=TaskNotifications(context,"sentinel-test-alert-receipts")
            val scope="synthetic:health:ram"
            assertTrue(notifier.handoff(listOf(OutcomeEvent("$scope:1:false","RAM warning",2,scope)))!!.contains("deferred"))
            assertNull(notifier.handoff(listOf(OutcomeEvent("$scope:1:true","RAM recovered",1,scope))))
            val assurance=OutcomeEvent("synthetic:expectation:one:100","Deadline overdue",2)
            assertTrue(notifier.handoff(listOf(assurance))!!.contains("deferred"))
            assertNull(notifier.handoff(emptyList(),setOf(assurance.scope)))
            policy.save(AlertPolicy(cooldownSeconds=0,health=true))
            assertNull(notifier.handoff(emptyList()))
            assertFalse(manager.activeNotifications.any { it.id==TaskNotifications.RESULT })
        } finally {
            manager.activeNotifications.filter { it.id==TaskNotifications.RESULT }.forEach { manager.cancel(it.tag,it.id) }
            prefs.edit().clear().commit();receipts.edit().clear().commit()
        }
    }
    @Test fun missingHardwareCriticalTemperatureIsUnavailableAndAssuranceDoesNotClaimFailure() {
        val data=JSONObject("""{"cpu":{"temperatureC":95},"memory":{"totalBytes":100,"availableBytes":5,"pressureSomeAvg10":null},"volumes":[{"path":"/","label":"System","mounted":false,"totalBytes":100,"availableBytes":0}]}""")
        val readings=healthReadings(HealthSnapshot(data))
        assertNull(readings.first { it.key=="cpu-temperature" }.value)
        assertNull(readings.first { it.key=="volume:/" }.value)
        assertEquals(5.0,readings.first { it.key=="memory-available" }.value!!,0.001)
        val raw="""{"protocol":1,"kind":"jobs","expectations":[{"id":"00000000-0000-0000-0000-000000000001","label":"Backup","lastSuccessMillis":null,"deadlineMillis":100,"state":"overdue_unknown"}]}"""
        val events=expectationEvents("synthetic",decodeExpectations(raw))
        assertTrue(events.single().text.contains("outcome unknown"))
        assertFalse(events.single().text.contains("failed"))
        assertEquals(2,events.single().severity)
    }
}
