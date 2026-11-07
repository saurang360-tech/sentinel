package dev.titus.sentinel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.titus.sentinel.monitor.*
import java.util.Locale

@Composable internal fun ProcessPage(status: ProcessUi,users: List<PcUser>,query: ProcessQuery,select: (ProcessQuery)->Unit,refresh: ()->Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var displayCount by rememberSaveable(query) { mutableStateOf(30) }
    LaunchedEffect(search,query) { displayCount=30 }
    SampleBar(status.phase,status.busy,status.lastContactMillis,refresh)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick={ menu=true },modifier=Modifier.fillMaxWidth()) {
                Text(users.find { it.uid==query.uid }?.name ?: "All users",maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded=menu,onDismissRequest={ menu=false }) {
                DropdownMenuItem(text={ Text("All users") },onClick={ select(query.copy(uid=null));menu=false })
                users.forEach { user -> DropdownMenuItem(text={ Text(user.name) },onClick={ select(query.copy(uid=user.uid));menu=false }) }
            }
        }
        ProcessSort.entries.forEach { sort -> FilterChip(selected=query.sort==sort,onClick={ select(query.copy(sort=sort)) },label={ Text(sort.label+if(query.sort==sort) " ↓" else "") }) }
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text("Include system processes",fontSize=12.sp,color=Color(0xFFA5A5A5),modifier=Modifier.weight(1f))
        Switch(checked=query.includeSystem,onCheckedChange={ select(query.copy(includeSystem=it)) })
    }
    OutlinedTextField(search,{ search=it },label={ Text("Search name or PID") },singleLine=true,modifier=Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    val snapshot=status.snapshot
    if(snapshot==null) {
        Note(if(status.phase==Phase.NoData) "A current Sentinel helper is required on this PC. Check Settings for setup requirements." else statusMessage(status.phase).ifBlank { "Waiting for processes…" })
        return
    }
    if(!snapshot.serverSorted) Note("Sorted within the received sample")
    Note("${snapshot.totalMatching} ${if(query.includeSystem) "processes" else "user processes"}"+(if(snapshot.limited) " · top ${snapshot.rows.size}" else ""))
    Spacer(Modifier.height(10.dp))
    val rows=snapshot.rows.filter { search.isBlank() || it.name.contains(search.trim(),true) || it.pid.toString().contains(search.trim()) }
    if(rows.isEmpty()) Note(if(search.isBlank()) "No processes for this selection." else "No matching processes in this sample.")
    if(rows.isNotEmpty()) SurfaceCard {
        Row(Modifier.fillMaxWidth()) {
            Eyebrow("PROCESS")
            Spacer(Modifier.weight(1f))
            Text("CPU",fontSize=10.sp,color=Color(0xFFA5A5A5),textAlign=TextAlign.End,modifier=Modifier.width(60.dp))
            Text("RAM",fontSize=10.sp,color=Color(0xFFA5A5A5),textAlign=TextAlign.End,modifier=Modifier.width(76.dp))
        }
        rows.take(displayCount).forEachIndexed { index,process ->
            key(process.pid,process.startTicks) {
                var expanded by rememberSaveable { mutableStateOf(false) }
                if(index>0) HorizontalDivider(color=Color(0xFF323232))
                Column(Modifier.fillMaxWidth().clickable(role=Role.Button,onClickLabel="Show process details") { expanded=!expanded }.padding(vertical=12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end=8.dp)) {
                            Text(process.name,fontSize=14.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(process.owner,fontSize=11.sp,color=Color(0xFFA5A5A5),maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Text(process.cpuPercent?.let { String.format(Locale.US,"%.1f%%",it) } ?: "—",fontSize=12.sp,textAlign=TextAlign.End,modifier=Modifier.width(60.dp))
                        Text(process.rssBytes?.let { String.format(Locale.US,"%.0f MiB",it/1048576.0) } ?: "—",fontSize=12.sp,textAlign=TextAlign.End,modifier=Modifier.width(76.dp))
                    }
                    if(expanded) {
                        Spacer(Modifier.height(8.dp))
                        Note("PID ${process.pid} · ${process.stateLabel}\nRuntime ${readableUptime(process.runtimeSeconds)}")
                    }
                }
            }
        }
    }
    if(rows.size>displayCount) TextButton(onClick={ displayCount+=30 },modifier=Modifier.fillMaxWidth()) { Text("Show more (${rows.size-displayCount})") }
    Spacer(Modifier.height(12.dp))
    Disclosure("About this list") {
        Note("User processes excludes service accounts and kernel tasks. Enable system processes for the full list. Accounts update on refresh.")
        Spacer(Modifier.height(8.dp))
        Note(if(snapshot.serverSorted) "Sorted on the PC before the 200-row limit. Accounts are refreshed from local login users."
            else "Compatibility mode: this helper returns up to 200 CPU-ranked processes. Sorting, search and user filters apply to that sample; accounts are inferred from visible processes. The current helper enables complete account discovery and sorting before the limit.")
        Spacer(Modifier.height(8.dp))
        Note("CPU is a share of total machine capacity; RAM is resident memory and includes shared pages. Tap a row for PID, state and runtime.")
        if(snapshot.scanLimited || snapshot.denied>0) Note("Some processes could not be read. The list may be partial.")
    }
}
