package dev.titus.sentinel.monitor

import java.util.UUID

data class ConnectionSettings(val host: String = "", val port: Int = 22,
    val username: String = "", val fingerprint: String = "", val deviceName: String = "") {
    fun validationError(): String? {
        val value=host.removeSurrounding("[","]")
        val validHost=when {
            value.isEmpty() || value.length>253 || value.any { it.isWhitespace() || it.isISOControl() } -> false
            ':' in value -> runCatching { java.net.InetAddress.getByName(value) is java.net.Inet6Address && '%' !in value }.getOrDefault(false)
            value.all { it.isDigit() || it=='.' } -> value.split('.').let { p -> p.size==4 && p.all { it.toIntOrNull() in 0..255 } }
            else -> value.trimEnd('.').split('.').all { it.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")) }
        }
        if(!validHost) return "Enter a hostname or IP address, without a port or URL."
        if(deviceName.length>64 || deviceName.any { it.isISOControl() }) return "Device name must be 64 characters or fewer."
        if(port !in 1..65535) return "SSH port must be between 1 and 65535."
        if(!username.matches(Regex("[a-zA-Z0-9_][a-zA-Z0-9_.@\\$-]{0,63}"))) return "Enter an SSH login of up to 64 letters, numbers, dots, underscores, @, $ or hyphens."
        if(!fingerprint.matches(Regex("SHA256:[A-Za-z0-9+/]{43}"))) return "Enter the verified SHA256 SSH host fingerprint."
        return null
    }
}
enum class Phase(val title: String) {
    Unknown("STATUS UNKNOWN"), Checking("CONNECTING"), Online("SYSTEM RESPONDING"),
    Unreachable("UNREACHABLE"), Stale("STALE DATA"), Pairing("SSH RESPONDING"),
    SecurityError("HOST KEY MISMATCH"), NoData("STATUS UNAVAILABLE")
}
data class Snapshot(val host: String, val bootId: String, val uptimeSeconds: Double, val systemState: String, val sampledAt: String, val users: List<PcUser> = emptyList()) {
    fun validate() {
        require(host.length in 1..64 && host.none { it.isISOControl() })
        require(UUID.fromString(bootId).toString() == bootId)
        require(uptimeSeconds.isFinite() && uptimeSeconds >= 0 && uptimeSeconds < 1e10)
        require(systemState in setOf("running","degraded","starting","initializing","maintenance","stopping","offline","unknown"))
        java.time.Instant.parse(sampledAt)
        require(users.size<=256 && users.map { it.uid }.distinct().size==users.size)
        users.forEach { it.validate() }
    }
}
data class MonitorUi(val phase: Phase = Phase.Unknown, val message: String = "No successful contact yet.",
    val snapshot: Snapshot? = null, val lastContactMillis: Long? = null, val receivedElapsed: Long? = null,
    val checkedMillis: Long? = null, val busy: Boolean = false) {
    fun aged(nowElapsed: Long): MonitorUi = if(phase == Phase.Online &&
        (receivedElapsed == null || nowElapsed - receivedElapsed !in 0..20_000))
        copy(phase = Phase.Stale,message = "Last response is no longer fresh. Power state is unknown.") else this
}
fun readableUptime(seconds: Double): String {
    if(seconds < 60) return "${seconds.toLong()} s"
    val minutes = seconds.toLong() / 60
    return if(minutes >= 1440) "${minutes/1440} d ${(minutes%1440)/60} h" else "${minutes/60} h ${minutes%60} min"
}

// Only authentication and host identity failures need owner intervention.
internal fun Phase.stopsAutomaticRetry(): Boolean = this == Phase.Pairing || this == Phase.SecurityError
