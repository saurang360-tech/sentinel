package dev.titus.sentinel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.titus.sentinel.monitor.*
import org.json.JSONObject
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date

internal fun JSONObject.value(key: String): Double?=if(!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
private fun JSONArray?.objects()=if(this==null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
@Composable internal fun TelemetryWidget(kind: WidgetKind,health: HealthUi,history: List<TelemetryPoint>,details: Map<String,String>,inspect: (String,Boolean)->Unit) {
    val j=health.snapshot?.data
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if(j==null) { Text("No data",style=MaterialTheme.typography.titleLarge);return@Column }
        if(health.phase!=Phase.Online) { Text("Last sample · ${statusLabel(health.phase)}",color=Amber,fontWeight=FontWeight.Bold);Spacer(Modifier.height(if(Retro) 4.dp else 12.dp)) }
        val cpu=j.getJSONObject("cpu");val memory=j.getJSONObject("memory")
        when(kind) {
            WidgetKind.Cpu -> {
                Text(displayNumber(cpu.value("utilisationPercent"),"%"),fontSize=if(Retro) 24.sp else 40.sp,fontWeight=FontWeight.Bold,fontFamily=FontFamily.Monospace)
                Trend(history,"cpu",percent=true)
                Text(displayNumber(cpu.value("temperatureC"),"°C"),color=Muted)
            }
            WidgetKind.CpuDetails -> {
                Spacer(Modifier.height(if(Retro) 4.dp else 12.dp))
                Text("Temperature ${displayNumber(cpu.value("temperatureC"),"°C")}",fontWeight=FontWeight.Bold)
                val critical=cpu.value("temperatureCriticalC")
                Text("Hardware limit: ${displayNumber(critical,"°C")}",color=Muted,fontSize=14.sp)
                Trend(history,"temperature",threshold=critical)
                Text("Per-core usage",fontWeight=FontWeight.Bold)
                val cores=cpu.optJSONArray("cores").objects()
                if(cores.isEmpty()) Note("Cores unavailable")
                cores.forEach { c ->
                    val value=c.value("utilisationPercent")
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(c.optString("name"));Text(displayNumber(value,"%")+if(Retro) " · "+displayNumber(c.value("frequencyMHz")," MHz") else "") }
                    if(value!=null && Retro) BlockMeter((value/100).toFloat())
                    else if(value!=null) LinearProgressIndicator(progress={ (value/100).toFloat().coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth().padding(vertical=5.dp),color=Accent,trackColor=Line)
                }
                Note(cpu.optString("name"))
            }
            WidgetKind.Memory -> {
                Capacity("",memory.value("usedBytes"),memory.value("totalBytes"))
                Trend(history,"ram",percent=true)
            }
            WidgetKind.MemoryDetails -> {
                Capacity("Swap",memory.value("swapUsedBytes"),memory.value("swapTotalBytes"))
                Text("Available ${bytesValue(memory.value("availableBytes"))}")
                run {
                    PairLine("Free",bytesValue(memory.value("freeBytes")))
                    PairLine("Buffers",bytesValue(memory.value("buffersBytes")))
                    PairLine("Cached",bytesValue(memory.value("cachedBytes")))
                    Note("Linux fields overlap; available includes reclaimable memory.")
                }
                Note("Pressure stalls: ${displayNumber(memory.value("pressureSomeAvg10"),"%")}")
            }
            WidgetKind.Storage -> {
                j.optJSONArray("volumes").objects().forEach { v -> Capacity(v.optString("label")+" · "+v.optString("path"),v.value("usedBytes"),v.value("totalBytes")) }

            }
            WidgetKind.Network,WidgetKind.Disk -> {
                val network=kind==WidgetKind.Network
                val key=if(network) "networks" else "disks"
                val latest=history.lastOrNull()
                Text((if(network) "↓ " else "R ")+bytesValue(latest?.values?.get("$key:a"))+"/s",fontSize=28.sp,fontWeight=FontWeight.Bold)
                Text((if(network) "↑ " else "W ")+bytesValue(latest?.values?.get("$key:b"))+"/s",fontSize=22.sp,color=Muted)
                Trend(history,"$key:a","$key:b")
            }
            WidgetKind.NetworkDetails,WidgetKind.DiskDetails -> {
                val network=kind==WidgetKind.NetworkDetails
                val key=if(network) "networks" else "disks"
                j.optJSONArray(key).objects().filter { !network || it.optBoolean("physical") }.forEach { row ->
                    Text(row.optString("name"),fontWeight=FontWeight.Bold)
                    PairLine(if(network) "Download" else "Read",bytesValue(row.value(if(network) "rxBytesPerSecond" else "readBytesPerSecond"))+"/s")
                    PairLine(if(network) "Upload" else "Write",bytesValue(row.value(if(network) "txBytesPerSecond" else "writeBytesPerSecond"))+"/s")
                }
            }
            WidgetKind.Sensors -> {
                val temps=j.optJSONArray("temperatures").objects()
                val ambient=temps.filter { it.optString("label").contains("ambient",true) }
                Text("Ambient",fontWeight=FontWeight.Bold)
                if(ambient.isEmpty()) Note("Ambient sensor not exposed by this hardware.")
                for((key,unit) in listOf("temperatures" to "°C","fans" to " RPM","componentPower" to " W")) {
                    Text(when(key) { "temperatures" -> "Temperatures";"fans" -> "Fans";else -> "Component power" },fontWeight=FontWeight.Bold)
                    val rows=j.optJSONArray(key).objects()
                    if(rows.isEmpty()) Note("Unavailable")
                    rows.forEach { PairLine(it.optString("label"),displayNumber(it.value("value"),unit)) }
                }
                j.optJSONArray("gpus").objects().forEach { gpu -> Text(gpu.optString("name"),fontWeight=FontWeight.Bold);PairLine("GPU",displayNumber(gpu.value("utilisationPercent"),"%"));PairLine("VRAM",bytesValue(gpu.value("vramUsedBytes"))) }
            }
            WidgetKind.Services -> {
                for((key,user) in listOf("failedSystemServices" to false,"failedUserServices" to true)) {
                    val group=j.optJSONObject(key)
                    Text("${if(user) "User" else "System"}: ${group?.optInt("count") ?: "Unavailable"}",fontSize=22.sp,fontWeight=FontWeight.Bold)
                    val names=group?.optJSONArray("names")
                    if(names!=null) for(i in 0 until names.length()) {
                        val unit=names.getString(i);val scope=(if(user) "user" else "system")+" "+unit
                        var expanded by remember(unit) { mutableStateOf(false) }
                        TextButton(onClick={ expanded=!expanded;if(expanded) inspect(unit,user) }) { Text((if(expanded) "− " else "+ ")+unit) }
                        if(expanded) { Text(details[scope] ?: "Not fetched",fontFamily=FontFamily.Monospace,fontSize=14.sp,lineHeight=20.sp);Note("Recent journal entries; stack traces appear only if the service logged them.") }
                    }
                }
                Note("Details are fetched on demand using this account’s existing journal permissions.")
            }
            WidgetKind.System -> { PairLine("OS",j.optString("osName"));PairLine("Kernel",j.optString("kernel"));PairLine("Logical CPUs",cpu.optString("logicalCores"));PairLine("Uptime",readableUptime(j.optDouble("uptimeSeconds")));PairLine("Load",j.optJSONArray("load")?.toString() ?: "Unavailable") }
            else -> Unit
        }
    }
}

@Composable internal fun WatchedWidget(watches: List<TaskWatch>,host: String,clear: ()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if(watches.isEmpty()) Note("Expand a process and choose Watch task. Identity includes PID, start time and boot ID.")
        watches.forEach { w ->
            Text(w.name,fontSize=20.sp,fontWeight=FontWeight.Bold)
            Text(w.label,color=if(w.terminal) Amber else Accent)
            w.events.forEach { event -> val parts=event.split(": ",limit=2)
                Row(Modifier.padding(vertical=5.dp)) { Text("●",color=Accent);Spacer(Modifier.width(10.dp));Text((parts.first().toLongOrNull()?.let { DateFormat.getTimeInstance().format(Date(it)) } ?: "—")+" · "+parts.getOrElse(1) { "unknown" },fontSize=14.sp) }
            }
            TaskMuteButton("$host:process:${w.identity}")
            HorizontalDivider(color=Line)
        }
        Note("Observed transitions only. Missing observations never prove success.")
        if(watches.isNotEmpty()) TextButton(onClick=clear) { Text("Delete watches and history") }
    }
}

