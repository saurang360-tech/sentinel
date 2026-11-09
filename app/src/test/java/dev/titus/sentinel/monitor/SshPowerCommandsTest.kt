package dev.titus.sentinel.monitor

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class SshPowerCommandsTest {
    private fun run(script: String,password: String="correct",execute: Boolean=true,refusePower: Boolean=false): Triple<Int,String,Boolean> {
        val dir=Files.createTempDirectory("sentinel-power-test").toFile()
        try {
            val called=java.io.File(dir,"called")
            val sudo=java.io.File(dir,"sudo").apply { writeText("""
                |#!/bin/sh
                |prompt=''
                |validate=false
                |while [ "${'$'}#" -gt 0 ]; do
                | case "${'$'}1" in -p) shift; prompt=${'$'}1;; -v) validate=true;; --) shift; break;; esac
                | shift
                |done
                |printf '%s' "${'$'}prompt" >&2
                |IFS= read -r password || exit 1
                |[ "${'$'}password" = correct ] || exit 1
                |if [ "${'$'}validate" = false ]; then
                | printf '%s' "${'$'}*" > "${'$'}CALLED"
                | exit "${'$'}POWER_EXIT"
                |fi
                |exit 0
                |""".trimMargin()) }
            check(sudo.setExecutable(true))
            val process=ProcessBuilder("/bin/sh").redirectErrorStream(true).apply {
                environment()["PATH"]=dir.absolutePath // No real sudo/systemctl can be reached.
                environment()["CALLED"]=called.absolutePath
                environment()["POWER_EXIT"]=if(refusePower) "1" else "0"
            }.start()
            val output=process.outputStream
            output.write(script.toByteArray());output.flush()
            val captured=StringBuilder()
            val reader=Thread {
                try {
                    val input=process.inputStream.reader()
                    while(true) {
                        val value=input.read();if(value<0) break
                        captured.append(value.toChar())
                        if(captured.endsWith(SshPowerCommands.PASSWORD_PROMPT) || captured.endsWith(SshPowerCommands.EXEC_PROMPT)) {
                            output.write((password+"\n").toByteArray());output.flush()
                        }
                        if(captured.endsWith(SshPowerCommands.VERIFIED+"\n")) {
                            if(execute && script in listOf(SshPowerCommands.SHUTDOWN,SshPowerCommands.REBOOT)) {
                                output.write((SshPowerCommands.EXECUTE+"\n").toByteArray());output.flush()
                            } else output.close()
                        }
                    }
                } catch(_: Exception) { /* Closing stdin intentionally aborts an unconfirmed request. */ }
            }.apply { start() }
            if(!process.waitFor(5,TimeUnit.SECONDS)) { process.destroyForcibly();error("Fixed script failed to terminate") }
            reader.join(1000)
            if(called.exists()) assertEquals("/usr/bin/systemctl --check-inhibitors=yes "+(if(script==SshPowerCommands.REBOOT) "reboot" else "poweroff"),called.readText())
            return Triple(process.exitValue(),captured.toString(),called.exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun authenticationCheckCannotPowerOff() {
        val result=run(SshPowerCommands.AUTH_CHECK)
        assertEquals(0,result.first);assertTrue(result.second.contains(SshPowerCommands.VERIFIED));assertFalse(result.third)
    }
    @Test fun badPasswordAndMissingConfirmationCannotPowerOff() {
        val rejected=run(SshPowerCommands.SHUTDOWN,password="wrong")
        assertNotEquals(0,rejected.first);assertFalse(rejected.third)
        val cancelled=run(SshPowerCommands.SHUTDOWN,execute=false)
        assertNotEquals(0,cancelled.first);assertFalse(cancelled.third)
    }
    @Test fun acceptedCommandHonoursRefusalAndKeepsPasswordOutOfScript() {
        val accepted=run(SshPowerCommands.SHUTDOWN)
        assertEquals(0,accepted.first);assertTrue(accepted.second.contains(SshPowerCommands.ACCEPTED));assertTrue(accepted.third)
        val inhibited=run(SshPowerCommands.SHUTDOWN,refusePower=true)
        assertNotEquals(0,inhibited.first);assertFalse(inhibited.second.contains(SshPowerCommands.ACCEPTED))
        assertFalse(SshPowerCommands.SHUTDOWN.contains("correct"))
    }
    @Test fun rebootUsesSameAuthenticationAndInhibitorGuard() {
        val accepted=run(SshPowerCommands.REBOOT)
        assertEquals(0,accepted.first);assertTrue(accepted.third)
        assertFalse(run(SshPowerCommands.REBOOT,password="wrong").third)
        assertFalse(run(SshPowerCommands.REBOOT,execute=false).third)
    }

}
