package dev.titus.sentinel.monitor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalSecurityTest {
    @Test fun terminalAndReadOnlyKeysAreSeparateAndStable() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val status=PhoneIdentity(context);val terminal=PhoneIdentity(context,true)
        assertFalse(status.publicKeyBlob.contentEquals(terminal.publicKeyBlob))
        assertTrue(status.publicKeyBlob.contentEquals(PhoneIdentity(context).publicKeyBlob))
        assertTrue(terminal.publicKeyBlob.contentEquals(PhoneIdentity(context,true).publicKeyBlob))
        assertTrue(terminal.publicLine().endsWith(" sentinel-terminal"))
    }
    @Test fun terminalRejectsWrongHostPinWithoutOpeningAShell() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val vm=TerminalViewModel(context.applicationContext as Application)
        val store=ViewModelStore().apply { put("terminal",vm) }
        try {
            withTimeout(5000) { vm.publicKey.first { it.isNotBlank() } }
            vm.connect(MonitorStore(context).settings().copy(fingerprint="SHA256:"+"A".repeat(43)))
            val result=withTimeout(12000) { vm.ui.first { !it.connecting } }
            assertFalse(result.connected)
            assertEquals("Host key mismatch. Connection blocked.",result.message)
            assertTrue(result.output.isEmpty())
        } finally { store.clear() }
    }
}
