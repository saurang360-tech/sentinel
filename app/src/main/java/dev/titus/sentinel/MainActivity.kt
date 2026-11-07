package dev.titus.sentinel

import android.os.Bundle
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.titus.sentinel.monitor.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF080808)
private val Panel = Color(0xFF191919)
private val Muted = Color(0xFFA5A5A5)
private val Line = Color(0xFF323232)
private val White = Color(0xFFF4F4F1)
private val Amber = Color(0xFFE4C68A)
private enum class Page(val title: String) {
    Power("Overview"), Health("Health"), Details("Details"), Activity("Processes"), Terminal("Terminal")
}
class MainActivity : ComponentActivity() {
    internal lateinit var security: DeviceSecurity
    internal lateinit var power: ShutdownController
    internal val settingsVisible=kotlinx.coroutines.flow.MutableStateFlow(false)
    internal val terminalVisible=kotlinx.coroutines.flow.MutableStateFlow(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        security=DeviceSecurity(this)
        power=ShutdownController(this)
        enableEdgeToEdge()
        setContent { AccessGate(content={ Sentinel() },demoContent={ exit -> Sentinel(demo=true,exitDemo=exit) }) }
    }
    override fun onStop() { super.onStop();security.lock();settingsVisible.value=false;terminalVisible.value=false;power.cancel() }
    override fun onUserLeaveHint() { super.onUserLeaveHint();settingsVisible.value=false;terminalVisible.value=false;if(!security.authenticating) security.cancel();power.cancel() }
    override fun onDestroy() { security.cancel();power.close();super.onDestroy() }
}

