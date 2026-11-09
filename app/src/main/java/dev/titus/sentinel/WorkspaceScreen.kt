package dev.titus.sentinel

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.titus.sentinel.monitor.*
import kotlinx.coroutines.launch

@Composable internal fun Sentinel() {
    val context=LocalContext.current
    val store=remember { WorkspaceStore(context) }
    var layout by remember { mutableStateOf(store.load()) }
    var preferences by remember { mutableStateOf(store.preferences()) }
    var error by remember { mutableStateOf(store.error.orEmpty()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var manageTabs by remember { mutableStateOf(false) }
    var presetTabs by remember { mutableStateOf(false) }
    var addWidget by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf(layout.selected) }
    val pager=rememberPagerState(initialPage=layout.tabs.indexOfFirst { it.id==selectedId }.coerceAtLeast(0)) { layout.tabs.size }
    val scope=rememberCoroutineScope()
    fun save(next: WorkspaceLayout) { runCatching { store.save(next);layout=next }.onFailure { error="Layout could not be saved. Your previous layout is retained." } }
    fun applyLayout(next: WorkspaceLayout) {
        selecting=true
        save(next)
        selectedId=next.selected
        scope.launch { withFrameNanos { };pager.scrollToPage(next.tabs.indexOfFirst { it.id==next.selected }.coerceAtLeast(0));selecting=false }
    }
    val activity=LocalActivity.current as? MainActivity
    val monitoring: StatusViewModel?=if(LocalInspectionMode.current) null else viewModel()
    val terminal: TerminalViewModel?=if(LocalInspectionMode.current) null else viewModel()
    val health=monitoring?.health?.collectAsStateWithLifecycle()?.value ?: HealthUi()
    var query by remember { mutableStateOf(ProcessQuery()) }
    val processes=monitoring?.processes?.collectAsStateWithLifecycle()?.value ?: ProcessUi()
    val status=monitoring?.ui?.collectAsStateWithLifecycle()?.value ?: MonitorUi()
    val history=monitoring?.history?.collectAsStateWithLifecycle()?.value ?: emptyList()
    val sessionSeconds=monitoring?.sessionSeconds?.collectAsStateWithLifecycle()?.value
    val details=monitoring?.serviceDetails?.collectAsStateWithLifecycle()?.value.orEmpty()
    val config=monitoring?.config?.collectAsStateWithLifecycle()?.value ?: ConnectionSettings()
    val users=monitoring?.users?.collectAsStateWithLifecycle()?.value ?: emptyList()
    val profiles=monitoring?.profiles?.collectAsStateWithLifecycle()?.value.orEmpty()
    val watches=monitoring?.watches?.collectAsStateWithLifecycle()?.value.orEmpty()
    val jobs=monitoring?.jobs?.collectAsStateWithLifecycle()?.value.orEmpty()
    val expectations=monitoring?.expectations?.collectAsStateWithLifecycle()?.value.orEmpty()
    val jobMessage=monitoring?.jobMessage?.collectAsStateWithLifecycle()?.value.orEmpty()
    val publicKey=monitoring?.publicKey?.collectAsStateWithLifecycle()?.value.orEmpty()
    val terminalKey=terminal?.publicKey?.collectAsStateWithLifecycle()?.value.orEmpty()
    val terminalUi=terminal?.ui?.collectAsStateWithLifecycle()?.value ?: TerminalUi()
    val unlocked=activity?.settingsVisible?.collectAsStateWithLifecycle()?.value ?: false
    val shellUnlocked=activity?.terminalVisible?.collectAsStateWithLifecycle()?.value ?: false
    val power=activity?.power?.ui?.collectAsStateWithLifecycle()?.value ?: PowerUi()
    val alertPrefs=remember { AlertPreferences(context) }
    var alertPolicy by remember { mutableStateOf(alertPrefs.read()) }
    var armedAction by remember { mutableStateOf<PcPowerAction?>(null) }
    var password by remember { mutableStateOf("") }
    var checkOnly by remember { mutableStateOf(false) }
    val page=layout.tabs.getOrElse(pager.settledPage.coerceAtMost(layout.tabs.lastIndex)) { layout.tabs.first() }
    val kinds=page.widgets.map { it.kind }.toSet()
    val needsHealth=kinds.any { it in setOf(WidgetKind.Connection,WidgetKind.ConnectionDetails,WidgetKind.CpuDetails,WidgetKind.MemoryDetails,WidgetKind.NetworkDetails,WidgetKind.DiskDetails,WidgetKind.Cpu,WidgetKind.Memory,WidgetKind.Storage,WidgetKind.Network,WidgetKind.Disk,WidgetKind.Sensors,WidgetKind.Services,WidgetKind.System,WidgetKind.Power) }
    val needsProcesses=WidgetKind.Processes in kinds || WidgetKind.Users in kinds
    LaunchedEffect(preferences.pollSeconds) { monitoring?.setPolling(preferences.pollSeconds) }
    LaunchedEffect(pager.settledPage,layout.tabs.map { it.id },selecting) {
        if(selecting) return@LaunchedEffect
        val current=layout.tabs.getOrNull(pager.settledPage) ?: return@LaunchedEffect
        selectedId=current.id
        if(layout.selected!=current.id) save(layout.copy(selected=current.id))
        terminal?.disconnect();activity?.terminalVisible?.value=false;activity?.settingsVisible?.value=false
    }
    LaunchedEffect(config) { terminal?.disconnect();terminal?.clear();activity?.terminalVisible?.value=false }
    LaunchedEffect(editing) { if(editing) { terminal?.disconnect();activity?.terminalVisible?.value=false;password="";armedAction=null } }
    LifecycleStartEffect(page.id,needsHealth,needsProcesses,query,editing,config) {
        monitoring?.visible(!editing,if(needsProcesses) query else null,needsHealth)
        onStopOrDispose { monitoring?.visible(false);terminal?.disconnect();password="";armedAction=null }
    }
    BackHandler(editing) { editing=false }
    fun updatePreferences(value: WorkspacePreferences) { runCatching { store.save(value);preferences=value }.onFailure { error="Preferences could not be saved." } }
    fun unlockConnections() { activity?.security?.authenticate("Unlock saved connections") { _,ok -> if(ok) activity.settingsVisible.value=true } }
    SentinelTheme(preferences) {
        Scaffold(containerColor=Ink) { insets ->
            Column(Modifier.padding(insets).imePadding().fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=if(Retro) 4.dp else 16.dp,vertical=if(Retro) 0.dp else 8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(page.name,style=MaterialTheme.typography.headlineLarge,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    TextButton(onClick={ editing=!editing }) { Text(if(editing) "Done" else "Edit") }
                    if(editing) TextButton(onClick={ manageTabs=true }) { Text("Tabs") }
                }
                ScrollableTabRow(selectedTabIndex=pager.currentPage.coerceAtMost(layout.tabs.lastIndex),containerColor=Ink,contentColor=Accent,edgePadding=16.dp) {
                    layout.tabs.forEachIndexed { index,t -> Tab(selected=pager.currentPage==index,onClick={ scope.launch { pager.animateScrollToPage(index) } },text={ Text(t.name,fontSize=16.sp,fontWeight=FontWeight.Bold) }) }
                }
                if(error.isNotBlank()) Text(error,color=Danger,modifier=Modifier.padding(16.dp))
                if(editing) Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("Layout",color=Muted,modifier=Modifier.weight(1f),fontSize=14.sp)
                    TextButton(enabled=page.widgets.size<32,onClick={ addWidget=true }) { Text("Add widget") }
                }
                HorizontalPager(state=pager,modifier=Modifier.weight(1f),userScrollEnabled=!editing && armedAction==null,key={ layout.tabs[it].id }) { index ->
                    val tab=layout.tabs[index]
                    WorkspaceGrid(tab,layout,editing,onChange={ applyLayout(it) }) { widget ->
                        // Adjacent pages must not expose protected forms or a live terminal.
                        val active=index==pager.settledPage && !pager.isScrollInProgress
                        when(widget.kind) {
                            WidgetKind.Connection -> Column(Modifier.verticalScroll(rememberScrollState())) {
                                val phase=if(needsHealth) health.phase else if(needsProcesses) processes.phase else status.phase
                                SimpleStatus(phase,health.lastContactMillis ?: processes.lastContactMillis ?: status.lastContactMillis,health.busy || processes.busy) { monitoring?.refreshWorkspace() }
                                PairLine("Uptime",health.snapshot?.data?.value("uptimeSeconds")?.let { readableUptime(it) } ?: "—")
                            }
                            WidgetKind.ConnectionDetails -> Column(Modifier.verticalScroll(rememberScrollState())) {
                                PairLine("SSH session",sessionSeconds?.let { readableUptime(it.toDouble()) } ?: "Not connected")
                                PairLine("PC uptime",health.snapshot?.data?.value("uptimeSeconds")?.let { readableUptime(it) } ?: status.snapshot?.let { readableUptime(it.uptimeSeconds) } ?: "Unknown")
                                Row(verticalAlignment=Alignment.CenterVertically) { Text("Mute",modifier=Modifier.weight(1f));Switch(alertPolicy.muted,{ muted -> val next=alertPrefs.read().copy(muted=muted);runCatching { alertPrefs.save(next);alertPolicy=next }.onFailure { error="Mute preference could not be saved." } }) }
                            }
                            WidgetKind.CpuDetails,WidgetKind.MemoryDetails,WidgetKind.NetworkDetails,WidgetKind.DiskDetails,WidgetKind.Cpu,WidgetKind.Memory,WidgetKind.Storage,WidgetKind.Network,WidgetKind.Disk,WidgetKind.Sensors,WidgetKind.Services,WidgetKind.System -> TelemetryWidget(widget.kind,health,history,details) { unit,user -> monitoring?.inspectService(unit,user) }
                            WidgetKind.Users -> UsersWidget(processes)
                            WidgetKind.Processes -> ProcessPage(processes,users,query,configure={ unlockConnections() },select={ query=it },refresh={ monitoring?.refreshWorkspace() },watch={ row -> monitoring?.watch(row) })
                            WidgetKind.Watches -> WatchedWidget(watches,watchHost(config)) { monitoring?.clearWatches() }
                            WidgetKind.Jobs -> JobsWidget(jobs,expectations,watchHost(config),jobMessage)
                            WidgetKind.Monitor -> Column(Modifier.verticalScroll(rememberScrollState())) { TaskMonitorControls(config,watches) }
                            WidgetKind.Alerts -> Column(Modifier.verticalScroll(rememberScrollState())) { AlertTestControls() }
                            WidgetKind.Delivery -> DeliveryWidget()
                            WidgetKind.Terminal -> if(!shellUnlocked || !active) ProtectedWidget("Terminal details") { activity?.security?.authenticate("Unlock terminal") { _,ok -> if(ok) activity.terminalVisible.value=true } }
                                else TerminalPage(terminalUi,connect={ terminal?.connect(config) },disconnect={ terminal?.disconnect() },send={ terminal?.send(it) },clear={ terminal?.clear() })
                            WidgetKind.Power -> Column(Modifier.verticalScroll(rememberScrollState())) {
                                Note("Hold, then enter your PC password.")
                                HoldControl("Hold: Shutdown",enabled=health.phase==Phase.Online && !power.busy) { checkOnly=false;armedAction=PcPowerAction.Shutdown }
                                HoldControl("Hold: Reboot",enabled=health.phase==Phase.Online && !power.busy) { checkOnly=false;armedAction=PcPowerAction.Reboot }
                                if(power.message.isNotBlank()) Note(power.message)
                                if(health.phase!=Phase.Online) Note("A fresh authenticated reading is required.")
                            }
                            WidgetKind.Preferences -> PreferencesWidget(preferences,save={ updatePreferences(it) },tabs={ presetTabs=true },manage={ manageTabs=true })
                            WidgetKind.Connections -> if(!unlocked || !active) ProtectedWidget("Saved connections") { unlockConnections() }
                                else Column(Modifier.verticalScroll(rememberScrollState())) { ConnectionSettingsPage(config,publicKey,status,users,terminalKey,
                                    save={ activity?.power?.cancel();monitoring?.save(it) },check={ monitoring?.checkNow() },erase={ TaskMonitoring.stop(context);activity?.power?.cancel();terminal?.disconnect();terminal?.clear();monitoring?.visible(false) },profiles=profiles,selectProfile={ activity?.power?.cancel();monitoring?.selectProfile(it) },powerUi=power,checkPower={ checkOnly=true;armedAction=PcPowerAction.Shutdown }) }
                        }
                    }
                }
            }
        }
        if(presetTabs) PresetPicker(layout,onAdd={ preset -> applyLayout(layout.addPreset(preset));presetTabs=false },close={ presetTabs=false })
        if(manageTabs) TabManager(layout,onChange={ applyLayout(it) },close={ manageTabs=false })
        if(addWidget) AlertDialog(onDismissRequest={ addWidget=false },title={ Text("Add widget") },text={ Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState())) {
            WidgetKind.entries.forEach { kind -> TextButton(onClick={ applyLayout(layout.update(page.copy(widgets=page.widgets+WidgetSpec(kind=kind))));addWidget=false }) { Text(kind.title) } }
        } },confirmButton={ TextButton(onClick={ addWidget=false }) { Text("Cancel") } })
        armedAction?.let { action -> AlertDialog(onDismissRequest={ password="";armedAction=null },title={ Text(if(checkOnly) "Verify PC authentication" else if(action==PcPowerAction.Reboot) "Reboot this PC?" else "Shut down this PC?") },
            text={ Column { Text(if(checkOnly) "Verify authentication without a power action." else "This interrupts running work and all connected users. No automatic retry.");OutlinedTextField(password,{ password=it },label={ Text("PC administrator password") },singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password,autoCorrectEnabled=false),visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());Note("Sent only through pinned SSH; never saved.") } },
            confirmButton={ TextButton(enabled=password.isNotBlank(),onClick={ val secret=password.toCharArray();password="";armedAction=null;activity?.power?.request(config,secret,checkOnly,action) ?: secret.fill('\u0000') }) { Text(if(checkOnly) "Verify only" else "Authenticate & confirm") } },dismissButton={ TextButton(onClick={ password="";armedAction=null }) { Text("Cancel") } }) }
    }
}