@Composable internal fun JobsWidget(jobs: List<VerifiedJob>,expectations: List<JobExpectation>,host: String,message: String) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(jobs) { while(true) { now=System.currentTimeMillis();kotlinx.coroutines.delay(1000) } }
    val durations=jobs.associate { it.id to ((it.endedMillis ?: if(it.state=="running") now else it.startedMillis)-it.startedMillis).coerceAtLeast(0) }
    val longest=(durations.values.maxOrNull() ?: 1).coerceAtLeast(1)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if(jobs.isEmpty()) Note("No verified runner jobs. Launch work using the optional Sentinel CLI runner on the PC.")
        jobs.forEach { job ->
            val symbol=when(job.state) { "succeeded" -> "[✓]";"failed","timed_out" -> "[✗]";"running" -> "[▶]";else -> "[?]" }
            Text("$symbol ${job.label}",fontWeight=FontWeight.Bold,fontSize=20.sp)
            Text(job.state.replace('_',' ')+(job.exitCode?.let { " · exit $it" } ?: " · no exit result"))
            val duration=durations.getValue(job.id)
            Text(if(job.state!="running" && job.endedMillis==null) "End time unknown" else readableUptime(duration/1000.0),fontFamily=FontFamily.Monospace)
            if((job.state=="running" || job.endedMillis!=null) && Retro) BlockMeter(duration.toFloat()/longest)
            else if(job.state=="running" || job.endedMillis!=null) LinearProgressIndicator(progress={ duration.toFloat()/longest },modifier=Modifier.fillMaxWidth().padding(vertical=8.dp),color=Accent,trackColor=Line)
            TaskMuteButton("$host:job:${job.id}")
        }
        Text("Bars compare elapsed duration, not completion percentage.",fontSize=14.sp,color=Muted)
        expectations.forEach { rule -> Text(rule.label+" · "+rule.state);Note("Expected success by ${DateFormat.getDateTimeInstance().format(Date(rule.deadlineMillis))}") }
        if(message.isNotEmpty()) Note(message)
    }
}
