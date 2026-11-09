package dev.titus.sentinel

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.titus.sentinel.monitor.*

@Composable internal fun TaskMonitorControls(config: ConnectionSettings,watches: List<TaskWatch>) {
    val context=LocalContext.current
    val state by TaskMonitoring.state.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf("") }
    fun start() { message=TaskMonitoring.start(context,config,watches) ?: "Starting background session…" }
    fun test() { message=runCatching { TaskNotifications(context).test() }.getOrDefault("Android refused the test notification.") }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) { if(pendingAction=="start") start() else test() }
        else message="Notifications were not allowed. Monitoring has not started."
        pendingAction=""
    }
    fun request(action: String) {
        pendingAction=action
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else { if(action=="start") start() else test();pendingAction="" }
    }
    SurfaceCard {
        Eyebrow("BACKGROUND MONITOR")
        Note("Opt in to a visible monitoring session for this PC. Connection details remain in memory while locked. Polling can be delayed by Android or network loss. Phone restart, app/process stop, or changing connections ends the session; restart it here. No cloud relay or PC power changes.")
        Note(state.message)
        Note("Last loop heartbeat: "+(state.heartbeatMillis?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: "none"))
        Note("Last confirmed PC observation: "+(state.observedMillis?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: "none"))
        Note(state.handoff)
        Note(state.healthMessage)
        if(message.isNotEmpty()) Note(message)
        if(state.running) TextButton(onClick={ TaskMonitoring.stop(context) }) { Text("Stop background monitor") }
        else TextButton(enabled=config.validationError()==null,onClick={ request("start") }) { Text("Start background monitor") }
        TextButton(onClick={ request("test") }) { Text("Test notification") }
    }
}
