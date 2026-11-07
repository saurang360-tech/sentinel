package dev.titus.sentinel.monitor

import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.io.ByteArrayInputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class StatusModelTest {
    @Test fun staleAgeUsesMonotonicTimeAndNeverRefreshesByItself() {
        val state=MonitorUi(Phase.Online,receivedElapsed=1000)
        assertEquals(Phase.Online,state.aged(21000).phase)
        assertEquals(Phase.Stale,state.aged(21001).phase)
        assertEquals(Phase.Stale,state.aged(500).phase)
        assertEquals(Phase.Stale,state.aged(25000).aged(1001).phase)
    }
    @Test fun failureCannotBecomeOnlineBecauseOfCachedTime() {
        val state=MonitorUi(Phase.Unreachable,receivedElapsed=1000,lastContactMillis=9999999999)
        assertEquals(Phase.Unreachable,state.aged(1001).phase)
    }
    @Test fun invalidConnectionSettingsAreRejected() {
        val valid=ConnectionSettings("workstation.tailnet.ts.net",22,"alice",("SHA256:" + "B".repeat(43)))
        assertNotNull(ConnectionSettings().validationError())
        listOf("workstation","workstation.tailnet.ts.net","192.168.1.2","100.75.2.3","fd7a:115c:a1e0::1").forEach {
            assertNull("Valid host $it",valid.copy(host=it).validationError())
        }
        listOf("","https://pc","pc;id","pc name","1.2.3.999","-pc.local","pc:22","pc/path").forEach {
            assertNotNull("Invalid host $it",valid.copy(host=it).validationError())
        }
        assertNotNull(valid.copy(port=0).validationError())
        assertNotNull(valid.copy(username="alice;id").validationError())
        listOf("alex.smith","alex@example.org","1001","workstation$").forEach {
            assertNull(valid.copy(username=it).validationError())
        }
        assertNotNull(valid.copy(fingerprint="SHA256:wrong").validationError())
    }

    @Test fun hostPinMismatchFailsClosed() {
        val key=byteArrayOf(1,2,3)
        assertTrue(SshEncoding.matchesPin(key,SshEncoding.fingerprint(key)))
        assertFalse(SshEncoding.matchesPin(key,("SHA256:" + "B".repeat(43))))
    }
    @Test fun keystoreSignatureFormatMatchesSshEcdsaWireFormat() {
        val key=KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val data="Sentinel status proof".toByteArray()
        val der=Signature.getInstance("SHA256withECDSA").run { initSign(key.private); update(data); sign() }
        val ssh=SshEncoding.signatureFromDer(der)
        fun DataInputStream.string(): ByteArray { val n=readInt();require(n in 0..1024);return ByteArray(n).also { readFully(it) } }
        val outer=DataInputStream(ByteArrayInputStream(ssh))
        assertEquals(SshEncoding.ALGORITHM,String(outer.string()))
        val inner=DataInputStream(ByteArrayInputStream(outer.string()))
        val r=inner.string();val s=inner.string()
        val rebuilt=byteArrayOf(0x30,(4+r.size+s.size).toByte(),2,r.size.toByte())+r+byteArrayOf(2,s.size.toByte())+s
        assertTrue(Signature.getInstance("SHA256withECDSA").run { initVerify(key.public);update(data);verify(rebuilt) })
        val blob=DataInputStream(ByteArrayInputStream(SshEncoding.publicBlob(key.public as ECPublicKey)))
        assertEquals(SshEncoding.ALGORITHM,String(blob.string()))
        assertEquals("nistp256",String(blob.string()));assertEquals(65,blob.string().size)
    }
    @Test fun malformedSignaturesAndSnapshotsAreRejected() {
        for(raw in listOf(byteArrayOf(),byteArrayOf(0x30,0),byteArrayOf(0x30,2,2,0))) {
            assertTrue(runCatching { SshEncoding.signatureFromDer(raw) }.isFailure)
        }
        assertTrue(runCatching { Snapshot("Titus","invalid",0.0,"running","invalid").validate() }.isFailure)
        assertTrue(runCatching { Snapshot("Titus","123e4567-e89b-12d3-a456-426614174000",Double.NaN,"running","2026-10-27T00:00:00Z").validate() }.isFailure)
    }
}
