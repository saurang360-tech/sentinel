package dev.titus.sentinel.monitor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.OutputStream
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal data class TerminalUi(val connected: Boolean=false,val connecting: Boolean=false,val output: String="",val message: String="Disconnected")
internal class TerminalViewModel(application: Application): AndroidViewModel(application) {
    private val _ui=MutableStateFlow(TerminalUi());val ui=_ui.asStateFlow()
    private val _publicKey=MutableStateFlow("");val publicKey=_publicKey.asStateFlow()
    private var identity: PhoneIdentity?=null
    private val reader=Executors.newSingleThreadExecutor()
    private val writer=Executors.newSingleThreadExecutor()
    private val generation=AtomicInteger()
    private val session=AtomicReference<Session?>()
    private val channel=AtomicReference<ChannelShell?>()
    private val output=AtomicReference<OutputStream?>()
    private val buffer=TerminalBuffer()
    init {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { PhoneIdentity(application,true) } }
                .onSuccess { identity=it;_publicKey.value=it.publicLine() }
                .onFailure { _ui.value=_ui.value.copy(message="The terminal key could not be opened.") }
        }
    }
    fun connect(config: ConnectionSettings) {
        if(!AppAccess.canConnect(getApplication())) {
            _ui.value=_ui.value.copy(message="Review the privacy notice and allow PC connections first.");return
        }
        if(_ui.value.connecting || _ui.value.connected) return
        config.validationError()?.let { _ui.value=_ui.value.copy(message="Configure the connection in Settings first.");return }
        val key=identity ?: return
        disconnect()
        val ticket=generation.get()
        _ui.value=_ui.value.copy(connecting=true,message="Connecting…")
        reader.submit {
            val pin=PinRepository(config.fingerprint)
            var ssh: Session?=null
            var shell: ChannelShell?=null
            var authenticated=false
            fun current()=generation.get()==ticket
            fun update(message: String,connected: Boolean=false) {
                if(current()) _ui.value=_ui.value.copy(connecting=false,connected=connected,message=message)
            }
            try {
                val client=JSch().apply { setHostKeyRepository(pin);addIdentity(key,null) }
                ssh=client.getSession(config.username,config.host,config.port).apply {
                    setConfig("StrictHostKeyChecking","yes");setConfig("PreferredAuthentications","publickey")
                    setConfig("PubkeyAcceptedAlgorithms",SshEncoding.ALGORITHM)
                    setConfig("ssh-ed25519","com.jcraft.jsch.bc.SignatureEd25519")
                    setConfig("compression.c2s","none");setConfig("compression.s2c","none")
                    timeout=5000
                }
                if(!current()) return@submit
                session.set(ssh)
                ssh.connect(5000)
                if(!current()) return@submit
                authenticated=true;ssh.timeout=0
                shell=ssh.openChannel("shell") as ChannelShell
                shell.setPty(true);shell.setPtyType("dumb",80,24,0,0)
                shell.setAgentForwarding(false);shell.setXForwarding(false)
                shell.setInputStream(null)
                val input=InputStreamReader(shell.inputStream,Charsets.UTF_8)
                val stream=shell.outputStream
                channel.set(shell)
                shell.connect(3000)
                if(!current()) return@submit
                output.set(stream)
                update("Connected · ${config.username}@${config.host}",true)
                val chars=CharArray(2048)
                while(current()) {
                    val count=input.read(chars)
                    if(count<0) break
                    val rendered=synchronized(buffer) { buffer.append(String(chars,0,count)) }
                    if(current()) _ui.value=_ui.value.copy(output=rendered)
                }
                update("Session ended")
            } catch(_: Exception) {
                update(when {
                    pin.mismatch -> "Host key mismatch. Connection blocked."
                    pin.verified && !authenticated -> "Terminal key not authorised. See Settings → Terminal access."
                    authenticated -> "Terminal session interrupted. Reconnect to open a new shell."
                    else -> "Cannot reach SSH. Check your network and connection settings."
                })
            } finally {
                if(current()) { output.set(null);channel.set(null);session.set(null) }
                shell?.disconnect();ssh?.disconnect()
            }
        }
    }
    fun send(value: String) {
        if(!_ui.value.connected) return
        val ticket=generation.get();val stream=output.get() ?: return
        writer.submit {
            if(generation.get()!=ticket) return@submit
            try { stream.write(value.toByteArray(Charsets.UTF_8));stream.flush() }
            catch(_: Exception) { if(generation.get()==ticket) disconnect() }
        }
    }
    fun clear() { synchronized(buffer) { buffer.clear() };_ui.value=_ui.value.copy(output="") }
    fun disconnect() {
        generation.incrementAndGet();output.set(null);channel.getAndSet(null)?.disconnect();session.getAndSet(null)?.disconnect()
        _ui.value=_ui.value.copy(connected=false,connecting=false,message="Disconnected")
    }
    override fun onCleared() { disconnect();reader.shutdownNow();writer.shutdownNow() }
}
