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
@Composable internal fun SampleBar(phase: Phase,busy: Boolean,time: Long?,refresh: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(if(phase==Phase.Online) Color(0xFFF4F4F1) else Color(0xFFE4C68A),CircleShape))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(statusLabel(phase),fontSize=12.sp,fontWeight=FontWeight.Medium)
                if(time!=null) Note("Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))}")
            }
            TextButton(onClick=refresh,enabled=!busy) { Text(if(busy) "Updating…" else "Refresh",fontSize=12.sp) }
        }
        if(phase !in setOf(Phase.Online,Phase.Unknown,Phase.Checking)) { Note(statusMessage(phase)); Spacer(Modifier.height(10.dp)) }
        Spacer(Modifier.height(8.dp))
    }
}
@Composable internal fun Disclosure(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SurfaceCard {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(role=Role.Button,onClickLabel=if(expanded) "Collapse" else "Expand") { expanded=!expanded }
            .semantics { stateDescription=if(expanded) "Expanded" else "Collapsed" },verticalAlignment=Alignment.CenterVertically) {
            Text(title,fontSize=14.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
            Text(if(expanded) "−" else "+",fontSize=20.sp,color=Color(0xFFA5A5A5))
        }
        if(expanded) { Spacer(Modifier.height(8.dp)); content() }
    }
}
