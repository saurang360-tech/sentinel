package dev.titus.sentinel.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class TaskSessionState(val running: Boolean=false,val host: String="",val heartbeatMillis: Long?=null,
    val observedMillis: Long?=null,val message: String="Background monitoring is stopped.",val handoff: String="No notification handoff this session.",
    val watches: List<TaskWatch> = emptyList(),val jobs: List<VerifiedJob> = emptyList(),val healthMessage: String="Sustained health alerts are off.",val expectations: List<JobExpectation> = emptyList())
internal data class TaskSample(val watches: List<TaskWatch>,val jobs: List<VerifiedJob>,val health: HealthSnapshot?=null,val healthProblem: String?=null,val expectations: List<JobExpectation> = emptyList())
internal interface TaskSource {
    suspend fun sample(watches: List<TaskWatch>): TaskSample
    fun close()
}
internal class SshTaskSource(context: Context,private val config: ConnectionSettings): TaskSource {
    private val transport=SshTransport()
    private val identity=PhoneIdentity(context)
    private val alerts=AlertPreferences(context)
    override suspend fun sample(watches: List<TaskWatch>): TaskSample {
        val result=transport.probe(config,identity)
        if(result is ProbeResult.Failed) throw TaskProbeFailure(result.detail,result.phase.stopsAutomaticRetry())
        require(result is ProbeResult.Success) { "Status response unavailable." }
        require(org.json.JSONObject(result.raw).optJSONObject("capabilities")?.optInt("jobs")==1) { "Install the updated PC helper to monitor jobs in the background." }
        val pending=watches.filterNot { it.terminal }
        val refreshed=if(pending.isEmpty()) emptyList() else {
            val response=transport.probe(config,identity,watchCommand="sentinel-watches-v1 "+pending.joinToString(" ") { it.identity })
            if(response is ProbeResult.Failed) throw TaskProbeFailure(response.detail,response.phase.stopsAutomaticRetry())
            require(response is ProbeResult.Watches) { "Watch response unavailable." }
            decodeWatchResult(response.raw,pending,System.currentTimeMillis())
        }
        val response=transport.probe(config,identity,watchCommand="sentinel-jobs-v1")
        if(response is ProbeResult.Failed) throw TaskProbeFailure(response.detail,response.phase.stopsAutomaticRetry())
        require(response is ProbeResult.Watches) { "Job response unavailable." }
        var health: HealthSnapshot?=null
        var healthProblem: String?=null
        if(alerts.read().health) {
            val sample=transport.probe(config,identity,health=true)
            if(sample is ProbeResult.Failed && sample.phase.stopsAutomaticRetry()) throw TaskProbeFailure(sample.detail,true)
            if(sample is ProbeResult.Health) health=sample.snapshot else healthProblem="Health readings unavailable; no recovery can be confirmed."
        }
        return TaskSample(watches.map { old -> refreshed.find { it.identity==old.identity } ?: old },decodeJobs(response.raw),health,healthProblem,decodeExpectations(response.raw))
    }
    override fun close() { transport.close() }
}
internal class TaskProbeFailure(message: String,val stop: Boolean): Exception(message)
internal data class TaskSession(val config: ConnectionSettings,val watches: List<TaskWatch>,val source: ((Context)->TaskSource)?=null,val intervalMillis: Long=30_000)

/** In-memory session only: no credentials in Intents and no silent restart after process death/reboot. */
internal object TaskMonitoring {
    private val mutable=MutableStateFlow(TaskSessionState())
    val state=mutable.asStateFlow()
    private var pending: TaskSession?=null
    fun publish(value: TaskSessionState) { mutable.value=value }
    fun takeSession(): TaskSession?=pending.also { pending=null }
    fun start(context: Context,config: ConnectionSettings,watches: List<TaskWatch>): String? = startSession(context,TaskSession(config,watches))
    internal fun startSession(context: Context,session: TaskSession): String? {
        if(state.value.running || pending!=null) return "A monitor is already active. Stop it before starting another."
        if(session.source==null && !AppAccess.canConnect(context)) return "Accept the privacy notice and allow local-network access first."
        if(session.config.validationError()!=null) return "Save a valid connection first."
        if(!TaskNotifications(context).allowed()) return "Allow notifications before starting background monitoring."
        if(session.source!=null || session.intervalMillis!=30_000L) require(dev.titus.sentinel.BuildConfig.DEBUG)
        pending=session
        return runCatching {
            context.startForegroundService(Intent(context,TaskMonitorService::class.java))
            null
        }.getOrElse { pending=null;"Android could not start monitoring. Keep Sentinel open and try again." }
    }
    fun stop(context: Context) {
        pending=null
        context.stopService(Intent(context,TaskMonitorService::class.java))
        mutable.value=mutable.value.copy(message=if(mutable.value.running) "Stopping background monitoring; results are last known." else "Background monitoring is stopped.")
    }
}

