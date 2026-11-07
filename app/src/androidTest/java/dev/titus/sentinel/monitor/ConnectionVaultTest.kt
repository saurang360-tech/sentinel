package dev.titus.sentinel.monitor

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ConnectionVaultTest {
    private fun context(name: String): Context {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        return object: ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String,mode: Int)=base.getSharedPreferences(name,mode)
        }
    }
    @Test fun profilesPersistByHostAndLoginAndNeverAppearAsPlaintext() {
        val c=context("vault-regression");val prefs=c.getSharedPreferences("ignored",0)
        prefs.edit().clear().commit()
        try {
            val first=ConnectionSettings("example.invalid",22,"alice","SHA256:"+"B".repeat(43),"Workstation")
            val second=first.copy(username="bob")
            val third=first.copy(host="other.invalid")
            val store=MonitorStore(c)
            listOf(first,second,third).forEach { store.saveSettings(it) }
            assertEquals(3,MonitorStore(c).profiles().size)
            assertEquals(third,MonitorStore(c).settings())
            val alice=store.profiles().first { it.settings==first }
            assertEquals(first,store.select(alice.id))
            assertEquals(first,MonitorStore(c).settings())
            store.saveSettings(first.copy(deviceName="Renamed"))
            assertEquals(3,store.profiles().size)
            val raw=prefs.all.toString()
            listOf("example.invalid","alice","bob","Workstation","SHA256:").forEach { assertFalse(raw.contains(it)) }
            assertEquals(setOf("vault"),prefs.all.keys)
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun modifiedCiphertextFailsClosedAndCannotBeSilentlyReplaced() {
        val c=context("vault-tamper-regression");val prefs=c.getSharedPreferences("ignored",0)
        prefs.edit().clear().commit()
        try {
            val config=ConnectionSettings("example.invalid",22,"alice","SHA256:"+"B".repeat(43))
            MonitorStore(c).saveSettings(config)
            val envelope=org.json.JSONObject(prefs.getString("vault",null)!!)
            val data=java.util.Base64.getDecoder().decode(envelope.getString("data"));data[0]=(data[0].toInt() xor 1).toByte()
            envelope.put("data",java.util.Base64.getEncoder().encodeToString(data))
            val tampered=envelope.toString();prefs.edit().putString("vault",tampered).commit()
            val store=MonitorStore(c)
            assertNotNull(store.storageError)
            assertEquals("",store.settings().host)
            assertTrue(runCatching { store.saveSettings(config) }.isFailure)
            assertEquals(tampered,prefs.getString("vault",null))
        } finally { prefs.edit().clear().commit() }
    }
}
