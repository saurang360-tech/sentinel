package dev.titus.sentinel

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dev.titus.sentinel.monitor.*

@Composable internal fun AlertTestControls() {
    val context=LocalContext.current
    val prefs=remember(context) { AlertPreferences(context) }
    var policy by remember { mutableStateOf(prefs.read()) }
    var error by remember { mutableStateOf("") }
    fun save(next: AlertPolicy) { runCatching { prefs.save(next) }.onSuccess { policy=next;error="" }.onFailure { error="Alert settings could not be saved." } }
    SurfaceCard {
        Eyebrow("ALERT POLICY")
        Note("Applies to background sessions on this phone. Quiet hours and cooldown defer alerts in session memory; stopping the session discards that queue. Muted/below-severity outcomes are suppressed, not replayed later. The explicit Test notification bypasses this policy.")
        TextButton(onClick={ save(policy.copy(quiet=!policy.quiet)) }) { Text("Quiet hours: "+if(policy.quiet) "ON" else "OFF") }
        var timeField by remember { mutableStateOf<String?>(null) }
        Row {
            TextButton(onClick={ timeField="start" }) { Text("From %02d:00".format(policy.startHour)) }
            TextButton(onClick={ timeField="end" }) { Text("Until %02d:00".format(policy.endHour)) }
        }
        timeField?.let { field ->
            var hour by remember(field) { mutableIntStateOf(if(field=="start") policy.startHour else policy.endHour) }
            AlertDialog(onDismissRequest={ timeField=null },title={ Text("Quiet hours · $field") },text={ Column {
                Text("%02d:00".format(hour),style=MaterialTheme.typography.titleLarge)
                Slider(value=hour.toFloat(),onValueChange={ hour=it.toInt().coerceIn(0,23) },valueRange=0f..23f,steps=22)
            } },confirmButton={ TextButton(onClick={ save(if(field=="start") policy.copy(startHour=hour) else policy.copy(endHour=hour));timeField=null }) { Text("Save time") } },dismissButton={ TextButton(onClick={ timeField=null }) { Text("Cancel") } })
        }
        Note("Phone local time. Equal endpoints mean quiet all day.")
        TextButton(onClick={ save(policy.copy(minimumSeverity=policy.minimumSeverity%3+1)) }) {
            Text("Minimum severity: "+when(policy.minimumSeverity) { 1 -> "all outcomes";2 -> "warnings and errors";else -> "errors only" })
        }
        val cooldowns=listOf(0,60,300,900)
        TextButton(onClick={ save(policy.copy(cooldownSeconds=cooldowns[(cooldowns.indexOf(policy.cooldownSeconds)+1)%cooldowns.size])) }) { Text("Alert cooldown: ${policy.cooldownSeconds/60} min · change") }
        TextButton(onClick={ save(policy.copy(health=!policy.health)) }) { Text("Sustained health alerts: "+if(policy.health) "ON" else "OFF") }
        Note("Opt-in RAM, memory pressure, disk capacity and CPU temperature warnings: 60 seconds to trigger/recover, with recovery hysteresis. Missing sensors and connection gaps cannot confirm recovery. Requires a running background session and compatible helper; health collection adds polling overhead.")
        if(error.isNotEmpty()) Note(error)
    }
}

@Composable internal fun TaskMuteButton(scope: String) {
    val context=LocalContext.current
    val prefs=remember(context) { AlertPreferences(context) }
    var muted by remember(scope) { mutableStateOf(prefs.muted(scope)) }
    var error by remember(scope) { mutableStateOf("") }
    TextButton(onClick={ runCatching { prefs.setMuted(scope,!muted) }.onSuccess { muted=!muted;error="" }.onFailure { error="Mute rule could not be saved (limit 256)." } }) {
        Text(if(muted) "Unmute task alerts" else "Mute task alerts")
    }
    if(error.isNotEmpty()) Note(error)
}
