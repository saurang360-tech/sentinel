package dev.titus.sentinel.monitor

import com.jcraft.jsch.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

sealed class ProbeResult {
    data class Success(val raw: String, val snapshot: Snapshot): ProbeResult()
    data class Processes(val snapshot: ProcessSnapshot): ProbeResult()
    data class Health(val snapshot: HealthSnapshot): ProbeResult()
    data class Failed(val phase: Phase, val detail: String, val diagnostic: String = ""): ProbeResult()
}
internal class PinRepository(private val pin: String): HostKeyRepository {
    @Volatile var verified = false
    @Volatile var mismatch = false
    override fun check(host: String?, key: ByteArray): Int {
        verified = SshEncoding.matchesPin(key,pin)
        mismatch = !verified
        return if(verified) HostKeyRepository.OK else HostKeyRepository.CHANGED
    }
    override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID() = "Sentinel pinned host"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}
private class LegacyProcessRequest: Exception()
class SshTransport {
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task,"Sentinel SSH").apply { isDaemon = true } }
    private val session = AtomicReference<Session?>()
    private var connectedConfig: ConnectionSettings? = null
    fun disconnect() { session.getAndSet(null)?.disconnect() }
    fun close() { disconnect(); worker.shutdownNow() }
    suspend fun probe(config: ConnectionSettings, identity: PhoneIdentity, query: ProcessQuery? = null, health: Boolean = false): ProbeResult = suspendCancellableCoroutine { continuation ->
        val future = worker.submit {
            val result = read(config,identity,query,health)
            if(continuation.isActive) continuation.resume(result)
        }
        continuation.invokeOnCancellation { disconnect(); future.cancel(true) }
    }
    private fun read(config: ConnectionSettings, identity: PhoneIdentity, query: ProcessQuery?, health: Boolean, legacy: Boolean = false): ProbeResult {
        var pinned: PinRepository? = null
        var authenticated = false
        var stage = "connect"
        var responseBytes = 0
        var exitStatus = -1
        try {
            require(config.validationError() == null)
            var ssh = session.get()
            if(ssh == null || !ssh.isConnected || connectedConfig != config) {
                disconnect()
                pinned = PinRepository(config.fingerprint)
                val client = JSch().apply { setHostKeyRepository(pinned); addIdentity(identity,null) }
                ssh = client.getSession(config.username,config.host,config.port).apply {
                    setConfig("StrictHostKeyChecking","yes")
                    setConfig("PreferredAuthentications","publickey")
                    setConfig("PubkeyAcceptedAlgorithms",SshEncoding.ALGORITHM)
                    setConfig("ssh-ed25519","com.jcraft.jsch.bc.SignatureEd25519")
                    setConfig("compression.c2s","none"); setConfig("compression.s2c","none")
                    timeout = 3000
                }
                session.set(ssh)
                if(Thread.currentThread().isInterrupted) throw InterruptedException()
                ssh.connect(3500)
                ssh.timeout = 0
                connectedConfig = config
            }
            authenticated = true
            stage = "channel"
            val channel = ssh.openChannel("exec") as ChannelExec
            try {
                channel.setCommand(if(health) "sentinel-health-v1" else if(query == null) "sentinel-status-v1" else if(legacy) "sentinel-processes-v1 all" else query.command())
                channel.setPty(false)
                channel.setAgentForwarding(false)
                channel.setInputStream(null)
                // Do not expose or persist arbitrary stderr from the remote shell.
                channel.setErrStream(null)
                val input = channel.inputStream
                channel.connect(2000)
                stage = "read"
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                val deadline = System.nanoTime() + 6_000_000_000L
                while(true) {
                    if(Thread.currentThread().isInterrupted) throw InterruptedException()
                    while(input.available() > 0) {
                        val n = input.read(buffer,0,minOf(buffer.size,input.available()))
                        if(n < 0) break
                        require(bytes.size() + n <= if(query == null && !health) 32768 else 131072) { "Oversized status response" }
                        bytes.write(buffer,0,n)
                        responseBytes=bytes.size()
                    }
                    if(channel.isClosed && input.available() == 0) break
                    if(System.nanoTime() > deadline) throw java.net.SocketTimeoutException()
                    Thread.sleep(20)
                }
                stage="exit"
                exitStatus=channel.exitStatus
                if(exitStatus==64 && query!=null && !legacy) throw LegacyProcessRequest()
                require(exitStatus == 0) { "Status command unavailable" }
                stage="decode"
                val raw = bytes.toString(Charsets.UTF_8.name()).trim()
                return if(health) ProbeResult.Health(decodeHealth(raw)) else if(query == null) ProbeResult.Success(raw,decodeSnapshot(raw)) else ProbeResult.Processes(if(legacy) decodeLegacyProcesses(raw,query) else decodeProcesses(raw,query))
            } finally { channel.disconnect() }
        } catch(error: Exception) {
            if(error is LegacyProcessRequest) return read(config,identity,query,health,legacy=true)
            val diagnostic="$stage:${error.javaClass.simpleName};exit=$exitStatus;bytes=$responseBytes"
            if(dev.titus.sentinel.BuildConfig.DEBUG) android.util.Log.w("SentinelTransport",diagnostic)
            disconnect()
            return when {
                pinned?.mismatch == true -> ProbeResult.Failed(Phase.SecurityError,"The server key does not match your saved fingerprint. Connection blocked; verify it outside Sentinel.")
                !authenticated && pinned?.verified == true -> ProbeResult.Failed(Phase.Pairing,"The SSH host key is verified. Sentinel could not authenticate; authorise this phone’s public key in Settings.")
                authenticated -> ProbeResult.Failed(Phase.NoData,"Monitoring was interrupted. Retrying automatically.",diagnostic)
                else -> ProbeResult.Failed(Phase.Unreachable,"No verified SSH response. Check your network and connection settings. The PC may be asleep, off or disconnected; power state is unknown.")
            }
        }
    }
}
