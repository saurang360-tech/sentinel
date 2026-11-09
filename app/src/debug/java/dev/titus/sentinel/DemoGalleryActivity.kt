package dev.titus.sentinel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.titus.sentinel.monitor.*

/** Synthetic data only. Never creates keys, monitoring ViewModels or SSH transports. */
class DemoGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,intent.getFloatExtra("fontScale",1f))) {
            when(intent.getStringExtra("surface")) {
                "theme" -> {
                    val store=remember { WorkspaceStore(this,"sentinel-theme-review") }
                    var prefs by remember { mutableStateOf(store.preferences()) }
                    SentinelTheme(prefs) {
                        Surface(Modifier.fillMaxSize(),color=Ink) {
                            Column(Modifier.safeDrawingPadding().padding(4.dp)) {
                                Text("Theme settings",style=MaterialTheme.typography.headlineLarge)
                                Box(Modifier.weight(1f)) { PreferencesWidget(prefs,save={ store.save(it);prefs=it }) }
                                Text("No PC connected · no telemetry samples")
                            }
                        }
                    }
                }
                "settings" -> SentinelTheme {
                    var config by remember { mutableStateOf(ConnectionSettings()) }
                    var status by remember { mutableStateOf(MonitorUi()) }
                    Surface(Modifier.fillMaxSize(),color=Ink) {
                        Column(Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState())) {
                            Text("Connections",style=MaterialTheme.typography.headlineLarge)
                            Note("DEMO · Synthetic setup, no PC connection")
                            Spacer(Modifier.height(12.dp))
                            ConnectionSettingsPage(config,"DEMO-PUBLIC-KEY-NOT-FOR-PAIRING",status,emptyList(),"DEMO-TERMINAL-KEY",save={ config=it;status=MonitorUi();null },check={ status=DemoData.status },erase={},profiles=emptyList(),selectProfile={ null },powerUi=PowerUi(),checkPower={})
                        }
                    }
                }
                "processes" -> SentinelTheme {
                    var query by remember { mutableStateOf(ProcessQuery()) }
                    val base=DemoData.processes(query)
                    val rows=(1..80).map { index -> base.snapshot!!.rows.first().copy(pid=2000+index,name="example-process-$index",startTicks=index.toLong()) }
                    val sample=base.copy(snapshot=base.snapshot!!.copy(rows=rows,totalMatching=rows.size))
                    Surface(Modifier.fillMaxSize(),color=Ink) {
                        Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                            Text("Processes",style=MaterialTheme.typography.headlineLarge)
                            ProcessPage(sample,DemoData.users,query,select={ query=it },refresh={})
                        }
                    }
                }
                "terminal" -> SentinelTheme {
                    Surface(Modifier.fillMaxSize(),color=Ink) {
                        Column(Modifier.safeDrawingPadding().imePadding().padding(20.dp)) {
                            Text("Terminal",style=MaterialTheme.typography.headlineLarge)
                            Note("DEMO · No shell or commands are executed")
                            TerminalPage(TerminalUi(connected=true,output="sentinel@workstation:~$ uptime\n 12:04:32 up 8:24, 1 user, load average: 0.74, 0.88, 0.91\nsentinel@workstation:~$ ",message="Example SSH session"),connect={},disconnect={},send={},clear={})
                        }
                    }
                }
                else -> Sentinel()
            }
            }
        }
    }
}