@Composable private fun ProtectedWidget(label: String,unlock: ()->Unit) { Column { Text(label,style=MaterialTheme.typography.titleLarge);Note("Verify with your phone to reveal this widget.");Spacer(Modifier.height(16.dp));Button(onClick=unlock) { Text("Unlock") } } }

@Composable internal fun HoldControl(label: String,enabled: Boolean,onArmed: ()->Unit) {
    var held by remember { mutableStateOf(false) }
    val action by rememberUpdatedState(onArmed)
    val progress by androidx.compose.animation.core.animateFloatAsState(if(held) 1f else 0f,animationSpec=androidx.compose.animation.core.tween(if(held) 1500 else 100),label="Hold confirmation")
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(min=56.dp).border(1.dp,if(enabled) Accent else Line,RoundedCornerShape(8.dp))
            .semantics { contentDescription=label;stateDescription=if(enabled) "Hold for 1.5 seconds, then enter the PC password" else "Unavailable";if(enabled) onClick(label="Arm password confirmation") { action();true } }
            .pointerInput(enabled) { if(enabled) detectTapGestures(onPress={ held=true;try { val released=kotlinx.coroutines.withTimeoutOrNull(1500) { tryAwaitRelease() };if(released==null) action() } finally { held=false } }) },contentAlignment=Alignment.Center) {
            Text(label,color=if(enabled) White else Muted,fontWeight=FontWeight.Bold)
        }
        LinearProgressIndicator(progress={ progress },modifier=Modifier.fillMaxWidth(),color=Accent,trackColor=Line)
    }
}

