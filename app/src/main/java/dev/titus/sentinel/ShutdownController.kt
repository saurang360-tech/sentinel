package dev.titus.sentinel

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import dev.titus.sentinel.monitor.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.nio.CharBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal enum class PcPowerAction { Shutdown, Reboot }
internal data class PowerUi(val busy: Boolean=false,val message: String="")
internal class ShutdownController(private val activity: ComponentActivity) {
    private val state=MutableStateFlow(PowerUi());val ui=state.asStateFlow()
    private val worker=Executors.newSingleThreadExecutor()
    private val generation=AtomicInteger()
    private val session=AtomicReference<Session?>()
    fun cancel() {
        generation.incrementAndGet();session.getAndSet(null)?.disconnect()
        if(state.value.busy) state.value=PowerUi(message="Cancelled. A shutdown already sent cannot be recalled. No automatic retry.")
    }
    fun close() { cancel();worker.shutdownNow() }
    fun request(config: ConnectionSettings,password: CharArray,checkOnly: Boolean=false,action: PcPowerAction=PcPowerAction.Shutdown) {
        if(state.value.busy) { password.fill('\u0000');return }
        if(!AppAccess.canConnect(activity) || config.validationError()!=null ||
            password.isEmpty() || password.size>1024 || password.any { it=='\n' || it=='\r' || it=='\u0000' }) {
            password.fill('\u0000');state.value=PowerUi(message="Enter the PC account’s administrator password. Line breaks and NUL are not supported.");return
        }
        val encoded=Charsets.UTF_8.encode(CharBuffer.wrap(password))
        val secret=ByteArray(encoded.remaining()+1);encoded.get(secret,0,secret.size-1);secret[secret.lastIndex]=10
        if(encoded.hasArray()) encoded.array().fill(0);password.fill('\u0000')
        val ticket=generation.incrementAndGet()
        state.value=PowerUi(true,if(checkOnly) "Verifying PC administrator authentication…" else "Authenticating with the PC…")
        worker.submit {
            var executionRequested=false
            var refused=false
            val pin=PinRepository(config.fingerprint)
            var ssh: Session?=null
            fun valid()=generation.get()==ticket &&
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && MonitorStore(activity).settings()==config
            try {
                val client=JSch().apply { setHostKeyRepository(pin);addIdentity(PhoneIdentity(activity,true),null) }
                ssh=client.getSession(config.username,config.host,config.port).apply {
                    setConfig("StrictHostKeyChecking","yes");setConfig("PreferredAuthentications","publickey")
                    setConfig("PubkeyAcceptedAlgorithms",SshEncoding.ALGORITHM)
                    setConfig("ssh-ed25519","com.jcraft.jsch.bc.SignatureEd25519")
                    setConfig("compression.c2s","none");setConfig("compression.s2c","none")
                }
                session.set(ssh);check(valid());ssh.connect(5000);check(valid())
                val channel=ssh.openChannel("shell") as ChannelShell
                try {
                    channel.setPty(false);channel.setAgentForwarding(false);channel.setXForwarding(false);channel.setInputStream(null)
                    val input=channel.inputStream;val errors=channel.extInputStream;val output=channel.outputStream
                    channel.connect(5000);check(valid())
                    output.write((if(checkOnly) SshPowerCommands.AUTH_CHECK else if(action==PcPowerAction.Reboot) SshPowerCommands.REBOOT else SshPowerCommands.SHUTDOWN).toByteArray());output.flush()
                    val stdout=ByteArrayOutputStream();val stderr=ByteArrayOutputStream();val buffer=ByteArray(1024)
                    var authSent=false;var execAuthSent=false;var verified=false
                    val deadline=System.nanoTime()+30_000_000_000L
                    while(true) {
                        check(valid());check(System.nanoTime()<deadline)
                        for((stream,destination) in listOf(input to stdout,errors to stderr)) while(stream.available()>0) {
                            val count=stream.read(buffer,0,minOf(buffer.size,stream.available()))
                            if(count<0) break
                            require(destination.size()+count<=8192);destination.write(buffer,0,count)
                        }
                        // Raw remote stdout/stderr are never displayed or logged: PAM can contain sensitive text.
                        val err=stderr.toString("UTF-8");val text=stdout.toString("UTF-8")
                        fun occurrences(marker: String)=err.windowed(marker.length).count { it==marker }
                        val authCount=occurrences(SshPowerCommands.PASSWORD_PROMPT)
                        val execCount=occurrences(SshPowerCommands.EXEC_PROMPT)
                        if(authCount>1 || execCount>1) error("Authentication rejected; no retries")
                        if(authCount==1 && !authSent) { check(valid());output.write(secret);output.flush();authSent=true }
                        if(text.lineSequence().any { it.trim()==SshPowerCommands.REFUSED }) { refused=true;error("PC refused authentication or action") }
                        if(text.lineSequence().any { it.trim()==SshPowerCommands.VERIFIED } && !verified) {
                            check(authSent) // Fail closed if PC policy did not actually ask for authentication.
                            verified=true
                            if(!checkOnly) {
                                check(valid());executionRequested=true
                                output.write((SshPowerCommands.EXECUTE+"\n").toByteArray());output.flush()
                            }
                        }
                        if(execCount==1 && !execAuthSent) {
                            check(verified && executionRequested && valid());output.write(secret);output.flush();execAuthSent=true
                        }
                        if(channel.isClosed && input.available()==0 && errors.available()==0) {
                            check(channel.exitStatus==0 && verified)
                            if(!checkOnly) check(text.lineSequence().any { it.trim()==SshPowerCommands.ACCEPTED })
                            if(generation.get()==ticket) state.value=PowerUi(message=if(checkOnly)
                                "PC administrator authentication verified. No shutdown requested."
                                else "Power request accepted by the PC. Remote access will end; disconnection alone does not confirm completion.")
                            break
                        }
                        Thread.sleep(20)
                    }
                } finally { channel.disconnect() }
            } catch(_: Exception) {
                if(generation.get()==ticket) state.value=PowerUi(message=when {
                    pin.mismatch -> "Host identity mismatch. No password or power request sent."
                    refused && !executionRequested -> "PC administrator authentication was refused. No shutdown requested."
                    refused -> "The PC refused shutdown. Check existing sudo rights or shutdown inhibitors. No automatic retry."
                    executionRequested -> "Shutdown was refused or its result could not be confirmed. Check the PC before trying again. No automatic retry."
                    else -> "PC authentication was not completed. Check the account password, existing sudo rights and SSH access. No shutdown requested."
                })
            } finally { secret.fill(0);ssh?.disconnect();session.compareAndSet(ssh,null) }
        }
    }
}
