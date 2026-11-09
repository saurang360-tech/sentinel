package dev.titus.sentinel.monitor

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.titus.sentinel.MainActivity
import dev.titus.sentinel.R
import java.security.MessageDigest

/** Receipts contain only hashes, never connection details, task labels or command output. */
internal class TaskNotifications(private val context: Context,receiptNamespace: String="sentinel-alert-receipts") {
    private val manager=context.getSystemService(NotificationManager::class.java)
    private val receipts=context.getSharedPreferences(receiptNamespace,Context.MODE_PRIVATE)
    private val policy=AlertPreferences(context,if(receiptNamespace=="sentinel-test-alert-receipts") "sentinel-test-alert-policy" else "sentinel-alert-policy")
    private val pending=linkedMapOf<String,OutcomeEvent>()
    private var lastHandoff: Long?=null
    init {
        manager.createNotificationChannels(listOf(
            NotificationChannel(SESSION,"Task monitoring",NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(RESULTS,"Task outcomes",NotificationManager.IMPORTANCE_DEFAULT)))
    }
    fun allowed() = (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) &&
        manager.areNotificationsEnabled() && manager.getNotificationChannel(RESULTS).importance!=NotificationManager.IMPORTANCE_NONE &&
        manager.getNotificationChannel(SESSION).importance!=NotificationManager.IMPORTANCE_NONE
    private fun openApp()=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun builder(channel: String)=Notification.Builder(context,channel)
        .setSmallIcon(R.drawable.ic_sentinel_monochrome).setContentIntent(openApp())
        .setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).apply { if(policy.read().muted) { setGroup("sentinel-muted");setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY);setSound(null);setVibrate(longArrayOf(0));setDefaults(0) } }
    fun ongoing(message: String): Notification {
        val stop=PendingIntent.getService(context,1,Intent(context,TaskMonitorService::class.java).setAction(TaskMonitorService.STOP),PendingIntent.FLAG_IMMUTABLE)
        return builder(SESSION).setContentTitle("Sentinel task monitor").setContentText(message)
            .setOngoing(true).addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()
    }
    fun update(message: String) { manager.notify(ONGOING,ongoing(message)) }
    fun test(): String {
        if(!allowed()) return "Notifications are blocked. Allow notifications in Android settings."
        manager.notify("sentinel-test",TEST,builder(RESULTS).setContentTitle("Sentinel test notification")
            .setContentText("Android accepted this local test. This does not test the PC or network path.").setAutoCancel(true).build())
        return "Test handed to Android. Seeing or hearing it is not confirmed by Sentinel."
    }
    @Synchronized fun seed(keys: List<String>) { save((known()+keys.map(::digest)).distinct().takeLast(256)) }
    private fun known()=receipts.getString("outcomes","")!!.split('\n').filter { it.isNotEmpty() }
    private fun save(values: List<String>) { check(receipts.edit().putString("outcomes",values.joinToString("\n")).commit()) }
    @Synchronized fun handoff(events: List<OutcomeEvent>,resolvedScopes: Set<String> = emptySet()): String? {
        if(!allowed()) return "Outcome alert blocked by Android notification settings."
        val known=known().toMutableList()
        pending.values.filter { it.scope in resolvedScopes }.map { it.key }.forEach { pending.remove(it) }
        events.forEach { event ->
            if(":health:" in event.scope) pending.values.filter { it.scope==event.scope }.map { it.key }.forEach { pending.remove(it) }
            if(digest(event.key) !in known) pending[event.key]=event
        }
        val settings=policy.read()
        val suppressed=pending.values.filter { it.severity<settings.minimumSeverity || policy.muted(it.scope) || (!settings.health && ":health:" in it.scope) }
        suppressed.forEach { known.add(digest(it.key));pending.remove(it.key) }
        if(suppressed.isNotEmpty()) save(known.distinct().takeLast(256))
        while(pending.size>256) pending.remove(pending.keys.first())
        val fresh=pending.values.filter { digest(it.key) !in known }
        if(fresh.isNotEmpty() && !settings.canHandoff(java.time.LocalTime.now().hour,android.os.SystemClock.elapsedRealtime(),lastHandoff))
            return "${fresh.size} alert(s) deferred by quiet hours or cooldown; pending only in this session's memory."
        if(fresh.isEmpty()) return null
        // Stable tag + onlyAlertOnce also limits duplicates if the process dies before receipt commit.
        val tag=digest(fresh.map { it.key }.sorted().joinToString("\n"))
        val text=if(fresh.size==1) fresh.single().text else "${fresh.size} task outcomes: "+fresh.take(3).joinToString("; ") { it.text }
        manager.notify(tag,RESULT,builder(RESULTS).setContentTitle("Sentinel task outcome")
            .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setAutoCancel(true).build())
        fresh.forEach { known.add(digest(it.key)) }
        save(known.distinct().takeLast(256))
        fresh.forEach { pending.remove(it.key) };lastHandoff=android.os.SystemClock.elapsedRealtime()
        return "${fresh.size} outcome notification(s) handed to Android; display/delivery not confirmed."
    }
    companion object {
        const val SESSION="sentinel-task-session"
        const val RESULTS="sentinel-task-results"
        const val ONGOING=4201
        const val RESULT=4202
        const val TEST=4203
        fun digest(value: String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
internal data class OutcomeEvent(val key: String,val text: String,val severity: Int=2,val scope: String=key.substringBeforeLast(':'))
internal fun outcomeEvents(host: String, watches: List<TaskWatch>, jobs: List<VerifiedJob>): List<OutcomeEvent> =
    watches.filter { it.terminal }.map { OutcomeEvent("$host:process:${it.identity}:ended_unknown","${it.name}: ended; outcome unknown") } +
    jobs.filter { it.state!="running" }.map { OutcomeEvent("$host:job:${it.id}:${it.state}","${it.label}: ${it.state}"+(it.exitCode?.let { code -> " (exit $code)" } ?: ""),if(it.state=="succeeded") 1 else if(it.state in setOf("failed","timed_out")) 3 else 2) }