@Composable internal fun PreferencesWidget(value: WorkspacePreferences,save: (WorkspacePreferences)->Unit,tabs: (()->Unit)?=null,manage: (()->Unit)?=null) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if(tabs!=null) {
            Text("Tabs",fontWeight=FontWeight.Bold)
            Row { TextButton(onClick=tabs) { Text("Add preset") };if(manage!=null) TextButton(onClick=manage) { Text("Manage tabs") } }
        }
        Text("Refresh",fontWeight=FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState())) { listOf(1,2,5,10).forEach { seconds -> FilterChip(selected=value.pollSeconds==seconds,onClick={ save(value.copy(pollSeconds=seconds)) },label={ Text("${seconds}s") }) } }

        Text("Theme",fontWeight=FontWeight.Bold)
        listOf("Nord" to "Nord", "Amber" to "Amber", "Mono" to "Monochrome").forEach { (id,label) ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { RadioButton(selected=value.theme==id,onClick={ save(value.copy(theme=id,nord=id=="Nord")) });TextButton(onClick={ save(value.copy(theme=id,nord=id=="Nord")) }) { Text(label) } }
        }
        if(value.theme!="Amber") { Text("Accent",fontWeight=FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState())) { listOf("Frost","Ice","Snow").forEach { name -> FilterChip(selected=value.accent==name,onClick={ save(value.copy(accent=name)) },label={ Text(name) }) } }
        }

    }
}

@Composable private fun DeliveryWidget() {
    val state by TaskMonitoring.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(if(state.running) "Monitor active" else "Monitor stopped",fontSize=22.sp,fontWeight=FontWeight.Bold)
        Text(state.handoff)
        Spacer(Modifier.height(12.dp));Note("OS acceptance and confirmed display are different. This app cannot confirm that you saw or heard a notification.")
        Note("Push/webhook relay: not configured. Notifications currently use Android’s local notification service.")
        Note("Last PC observation: "+(state.observedMillis?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: "none"))
    }
}

@Composable private fun SimpleStatus(phase: Phase,time: Long?,busy: Boolean,refresh: ()->Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(time) { while(true) { now=System.currentTimeMillis();kotlinx.coroutines.delay(1000) } }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(statusLabel(phase),fontWeight=FontWeight.Bold,fontSize=20.sp)
            time?.let { Text("${((now-it).coerceAtLeast(0)/1000)}s ago",color=Muted,fontSize=14.sp) }
        }
        TextButton(enabled=!busy,onClick=refresh) { Text(if(busy) "Updating…" else "Refresh") }
    }
}