@Composable
private fun Sentinel(initialPage: Page = Page.Power, demo: Boolean = false, exitDemo: () -> Unit = {}) {
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var selectedUid by rememberSaveable { mutableStateOf<Int?>(null) }
    var processSort by rememberSaveable { mutableStateOf(ProcessSort.CPU) }
    var includeSystem by rememberSaveable { mutableStateOf(false) }
    val processQuery=ProcessQuery(selectedUid,processSort,includeSystem)
    val activity=androidx.activity.compose.LocalActivity.current as? MainActivity
    var demoSettings by remember { mutableStateOf(false) }
    val settingsOpen=if(demo || activity==null) demoSettings else activity.settingsVisible.collectAsStateWithLifecycle().value
    val terminalRevealed=activity?.terminalVisible?.collectAsStateWithLifecycle()?.value ?: false
    fun showSettings(value: Boolean) { if(demo || activity==null) demoSettings=value else activity.settingsVisible.value=value }
    val powerUi=activity?.power?.ui?.collectAsStateWithLifecycle()?.value ?: PowerUi()
    var confirmPower by remember { mutableStateOf(false) }
    var checkPowerOnly by remember { mutableStateOf(false) }
    var pcPassword by remember { mutableStateOf("") }
    val monitoring: StatusViewModel? = if(LocalInspectionMode.current || demo) null else viewModel()
    val terminal: TerminalViewModel? = if(LocalInspectionMode.current || demo) null else viewModel()
    val terminalUi=terminal?.ui?.collectAsStateWithLifecycle()?.value ?: TerminalUi()
    val terminalPublicKey=terminal?.publicKey?.collectAsStateWithLifecycle()?.value.orEmpty()
    val healthStatus = monitoring?.health?.collectAsStateWithLifecycle()?.value ?: if(demo) DemoData.health else HealthUi()
    val processStatus = monitoring?.processes?.collectAsStateWithLifecycle()?.value ?: if(demo) DemoData.processes(processQuery) else ProcessUi()
    val status = monitoring?.ui?.collectAsStateWithLifecycle()?.value ?: if(demo) DemoData.status else MonitorUi()
    val config = monitoring?.config?.collectAsStateWithLifecycle()?.value ?: ConnectionSettings()
    val users=monitoring?.users?.collectAsStateWithLifecycle()?.value ?: if(demo) DemoData.users else emptyList()
    val profiles=monitoring?.profiles?.collectAsStateWithLifecycle()?.value.orEmpty()
    LaunchedEffect(config) {
        terminal?.disconnect();terminal?.clear()
        activity?.terminalVisible?.value=false
    }
    LaunchedEffect(users,processStatus.snapshot) {
        if(processStatus.snapshot?.serverSorted==true && selectedUid!=null && users.none { it.uid==selectedUid }) selectedUid=null
    }
    val publicKey = monitoring?.publicKey?.collectAsStateWithLifecycle()?.value.orEmpty()
    LifecycleStartEffect(page,settingsOpen,processQuery) {
        monitoring?.visible(!settingsOpen && page!=Page.Terminal,if(page == Page.Activity) processQuery else null,page == Page.Health || page == Page.Details)
        onStopOrDispose { pcPassword="";confirmPower=false;monitoring?.visible(false);terminal?.disconnect() }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = White, background = Ink, surface = Panel, onSurface = White)) {
        Scaffold(containerColor = Ink, bottomBar = {
            Row(Modifier.fillMaxWidth().background(Ink).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Page.entries.forEach { item ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).selectable(selected = page == item && !settingsOpen, role = Role.Tab, onClick = { page = item; showSettings(false);activity?.terminalVisible?.value=false })
                        .background(if (page == item && !settingsOpen) Panel else Ink).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(width = 18.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(if(page == item && !settingsOpen) White else Color.Transparent))
                        Spacer(Modifier.height(7.dp))
                        Text(item.title, color = if (page == item && !settingsOpen) White else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }) { inset ->
            Column(Modifier.padding(inset).imePadding().fillMaxSize().verticalScroll(key(page,settingsOpen) { rememberScrollState() }).padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SentinelMark()
                    Spacer(Modifier.width(9.dp))
                    Text("SENTINEL", fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.5.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        if(settingsOpen) showSettings(false)
                        else if(demo) showSettings(true)
                        else activity?.security?.authenticate("Unlock saved connections") { _,ok -> if(ok) showSettings(true) }
                    }) { Text(if(settingsOpen) "Done" else "Settings", color = Muted, fontSize = 12.sp) }
                }
                Spacer(Modifier.height(22.dp))
                Text(if(settingsOpen) "Settings" else when(page) { Page.Power -> config.deviceName.ifBlank { status.snapshot?.host ?: "Overview" }; Page.Health -> "Health"; Page.Details -> "System details"; Page.Activity -> "Processes"; Page.Terminal -> "Terminal" },
                    fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
                Spacer(Modifier.height(18.dp))
                if(demo) {
                    Text("DEMO · Synthetic examples, no PC connection",color=Amber,fontSize=12.sp)
                    TextButton(onClick=exitDemo) { Text("Exit demo") }
                }
                if(settingsOpen && demo) SurfaceCard {
                    Note("Demo uses synthetic data and makes no SSH connections. Exit demo to set up your own Linux PC.")
                    PrivacyLinks()
                }
                else if(settingsOpen) ConnectionSettingsPage(config,publicKey,status,users,terminalPublicKey,
                    save = { activity?.power?.cancel();terminal?.disconnect();terminal?.clear();monitoring?.save(it) }, check = { monitoring?.checkNow() },
                    erase = { activity?.power?.cancel();monitoring?.visible(false);terminal?.disconnect();terminal?.clear() },
                    profiles=profiles,selectProfile={ activity?.power?.cancel();terminal?.disconnect();terminal?.clear();monitoring?.selectProfile(it) },
                    powerUi=powerUi,checkPower={ pcPassword="";checkPowerOnly=true;confirmPower=true })
                else when (page) {
                    Page.Power -> PowerPage(status,refresh = { monitoring?.checkNow() },powerUi=if(demo) PowerUi() else powerUi,
                        powerEnabled=!demo && config.validationError()==null && !powerUi.busy,shutdown={ pcPassword="";checkPowerOnly=false;confirmPower=true })
                    Page.Health -> HealthPage(healthStatus, false) { monitoring?.checkHealth() }
                    Page.Details -> HealthPage(healthStatus, true) { monitoring?.checkHealth() }
                    Page.Terminal -> if(demo) SurfaceCard { Note("Demo does not open a shell. On a configured PC, Terminal provides an optional basic SSH shell with separate authorisation.") }
                        else if(!terminalRevealed) SurfaceCard {
                            Note("Terminal output can reveal login details. Verify with your phone to open it. This is separate from shutdown, which only asks for the PC sudo password.")
                            Button(onClick={ activity?.security?.authenticate("Unlock terminal details") { _,ok -> if(ok) activity.terminalVisible.value=true } }) { Text("Unlock terminal") }
                        }
                        else TerminalPage(terminalUi,connect={ terminal?.connect(config) },disconnect={ terminal?.disconnect() },send={ terminal?.send(it) },clear={ terminal?.clear() })
                    Page.Activity -> ProcessPage(processStatus,users,processQuery,select = { selectedUid=it.uid; processSort=it.sort; includeSystem=it.includeSystem },refresh = { monitoring?.checkProcesses(processQuery) })
                }
                Spacer(Modifier.height(22.dp))
            }
        }
        if(confirmPower && !demo) AlertDialog(onDismissRequest={ confirmPower=false;pcPassword="" },
            title={ Text(if(checkPowerOnly) "Verify PC authentication" else "Shut down this PC?") },
            text={ Column {
                Text(if(checkPowerOnly) "Verify the PC account’s existing administrator authentication. This check does not shut down the PC."
                    else "This turns off the entire PC, interrupts work and disconnects all users. Enter the PC account’s administrator (sudo) password to authenticate and shut down.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(pcPassword,{ pcPassword=it },label={ Text("PC administrator password") },singleLine=true,
                    visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password,autoCorrectEnabled=false),
                    modifier=Modifier.fillMaxWidth())
                Note("For ${config.username}. Sent only through verified SSH; never saved. Existing sudo permissions are required.")
            } },
            confirmButton={ TextButton(enabled=pcPassword.isNotEmpty(),onClick={
                val entered=pcPassword.toCharArray();pcPassword="";confirmPower=false
                activity?.power?.request(config,entered,checkPowerOnly) ?: entered.fill('\u0000')
            }) { Text(if(checkPowerOnly) "Verify only" else "Authenticate and shut down") } },
            dismissButton={ TextButton(onClick={ confirmPower=false;pcPassword="" }) { Text("Cancel") } })
    }
}

