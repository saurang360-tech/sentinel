package dev.titus.sentinel.monitor

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

class StatusViewModel(application: Application): AndroidViewModel(application) {
    private val store = MonitorStore(application)
    private val transport = SshTransport()
    private val mutex = Mutex()
    private val _ui = MutableStateFlow(store.cached())
    val ui = _ui.asStateFlow()
    private val _processUi = MutableStateFlow(ProcessUi())
    val processes = _processUi.asStateFlow()
    private val _healthUi = MutableStateFlow(HealthUi())
    val health = _healthUi.asStateFlow()
    private val _users=MutableStateFlow<List<PcUser>>(emptyList())
    val users=_users.asStateFlow()
    private val _sessionSeconds=MutableStateFlow<Long?>(null)
    internal val sessionSeconds=_sessionSeconds.asStateFlow()
    private val _history=MutableStateFlow<List<TelemetryPoint>>(emptyList())
    internal val history=_history.asStateFlow()
    private val _serviceDetails=MutableStateFlow<Map<String,String>>(emptyMap())
    internal val serviceDetails=_serviceDetails.asStateFlow()
    private var intervalMillis=10_000L
    fun setPolling(seconds: Int) { require(seconds in listOf(1,2,5,10));val next=seconds*1000L;if(next!=intervalMillis) { intervalMillis=next;if(visible) start(true) } }
    fun refreshWorkspace() { start(visible) }
    fun inspectService(unit: String,user: Boolean) {
        if(!isServiceUnit(unit)) return
        val key=identity ?: return
        val config=_config.value
        val scope=(if(user) "user" else "system")+" "+unit
        _serviceDetails.value=_serviceDetails.value+(scope to "Loading recent journal entries…")
        viewModelScope.launch {
            val result=mutex.withLock { withTimeoutOrNull(9000) { transport.probe(config,key,watchCommand="sentinel-service-v1 $scope") } }
            if(config!=_config.value) return@launch
            val detail=if(result is ProbeResult.Watches) runCatching {
                val root=org.json.JSONObject(result.raw);require(root.getInt("protocol")==1 && root.getString("kind")=="service")
                root.getString("detail").take(16000)
            }.getOrDefault("Service details unavailable.") else "Journal unavailable. Update the helper or check this account’s journal permissions."
            _serviceDetails.value=_serviceDetails.value+(scope to detail)
        }
    }
    private var healthMode = false
    private var processQuery: ProcessQuery? = null
    private val _config = MutableStateFlow(store.settings())
    val config = _config.asStateFlow()
    private val _watches = MutableStateFlow(loadWatches())
    val watches = _watches.asStateFlow()
    private val _jobs=MutableStateFlow<List<VerifiedJob>>(emptyList())
    val jobs=_jobs.asStateFlow()
    private val _expectations=MutableStateFlow<List<JobExpectation>>(emptyList())
    internal val expectations=_expectations.asStateFlow()
    private val _jobMessage=MutableStateFlow("Update the PC helper to enable verified job results. Optional background sessions are controlled separately below.")
    val jobMessage=_jobMessage.asStateFlow()
    private var jobsSupported=false
    private suspend fun checkJobs(key: PhoneIdentity) {
        if(!jobsSupported) return
        val result=withTimeoutOrNull(9000) { transport.probe(_config.value,key,watchCommand="sentinel-jobs-v1") }
        currentCoroutineContext().ensureActive()
        val values=if(result is ProbeResult.Watches) runCatching { decodeJobs(result.raw) to decodeExpectations(result.raw) }.getOrNull() else null
        if(values!=null) {
            _expectations.value=values.second
            _jobs.value=values.first
            _jobMessage.value="Job results received ${java.time.Instant.now()}. Foreground sample. PC history: 7 days, up to 64 jobs."
        } else _jobMessage.value="Job checks unavailable; shown results are last known. Check the background monitor panel for session status."
    }
    private fun loadWatches() = runCatching { store.watches(_config.value).map { if(it.terminal) it else it.copy(state="unavailable") } }.getOrDefault(emptyList())
    fun watch(row: ProcessRow) {
        val sample=_processUi.value
        val boot=sample.snapshot?.bootId ?: return
        if(sample.phase!=Phase.Online || sample.aged(SystemClock.elapsedRealtime()).phase!=Phase.Online) return
        val id="${row.pid}:${row.startTicks}:$boot"
        if(_watches.value.size>=32 || _watches.value.any { it.identity==id }) return
        TaskMonitoring.stop(getApplication())
        persistWatches(_watches.value + TaskWatch(id,row.name).observe("running",System.currentTimeMillis()))
    }
    fun clearWatches() { TaskMonitoring.stop(getApplication());persistWatches(emptyList()) }
    private fun mergeSession() {
        val session=TaskMonitoring.state.value
        if(session.host!=watchHost(_config.value)) return
        val merged=_watches.value.map { old -> session.watches.find { it.identity==old.identity && it.observedMillis>old.observedMillis } ?: old }
        if(merged!=_watches.value) persistWatches(merged)
        if(session.jobs.isNotEmpty()) _jobs.value=session.jobs
        _expectations.value=session.expectations
    }
    private fun persistWatches(values: List<TaskWatch>) {
        runCatching { store.saveWatches(_config.value,values) }.onSuccess { _watches.value=values }
    }
    private suspend fun checkWatches(key: PhoneIdentity) {
        val pending=_watches.value.filterNot { it.terminal }
        if(pending.isEmpty()) return
        val result=withTimeoutOrNull(9000) { transport.probe(_config.value,key,watchCommand="sentinel-watches-v1 "+pending.joinToString(" ") { it.identity }) }
        ensureActiveWatch()
        val updated=if(result is ProbeResult.Watches) runCatching { decodeWatchResult(result.raw,pending,System.currentTimeMillis()) }.getOrNull() else null
        val values=updated ?: pending.map { it.observe("unavailable",System.currentTimeMillis()) }
        persistWatches(_watches.value.map { old -> values.find { it.identity==old.identity } ?: old })
    }
    private suspend fun ensureActiveWatch() { currentCoroutineContext().ensureActive() }
    private val _profiles=MutableStateFlow(store.profiles())
    val profiles=_profiles.asStateFlow()
    private val _publicKey = MutableStateFlow("")
    val publicKey = _publicKey.asStateFlow()
    private var identity: PhoneIdentity? = null
    private var polling: Job? = null
    private var freshness: Job? = null
    private var visible = false
    init {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { PhoneIdentity(application) } }
                .onSuccess { identity = it; _publicKey.value = it.publicLine(); if(visible) start(true) }
                .onFailure { _ui.value = _ui.value.copy(phase=Phase.Unknown,message="The device key could not be opened. Reopen Sentinel or check device security settings.") }
        }
    }
    fun visible(value: Boolean, query: ProcessQuery? = null, health: Boolean = false) {
        if(!value) { visible=false; stop(); return }
        if(value == visible && query == processQuery && health == healthMode) return
        if(query != null && query != processQuery) _processUi.value=ProcessUi()
        mergeSession()
        processQuery=query; healthMode=health; visible=true; start(true)
    }
    private fun stop() {
        if(_jobs.value.isNotEmpty()) _jobMessage.value="Monitoring paused; job results are last known. Reopen Sentinel to refresh."
        polling?.cancel(); polling = null; freshness?.cancel(); freshness = null; transport.disconnect();_sessionSeconds.value=null
        _watches.value=_watches.value.map { if(it.terminal) it else it.copy(state="unavailable") }
        _ui.value = _ui.value.aged(SystemClock.elapsedRealtime()).copy(busy=false)
        _processUi.value = _processUi.value.aged(SystemClock.elapsedRealtime()).copy(busy=false)
        _healthUi.value = _healthUi.value.aged(SystemClock.elapsedRealtime()).copy(busy=false)
    }
    fun checkHealth() { healthMode=true; processQuery=null; start(visible) }
    fun checkNow() { healthMode=false; processQuery=null; start(visible) }
    fun checkProcesses(query: ProcessQuery) {
        healthMode=false
        if(query != processQuery) _processUi.value=ProcessUi()
        processQuery=query; start(visible)
    }
    fun save(config: ConnectionSettings): String? {
        config.validationError()?.let { return it }
        TaskMonitoring.stop(getApplication())
        stop()
        return runCatching {
            if(config != _config.value) { store.saveSettings(config); _config.value=config; _profiles.value=store.profiles(); _ui.value=MonitorUi(); _processUi.value=ProcessUi() }
            processQuery=null; healthMode=false; _healthUi.value=HealthUi(); _history.value=emptyList();_serviceDetails.value=emptyMap();_users.value=emptyList(); _watches.value=loadWatches(); _jobs.value=emptyList();_expectations.value=emptyList();jobsSupported=false;_jobMessage.value="Awaiting helper capabilities. Background sessions require an explicit start."; start(visible); null
        }.getOrElse { "Settings could not be saved. Please try again." }
    }
    fun selectProfile(id: String): String? = runCatching {
        TaskMonitoring.stop(getApplication())
        stop();_config.value=store.select(id);_profiles.value=store.profiles()
        _ui.value=MonitorUi();_processUi.value=ProcessUi();_healthUi.value=HealthUi();_history.value=emptyList();_serviceDetails.value=emptyMap();_users.value=emptyList()
        processQuery=null;healthMode=false;_watches.value=loadWatches(); _jobs.value=emptyList();_expectations.value=emptyList();jobsSupported=false;_jobMessage.value="Awaiting helper capabilities. Background sessions require an explicit start.";start(visible);null
    }.getOrElse { "Saved connection could not be opened. Unlock the phone and try again." }
    private fun start(repeat: Boolean) {
        stop()
        if(!AppAccess.canConnect(getApplication())) return
        if(_config.value.validationError()!=null) return
        val key = identity ?: return
        if(repeat) freshness = viewModelScope.launch {
            while(isActive) { delay(1000); _sessionSeconds.value=transport.connectedElapsed?.let { (SystemClock.elapsedRealtime()-it).coerceAtLeast(0)/1000 };_ui.value = _ui.value.aged(SystemClock.elapsedRealtime()); _processUi.value = _processUi.value.aged(SystemClock.elapsedRealtime()); _healthUi.value = _healthUi.value.aged(SystemClock.elapsedRealtime()) }
        }
        polling = viewModelScope.launch {
            var failures = 0
            do {
                val query=processQuery
                val health=healthMode
                if(health) {
                    val previous=_healthUi.value
                    _healthUi.value=previous.copy(busy=true,phase=if(previous.snapshot == null) Phase.Checking else previous.phase)
                } else if(query == null) {
                    val previous = _ui.value
                    _ui.value = previous.copy(busy=true,phase=if(previous.snapshot == null) Phase.Checking else previous.phase)
                } else {
                    val previous = _processUi.value
                    _processUi.value = previous.copy(busy=true,phase=if(previous.snapshot == null) Phase.Checking else previous.phase)
                }
                val result = mutex.withLock { withTimeoutOrNull(9000) { transport.probe(_config.value,key,query,health) } }
                    ?: ProbeResult.Failed(Phase.Unreachable,"Connection timed out. Power state is unknown.")
                ensureActive()
                val now = System.currentTimeMillis()
                when(result) {
                    is ProbeResult.Watches -> Unit
                    is ProbeResult.Success -> {
                        failures=0
                        jobsSupported=org.json.JSONObject(result.raw).optJSONObject("capabilities")?.optInt("jobs")==1
                        _users.value=result.snapshot.users
                        _ui.value=MonitorUi(Phase.Online,"Authenticated status received from ${result.snapshot.host}.",result.snapshot,now,SystemClock.elapsedRealtime(),now)
                    }
                    is ProbeResult.Health -> {
                        jobsSupported=result.snapshot.data.optJSONObject("capabilities")?.optInt("jobs")==1
                        failures=0
                        _healthUi.value=HealthUi(Phase.Online,"Authenticated health sample from ${result.snapshot.host}.",result.snapshot,now,SystemClock.elapsedRealtime())
                        val point=telemetry(result.snapshot,now,SystemClock.elapsedRealtime())
                        _history.value=(_history.value+point).filter { point.elapsed-it.elapsed<=600_000 }.takeLast(600)
                    }
                    is ProbeResult.Processes -> {
                        jobsSupported=result.snapshot.jobsSupported
                        failures=0
                        _users.value=result.snapshot.users
                        _processUi.value=ProcessUi(Phase.Online,"Authenticated process sample from ${result.snapshot.host}.",result.snapshot,now,SystemClock.elapsedRealtime())
                    }
                    is ProbeResult.Failed -> {
                        failures++
                        if(health) _healthUi.value=_healthUi.value.copy(phase=result.phase,message=result.detail,busy=false)
                        else if(query == null) _ui.value=_ui.value.copy(phase=result.phase,message=result.detail,busy=false,checkedMillis=now)
                        else _processUi.value=_processUi.value.copy(phase=result.phase,message=result.detail,busy=false)
                        // Avoid repeated auth attempts and security-error retries until the owner intervenes.
                        if(result.phase.stopsAutomaticRetry()) break
                    }
                }
                if(health && query!=null && result is ProbeResult.Health) {
                    val extra=mutex.withLock { withTimeoutOrNull(9000) { transport.probe(_config.value,key,query) } }
                    ensureActive()
                    if(extra is ProbeResult.Processes) { _users.value=extra.snapshot.users;_processUi.value=ProcessUi(Phase.Online,"Process sample received",extra.snapshot,System.currentTimeMillis(),SystemClock.elapsedRealtime()) }
                    else if(extra is ProbeResult.Failed) _processUi.value=_processUi.value.copy(phase=extra.phase,message=extra.detail,busy=false)
                }
                checkWatches(key)
                checkJobs(key)
                if(!repeat) break
                val delayMs = if(failures == 0) intervalMillis else minOf(60_000L,5_000L shl minOf(failures,4)) + Random.nextLong(1000)
                delay(delayMs)
            } while(isActive && visible)
        }
    }
    override fun onCleared() { polling?.cancel(); freshness?.cancel(); transport.close() }
}
