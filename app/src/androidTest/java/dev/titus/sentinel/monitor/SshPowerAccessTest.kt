package dev.titus.sentinel.monitor

import androidx.test.platform.app.InstrumentationRegistry
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

/** Read-only capability query over the already authorised Terminal key. Never power off. */
class SshPowerAccessTest {
    @Test fun queryExistingRemoteShutdownPermissionWithoutChangingPC() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val config=MonitorStore(context).settings();assertNull(config.validationError())
        val pin=PinRepository(config.fingerprint)
        val client=JSch().apply { setHostKeyRepository(pin);addIdentity(PhoneIdentity(context,true),null) }
        val ssh=client.getSession(config.username,config.host,config.port)
        try {
            ssh.setConfig("StrictHostKeyChecking","yes");ssh.setConfig("PreferredAuthentications","publickey")
            ssh.setConfig("PubkeyAcceptedAlgorithms",SshEncoding.ALGORITHM)
            ssh.setConfig("ssh-ed25519","com.jcraft.jsch.bc.SignatureEd25519");ssh.connect(5000)
            val channel=ssh.openChannel("shell") as ChannelShell
            try {
                channel.setPty(false);channel.setAgentForwarding(false);channel.setXForwarding(false)
                channel.setInputStream(ByteArrayInputStream(SshPowerCommands.CHECK.toByteArray()))
                val input=channel.inputStream;channel.connect(3000)
                val bytes=java.io.ByteArrayOutputStream();val deadline=System.nanoTime()+10_000_000_000L
                while(!channel.isClosed || input.available()>0) {
                    while(input.available()>0) { bytes.write(input.read());require(bytes.size()<4096) }
                    check(System.nanoTime()<deadline);Thread.sleep(20)
                }
                val output=bytes.toString("UTF-8")
                val result=when {
                    output.lineSequence().any { it.trim()==SshPowerCommands.READY } -> "ready"
                    output.lineSequence().any { it.trim()==SshPowerCommands.CHALLENGE } -> "administrator-authentication-required"
                    else -> "unavailable"
                }
                println("SENTINEL_EXISTING_SSH_POWER_PERMISSION=$result")
                assertTrue("Unexpected SSH shell failure",channel.exitStatus in setOf(0,69,77))
            } finally { channel.disconnect() }
        } finally { ssh.disconnect() }
    }
}
