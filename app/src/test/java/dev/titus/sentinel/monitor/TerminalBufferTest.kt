package dev.titus.sentinel.monitor
import org.junit.Assert.*
import org.junit.Test
class TerminalBufferTest {
    @Test fun splitAnsiAndOscCannotExecuteOrLeakControlPayloads() {
        val b=TerminalBuffer()
        b.append("\u001b[3");b.append("1mhello\u001b[0m\r\n")
        b.append("\u001b]52;c;secret");b.append("\u0007world")
        assertEquals("hello\nworld",b.append(""))
    }
    @Test fun carriageReturnAndBackspaceOverwriteInsteadOfLosingThePrompt() {
        val b=TerminalBuffer()
        assertEquals("200%",b.append("100%\r200%"))
        assertEquals("200!",b.append("\b!"))
        assertEquals("200!\n$ ",b.append("\r\n$ "))
    }
    @Test fun boundedOutputAndExplicitClear() {
        val b=TerminalBuffer(100)
        assertEquals(100,b.append("x".repeat(5000)).length)
        b.clear();assertEquals("",b.append(""))
        assertEquals("new",b.append("old\u001b[2Jnew"))
    }
}
