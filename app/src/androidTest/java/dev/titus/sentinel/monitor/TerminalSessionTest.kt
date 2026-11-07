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

/** Invoke only after the owner approves the separate interactive key. */
@RunWith(AndroidJUnit4::class)
class TerminalSessionTest {
    @Test fun interactiveShellAcceptsInputInterruptsOnlyItsOwnJobAndDisconnects() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val vm=TerminalViewModel(context.applicationContext as Application)
        val store=ViewModelStore().apply { put("terminal",vm) }
        try {
            withTimeout(5000) { vm.publicKey.first { it.isNotBlank() } }
            vm.connect(MonitorStore(context).settings())
            val connected=withTimeout(12000) { vm.ui.first { !it.connecting } }
            assertTrue(connected.message,connected.connected)
            vm.send("HISTFILE=/dev/null; printf '\\nSENTINEL_%s\\n' 'TERMINAL_OK'\r")
            withTimeout(8000) { vm.ui.first { it.output.contains("SENTINEL_TERMINAL_OK") } }
            vm.send("sleep 30\r");delay(500);vm.send("\u0003")
            vm.send("printf '\\nSENTINEL_%s\\n' 'INTERRUPT_OK'\r")
            withTimeout(8000) { vm.ui.first { it.output.contains("SENTINEL_INTERRUPT_OK") } }
            vm.send("exit\r")
            withTimeout(8000) { vm.ui.first { !it.connected } }
            delay(300);assertFalse(vm.ui.value.connected)
        } finally { store.clear() }
    }
}