@Composable private fun SentinelMark() {
    Image(painterResource(R.drawable.ic_sentinel_foreground), contentDescription = "Sentinel Roman helmet and shield", modifier = Modifier.size(36.dp))
}
@Composable internal fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(20.dp), content = content)
}
@Composable internal fun Eyebrow(text: String) { Text(text, color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium) }
@Composable internal fun Note(text: String) { Text(text, color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }
@Composable internal fun PairLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(0.9f))
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
    }
}

@Composable private fun PowerPage(status: MonitorUi, refresh: () -> Unit,powerUi: PowerUi=PowerUi(),powerEnabled: Boolean=false,shutdown: ()->Unit={}) {
    val online=status.phase == Phase.Online
    SampleBar(status.phase,status.busy,status.lastContactMillis,refresh)
    SurfaceCard {
        Eyebrow("WORKSTATION")
        Box(Modifier.fillMaxWidth().height(210.dp),contentAlignment=Alignment.Center) {
            Box(Modifier.size(190.dp).background(Brush.radialGradient(listOf(Color(0xFF333333),Color.Transparent))),contentAlignment=Alignment.Center) {
                OutlinedButton(
                    onClick=shutdown,enabled=powerEnabled && online,shape=CircleShape,
                    border=BorderStroke(3.dp,Muted),contentPadding=PaddingValues(0.dp),
                    colors=ButtonDefaults.outlinedButtonColors(disabledContentColor=Muted),
                    modifier=Modifier.size(140.dp).semantics { contentDescription="Power off PC. Requires confirmation and PC administrator authentication" }
                ) {
                    Canvas(Modifier.size(44.dp)) {
                        drawArc(Muted,-49f,278f,false,topLeft=Offset(4.dp.toPx(),4.dp.toPx()),size=Size(size.width-8.dp.toPx(),size.height-8.dp.toPx()),style=Stroke(4.dp.toPx(),cap=StrokeCap.Round))
                        drawLine(Muted,Offset(size.width/2,0f),Offset(size.width/2,size.height*.46f),4.dp.toPx(),StrokeCap.Round)
                    }
                }
            }
        }
        Text("Power off PC",modifier=Modifier.align(Alignment.CenterHorizontally),fontSize=24.sp,fontWeight=FontWeight.Bold,color=Muted)
        Spacer(Modifier.height(5.dp))
        Text(if(powerUi.busy) "Waiting for verification or PC response…" else if(powerEnabled) "PC administrator authentication required" else "Protected power access unavailable",modifier=Modifier.align(Alignment.CenterHorizontally),fontSize=12.sp,color=Muted)
        if(powerUi.message.isNotBlank()) { Spacer(Modifier.height(12.dp));Note(powerUi.message) }
        Spacer(Modifier.height(22.dp))
        HorizontalDivider(color=Line)
        Spacer(Modifier.height(8.dp))
        val sample=status.snapshot
        val suffix=if(sample!=null && !online) " · last known" else ""
        PairLine("System state$suffix",sample?.systemState?.replaceFirstChar { it.uppercase() } ?: "—")
        PairLine("Uptime$suffix",sample?.let { readableUptime(it.uptimeSeconds) } ?: "—")
        PairLine("Last connected",status.lastContactMillis?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(it)) } ?: "—")
        if(online && sample?.systemState=="degraded") {
            Spacer(Modifier.height(6.dp)); Note("Some services have failed. See System details.")
        }
    }

}

@Composable internal fun Metric(name: String, description: String, percent: Int?, reading: String, secondary: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Panel).padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=14.dp)) {
            Text(name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.7).sp)
            Text(description, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(7.dp)); Text(secondary, color = Muted, fontSize = 11.sp)
        }
        Box(Modifier.size(72.dp).semantics { contentDescription = "$name: $reading. $secondary" }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Line, style = Stroke(5.dp.toPx()))
                if (percent != null) drawArc(White, -90f, percent * 3.6f, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            }
            Text(reading, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(9.dp))
}
@Preview(name = "01 Power", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PowerPreview() = Sentinel(Page.Power)
@Preview(name = "02 Health", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun HealthPreview() = Sentinel(Page.Health)
@Preview(name = "03 Details", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun DetailsPreview() = Sentinel(Page.Details)
@Preview(name = "04 Activity", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun ActivityPreview() = Sentinel(Page.Activity)
