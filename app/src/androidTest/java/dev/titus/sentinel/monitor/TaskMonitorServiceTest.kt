package dev.titus.sentinel.monitor

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.titus.sentinel.DemoGalleryActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic service sources only; no real connection, vault access, or task launch. */
@RunWith(AndroidJUnit4::class)
class TaskMonitorServiceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val manager=context.getSystemService(NotificationManager::class.java)
    private val config=ConnectionSettings("synthetic.invalid",22,"test","SHA256:"+"A".repeat(43))
    private fun await(message: String, condition: ()->Boolean) {
        val until=SystemClock.elapsedRealtime()+15_000
        while(SystemClock.elapsedRealtime()<until) { if(condition()) return;SystemClock.sleep(100) }
        fail(message+": "+TaskMonitoring.state.value)
    }
    private fun launch(): Activity = instrumentation.startActivitySync(Intent(context,DemoGalleryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    @Test fun serviceContinuesInBackgroundAndDeduplicatesOutcomeHandoffs() {
        val activity=launch()
        val prefs=context.getSharedPreferences("sentinel-test-alert-receipts",0)
        prefs.edit().clear().commit()
        val calls=AtomicInteger()
        var closed=false
        val source=object: TaskSource {
            override suspend fun sample(watches: List<TaskWatch>): TaskSample {
                val count=calls.incrementAndGet()
                val completed=count>=3
                return TaskSample(emptyList(),listOf(VerifiedJob("00000000-0000-0000-0000-000000000099","Synthetic job",
                    if(completed) "succeeded" else "running",if(completed) 0 else null,100,if(completed) 200 else null)))
            }
            override fun close() { closed=true }
        }
        try {
            instrumentation.runOnMainSync { assertNull(TaskMonitoring.startSession(context,TaskSession(config,emptyList(),{ source },1000))) }
            await("No initial PC sample") { TaskMonitoring.state.value.observedMillis!=null && calls.get()>=1 }
            instrumentation.runOnMainSync { activity.moveTaskToBack(true) }
            await("No outcome after activity was backgrounded") { TaskMonitoring.state.value.jobs.singleOrNull()?.state=="succeeded" }
            assertTrue(TaskMonitoring.state.value.running)
            assertTrue(TaskMonitoring.state.value.handoff.contains("handed to Android"))
            await("Not enough repeat polls") { calls.get()>=5 }
            val outcomes=manager.activeNotifications.filter { it.id==TaskNotifications.RESULT }
            assertEquals(1,outcomes.size)
            val receipts=prefs.getString("outcomes","")!!.split('\n').filter { it.isNotEmpty() }
            assertEquals(1,receipts.size)
            assertFalse(prefs.getString("outcomes","")!!.contains("Synthetic"))
            // Exercise the same explicit Stop action used in the ongoing notification.
            context.startService(Intent(context,TaskMonitorService::class.java).setAction(TaskMonitorService.STOP))
            await("Service did not stop") { !TaskMonitoring.state.value.running && closed }
            assertFalse(manager.activeNotifications.any { it.id==TaskNotifications.ONGOING })
        } finally {
            instrumentation.runOnMainSync { TaskMonitoring.stop(context);activity.finish() }
            manager.activeNotifications.filter { it.id==TaskNotifications.RESULT }.forEach { manager.cancel(it.tag,it.id) }
            prefs.edit().clear().commit()
        }
    }
    @Test fun historicalOutcomesAreBaselinedAndSecurityFailureStopsRetries() {
        val activity=launch()
        val calls=AtomicInteger()
        val prefs=context.getSharedPreferences("sentinel-test-alert-receipts",0)
        prefs.edit().clear().commit()
        val source=object: TaskSource {
            override suspend fun sample(watches: List<TaskWatch>): TaskSample {
                if(calls.incrementAndGet()>1) throw TaskProbeFailure("Synthetic host mismatch",true)
                return TaskSample(emptyList(),listOf(VerifiedJob("00000000-0000-0000-0000-000000000098","Historical job","failed",7,100,200)))
            }
            override fun close() {}
        }
        try {
            instrumentation.runOnMainSync { assertNull(TaskMonitoring.startSession(context,TaskSession(config,emptyList(),{ source },1000))) }
            await("Security failure did not stop monitoring") { calls.get()>=2 && !TaskMonitoring.state.value.running }
            assertEquals(2,calls.get())
            assertTrue(TaskMonitoring.state.value.message.contains("Synthetic host mismatch"))
            assertFalse(manager.activeNotifications.any { it.id==TaskNotifications.RESULT })
        } finally {
            instrumentation.runOnMainSync { TaskMonitoring.stop(context);activity.finish() }
            prefs.edit().clear().commit()
        }
    }
}
