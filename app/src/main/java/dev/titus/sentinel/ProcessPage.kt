package dev.titus.sentinel

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.titus.sentinel.monitor.*
import java.util.Locale

@Composable internal fun ProcessPage(status: ProcessUi,users: List<PcUser>,query: ProcessQuery,configure: (() -> Unit)?=null,select: (ProcessQuery)->Unit,refresh: ()->Unit, watch: ((ProcessRow)->Unit)?=null) {
    var search by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val list=rememberLazyListState()
    var availableWidth by remember { mutableIntStateOf(400) }
    val density=LocalDensity.current
    val largeText=density.fontScale>1.3f || availableWidth/density.density<300f
    LaunchedEffect(search,query) { list.scrollToItem(0) }
    Column(Modifier.fillMaxSize().onSizeChanged { availableWidth=it.width }) {
        SampleBar(status.phase,status.busy,status.lastContactMillis,refresh,configure)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(shape=RoundedCornerShape(4.dp),onClick={ menu=true }) { Text((users.find { it.uid==query.uid }?.name ?: "All users")+" ▾") }
                DropdownMenu(expanded=menu,onDismissRequest={ menu=false }) {
                    DropdownMenuItem(text={ Text("All users") },onClick={ select(query.copy(uid=null));menu=false })
                    users.forEach { user -> DropdownMenuItem(text={ Text(user.name) },onClick={ select(query.copy(uid=user.uid));menu=false }) }
                }
            }
            ProcessSort.entries.forEach { sort -> FilterChip(selected=query.sort==sort,onClick={ select(query.copy(sort=sort)) },label={ Text(sort.label+if(query.sort==sort) " ↓" else "") },modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription="Sort by ${sort.label}, highest first" }) }
        }
        OutlinedTextField(search,{ search=it },label={ Text("Search name or PID") },singleLine=true,modifier=Modifier.fillMaxWidth(),
            trailingIcon={ if(search.isNotEmpty()) TextButton(onClick={ search="" }) { Text("Clear") } })
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Include system processes",fontSize=18.sp,color=Muted,modifier=Modifier.weight(1f))
            Switch(checked=query.includeSystem,onCheckedChange={ select(query.copy(includeSystem=it)) },modifier=Modifier.semantics { contentDescription="Include system processes" })
        }
        val snapshot=status.snapshot
        val rows=snapshot?.rows?.filter { search.isBlank() || it.name.contains(search.trim(),true) || it.pid.toString().contains(search.trim()) }.orEmpty()
        if(snapshot!=null) Text("${rows.size} shown · ${snapshot.totalMatching} matching"+(if(snapshot.limited) " · top ${snapshot.rows.size} received" else ""),fontSize=16.sp,color=Muted)
        Spacer(Modifier.height(8.dp))
        // Controls and table headings remain in place while only the results scroll.
        Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal=12.dp,vertical=12.dp)) {
            Text("PROCESS",fontSize=14.sp,fontWeight=FontWeight.Bold,color=White,modifier=Modifier.weight(1f))
            if(!largeText) Text("CPU"+if(query.sort==ProcessSort.CPU) " ↓" else "",fontSize=14.sp,color=if(query.sort==ProcessSort.CPU) Accent else Muted,textAlign=TextAlign.End,modifier=Modifier.width(74.dp))
            if(!largeText) Text("RAM"+if(query.sort==ProcessSort.RAM) " ↓" else "",fontSize=14.sp,color=if(query.sort==ProcessSort.RAM) Accent else Muted,textAlign=TextAlign.End,modifier=Modifier.width(92.dp))
        }
        LazyColumn(state=list,modifier=Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(bottom=20.dp)) {
            if(snapshot==null) item { Spacer(Modifier.height(16.dp));Note(if(status.busy) "Loading processes…" else "No process readings yet. Check your connection and helper setup.") }
            else if(rows.isEmpty()) item { Spacer(Modifier.height(16.dp));Note(if(search.isBlank()) "No processes for this selection." else "No matching processes in this sample. Try another name or PID.") }
            items(rows,key={ "${it.pid}:${it.startTicks}" }) { process ->
                var expanded by rememberSaveable { mutableStateOf(false) }
                val cpu=process.cpuPercent?.let { String.format(Locale.US,"%.1f%%",it) } ?: "—"
                val ram=process.rssBytes?.let { if(it>=1073741824L) String.format(Locale.US,"%.1f GiB",it/1073741824.0) else String.format(Locale.US,"%.0f MiB",it/1048576.0) } ?: "—"
                Column(Modifier.fillMaxWidth().clickable(role=Role.Button,onClickLabel=if(expanded) "Hide process details" else "Show process details") { expanded=!expanded }
                    .semantics { stateDescription=if(expanded) "Expanded" else "Collapsed" }.padding(horizontal=12.dp,vertical=14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end=8.dp)) {
                            Text(process.name,fontSize=18.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(process.owner+if(expanded) " ▴" else " ▾",fontSize=16.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        if(!largeText) {
                            UsageCell(cpu,process.cpuPercent?.div(100.0)?.toFloat(),74)
                            UsageCell(ram,process.rssBytes?.toFloat()?.div((snapshot?.rows?.maxOfOrNull { it.rssBytes ?: 0L } ?: 1L).coerceAtLeast(1)),92)
                        }
                    }
                    if(largeText) { Spacer(Modifier.height(8.dp));Text("CPU $cpu · RAM $ram",fontSize=18.sp,fontWeight=FontWeight.Bold,lineHeight=26.sp) }
                    if(expanded && watch!=null && snapshot?.bootId!=null) TextButton(enabled=status.phase==Phase.Online,onClick={ watch(process) }) { Text("Watch task (test)") }
                    if(expanded) { Spacer(Modifier.height(10.dp));Note("${process.name}\nPID ${process.pid} · ${process.stateLabel}\nRuntime ${readableUptime(process.runtimeSeconds)}") }
                }
                HorizontalDivider(color=Line.copy(alpha=.5f))
            }
            if(snapshot!=null) item {
                Spacer(Modifier.height(16.dp))
                Disclosure("About this list") {
                    Note("CPU bars show a share of total machine capacity. RAM bars compare against the largest process in the received sample. RAM includes shared pages; search is sample-limited.")
                    Spacer(Modifier.height(8.dp))
                    Note(if(snapshot.serverSorted) "Sorted on the PC before the 200-row limit. User processes excludes service accounts and kernel tasks."
                        else "Compatibility mode: sorting and filters apply to the received CPU-ranked sample. Upgrade the PC helper for complete user discovery and sorting before the limit.")
                    if(snapshot.scanLimited || snapshot.denied>0) Note("Some processes could not be read. This list may be partial.")
                }
            }
        }
    }
}

@Composable private fun UsageCell(text: String,fraction: Float?,width: Int) {
    val color=Accent.copy(alpha=.22f)
    Box(Modifier.width(width.dp).heightIn(min=36.dp),contentAlignment=Alignment.CenterEnd) {
        if(fraction!=null) Canvas(Modifier.fillMaxSize()) { drawRect(color,size=Size(size.width*fraction.coerceIn(0f,1f),size.height)) }
        Text(text,fontSize=16.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.End)
    }
}
