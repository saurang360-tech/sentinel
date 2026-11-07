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
    private var healthMode = false
    private var processQuery: ProcessQuery? = null
    private val _config = MutableStateFlow(store.settings())
    val config = _config.asStateFlow()
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
        processQuery=query; healthMode=health; visible=true; start(true)
    }
    private fun stop() {
        polling?.cancel(); polling = null; freshness?.cancel(); freshness = null; transport.disconnect()
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
        stop()
        return runCatching {
            if(config != _config.value) { store.saveSettings(config); _config.value=config; _profiles.value=store.profiles(); _ui.value=MonitorUi(); _processUi.value=ProcessUi() }
            processQuery=null; healthMode=false; _healthUi.value=HealthUi(); _users.value=emptyList(); start(visible); null
        }.getOrElse { "Settings could not be saved. Please try again." }
    }
    fun selectProfile(id: String): String? = runCatching {
        stop();_config.value=store.select(id);_profiles.value=store.profiles()
        _ui.value=MonitorUi();_processUi.value=ProcessUi();_healthUi.value=HealthUi();_users.value=emptyList()
        processQuery=null;healthMode=false;start(visible);null
    }.getOrElse { "Saved connection could not be opened. Unlock the phone and try again." }
    private fun start(repeat: Boolean) {
        stop()
        if(!AppAccess.canConnect(getApplication())) return
        if(_config.value.validationError()!=null) return
        val key = identity ?: return
        if(repeat) freshness = viewModelScope.launch {
            while(isActive) { delay(1000); _ui.value = _ui.value.aged(SystemClock.elapsedRealtime()); _processUi.value = _processUi.value.aged(SystemClock.elapsedRealtime()); _healthUi.value = _healthUi.value.aged(SystemClock.elapsedRealtime()) }
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
                    is ProbeResult.Success -> {
                        failures=0
                        _users.value=result.snapshot.users
                        _ui.value=MonitorUi(Phase.Online,"Authenticated status received from ${result.snapshot.host}.",result.snapshot,now,SystemClock.elapsedRealtime(),now)
                    }
                    is ProbeResult.Health -> {
                        failures=0
                        _healthUi.value=HealthUi(Phase.Online,"Authenticated health sample from ${result.snapshot.host}.",result.snapshot,now,SystemClock.elapsedRealtime())
                    }
                    is ProbeResult.Processes -> {
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
                if(!repeat) break
                val delayMs = if(failures == 0) 10_000L else minOf(60_000L,5_000L shl minOf(failures,4)) + Random.nextLong(1000)
                delay(delayMs)
            } while(isActive && visible)
        }
    }
    override fun onCleared() { polling?.cancel(); freshness?.cancel(); transport.close() }
}
