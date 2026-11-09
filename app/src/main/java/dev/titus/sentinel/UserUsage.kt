package dev.titus.sentinel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.titus.sentinel.monitor.*

internal data class UserUsage(val uid: Int,val name: String,val count: Int,val cpu: Double?,val rss: Long?)
internal fun userUsage(rows: List<ProcessRow>): List<UserUsage> = rows.groupBy { it.uid }.map { (uid,group) ->
    UserUsage(uid,group.first().owner,group.size,
        if(group.all { it.cpuPercent!=null }) group.sumOf { it.cpuPercent!! } else null,
        if(group.all { it.rssBytes!=null }) group.sumOf { it.rssBytes!! } else null)
}.sortedWith(compareByDescending<UserUsage> { it.cpu ?: -1.0 }.thenBy { it.uid })

@Composable internal fun UsersWidget(status: ProcessUi) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val snapshot=status.snapshot
        if(snapshot==null) { Text("No data");return@Column }
        if(status.phase!=Phase.Online) Text("Last sample",color=Amber)
        Text("Sample totals",color=Muted,fontSize=14.sp)
        userUsage(snapshot.rows).forEach { user ->
            Text(user.name,fontWeight=FontWeight.Bold,fontSize=20.sp)
            Text("CPU ${displayNumber(user.cpu,"%")} · RSS ${bytesValue(user.rss?.toDouble())}")
            Text("${user.count} processes",color=Muted,fontSize=14.sp)
        }
        if(snapshot.rows.isEmpty()) Text("No processes")
        if(snapshot.limited || snapshot.scanLimited || snapshot.denied>0 || snapshot.vanished>0) Text("Partial sample",color=Amber,fontSize=14.sp)
        Text("RSS includes shared memory.",color=Muted,fontSize=14.sp)
    }
}