class TaskMonitorService: Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var source: TaskSource?=null
    private var work: Job?=null
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action==STOP) { stopSelf();return START_NOT_STICKY }
        val session=TaskMonitoring.takeSession()
        if(session==null || work!=null) { if(work==null) stopSelf();return START_NOT_STICKY }
        val notifications=TaskNotifications(this,if(session.source!=null) "sentinel-test-alert-receipts" else "sentinel-alert-receipts")
        if((session.source==null && !AppAccess.canConnect(this)) || !notifications.allowed()) { stopSelf();return START_NOT_STICKY }
        try {
            startForeground(TaskNotifications.ONGOING,notifications.ongoing("Starting; keep the PC reachable."),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch(error: RuntimeException) {
            TaskMonitoring.publish(TaskSessionState(message="Android refused background monitoring. Check permissions and start while Sentinel is visible."))
            stopSelf();return START_NOT_STICKY
        }
        val alerts=AlertPreferences(this,if(session.source!=null) "sentinel-test-alert-policy" else "sentinel-alert-policy")
        var healthEngine=SustainedHealth()
        val host=watchHost(session.config)
        TaskMonitoring.publish(TaskSessionState(running=true,host=host,watches=session.watches,message="Connecting; no live task results yet."))
        work=scope.launch {
            var watches=session.watches
            var baselined=false
            var failures=0
            try {
                source=withContext(Dispatchers.IO) { session.source?.invoke(this@TaskMonitorService) ?: SshTaskSource(this@TaskMonitorService,session.config) }
                while(isActive) {
                    if((session.source==null && !AppAccess.canConnect(this@TaskMonitorService)) || !notifications.allowed()) {
                        TaskMonitoring.publish(TaskMonitoring.state.value.copy(message="Monitoring stopped: notice or permission is no longer available."))
                        break
                    }
                    val now=System.currentTimeMillis()
                    TaskMonitoring.publish(TaskMonitoring.state.value.copy(heartbeatMillis=now))
                    try {
                        val sample=withTimeout(25_000) { source!!.sample(watches) }
                        ensureActive()
                        failures=0
                        // First sample seeds historical jobs, while selected process watches can alert immediately.
                        if(!baselined) {
                            notifications.seed(outcomeEvents(host,session.watches.filter { it.terminal },sample.jobs).map { it.key })
                            baselined=true
                        }
                        watches=sample.watches
                        var healthMessage="Sustained health alerts are off."
                        val healthEvents=if(alerts.read().health) {
                            if(sample.health!=null) {
                                val transitions=healthEngine.observe(healthReadings(sample.health),android.os.SystemClock.elapsedRealtime())
                                healthMessage=healthEngine.summary
                                transitions.map { transition ->
                                    val scope="$host:health:${transition.key}"
                                    OutcomeEvent("$scope:${transition.episode}:${transition.recovered}",
                                        transition.label+(if(transition.recovered) ": recovered for 60 seconds" else ": sustained for 60 seconds"),
                                        if(transition.recovered) 1 else 2,scope)
                                }
                            } else { healthEngine.interrupted();healthMessage=sample.healthProblem ?: healthEngine.summary;emptyList() }
                        } else { healthEngine=SustainedHealth();emptyList() }
                        val handoff=notifications.handoff(outcomeEvents(host,watches,sample.jobs)+healthEvents+expectationEvents(host,sample.expectations),
                            sample.expectations.filter { it.state!="overdue_unknown" }.map { "$host:expectation:${it.id}" }.toSet())
                        val state=TaskMonitoring.state.value
                        TaskMonitoring.publish(state.copy(observedMillis=System.currentTimeMillis(),watches=watches,jobs=sample.jobs,healthMessage=healthMessage,expectations=sample.expectations,
                            message="Task checks active. Approx. 30-second polling; Android/network delays are possible.",handoff=handoff ?: state.handoff))
                        notifications.update("Monitoring active · last PC observation ${java.time.Instant.now()}")
                    } catch(cancelled: CancellationException) {
                        if(cancelled !is TimeoutCancellationException) throw cancelled
                        failures++
                        healthEngine.interrupted()
                        unavailable("PC check timed out. Task outcomes and power state are unknown.",notifications)
                    } catch(error: Exception) {
                        failures++
                        healthEngine.interrupted()
                        unavailable(if(error is TaskProbeFailure) error.message!! else "Task check unavailable. Check the connection and updated PC helper.",notifications)
                        if(error is TaskProbeFailure && error.stop) break
                    }
                    delay(if(failures==0) session.intervalMillis else minOf(300_000L,30_000L shl minOf(failures,3)))
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) { unavailable("Monitoring could not open the device key. Reopen Sentinel and restart the session.",notifications) }
            finally { stopSelf() }
        }
        return START_NOT_STICKY
    }
    private fun unavailable(message: String,notifications: TaskNotifications) {
        val state=TaskMonitoring.state.value
        TaskMonitoring.publish(state.copy(message=message,healthMessage="Health observation interrupted; recovery unknown.",watches=state.watches.map { if(it.terminal) it else it.copy(state="unavailable") }))
        notifications.update("Connection/monitoring interrupted; live task outcomes unknown.")
    }
    override fun onDestroy() {
        work?.cancel();source?.close();scope.cancel()
        val state=TaskMonitoring.state.value
        TaskMonitoring.publish(state.copy(running=false,message="${state.message} Session stopped; restart from Sentinel to resume."))
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object { const val STOP="dev.titus.sentinel.STOP_TASK_MONITOR" }
}
