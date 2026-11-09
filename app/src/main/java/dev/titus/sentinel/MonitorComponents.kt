package dev.titus.sentinel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.titus.sentinel.monitor.Phase
import java.text.DateFormat
import java.util.Date

internal fun statusLabel(phase: Phase): String = when(phase) {
    Phase.Online -> "Connected"
    Phase.Checking -> "Connecting"
    Phase.Stale -> "Out of date"
    Phase.Unreachable -> "Unreachable"
    Phase.Pairing -> "Pairing required"
    Phase.SecurityError -> "Connection blocked"
    Phase.NoData -> "Readings unavailable"
    Phase.Unknown -> "Not connected"
}
internal fun statusMessage(phase: Phase): String = when(phase) {
    Phase.Unreachable -> "Check your network. The PC may be asleep, off or disconnected."
    Phase.SecurityError -> "The host key has changed. Verify the fingerprint in Settings."
    Phase.Pairing -> "Authorise this phone’s public key on the PC."
    Phase.NoData -> "Monitoring interrupted. Reconnecting automatically…"
    Phase.Stale -> "Showing the last reading. Refresh to reconnect."
    Phase.Checking -> "Connecting to your PC…"
    Phase.Unknown -> "Connect to your PC’s network, then refresh."
    Phase.Online -> ""
}
@Composable internal fun SampleBar(phase: Phase,busy: Boolean,time: Long?,refresh: () -> Unit,configure: (() -> Unit)?=null,refreshEnabled: Boolean=true) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(time) { while(true) { now=System.currentTimeMillis();kotlinx.coroutines.delay(1000) } }
    val age=time?.let { ((now-it).coerceAtLeast(0)/1000) }
    Column(Modifier.fillMaxWidth().padding(bottom=12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(when(phase) { Phase.Online -> Accent;Phase.SecurityError -> Danger;else -> Amber },CircleShape))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(statusLabel(phase),fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                if(age!=null) Text(when { age<5 -> "Updated just now";age<60 -> "Updated ${age}s ago";age<3600 -> "Updated ${age/60}m ago";else -> "Updated ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(time))}" },fontSize=16.sp,color=Muted)
            }
            TextButton(onClick=refresh,enabled=!busy && refreshEnabled) { Text(if(busy) "Updating…" else if(phase==Phase.Online) "Refresh" else "Retry") }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=6.dp),color=Accent,trackColor=Line)
        if(phase !in setOf(Phase.Online,Phase.Checking)) {
            Note(statusMessage(phase))
            if(configure!=null && phase in setOf(Phase.Unknown,Phase.Pairing,Phase.SecurityError,Phase.NoData)) {
                TextButton(onClick=configure) { Text(when(phase) { Phase.SecurityError -> "Review host fingerprint";Phase.Pairing -> "Open pairing setup";else -> "Open connection settings" }) }
            }
        }
    }
}
@Composable internal fun Disclosure(title: String, initiallyExpanded: Boolean=false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    SurfaceCard {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(role=Role.Button,onClickLabel=if(expanded) "Collapse" else "Expand") { expanded=!expanded }
            .semantics { stateDescription=if(expanded) "Expanded" else "Collapsed" },verticalAlignment=Alignment.CenterVertically) {
            Text(title,fontSize=16.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
            Text(if(expanded) "−" else "+",fontSize=20.sp,color=Muted)
        }
        if(expanded) { Spacer(Modifier.height(8.dp)); content() }
    }
}
