package dev.titus.sentinel.monitor

internal fun isServiceUnit(value: String)=value.matches(Regex("""[A-Za-z0-9_.@:-]{1,160}\.service"""))
internal fun validServiceRequest(command: String): Boolean {
    val parts=command.split(' ')
    return parts.size==3 && parts[0]=="sentinel-service-v1" && parts[1] in setOf("user","system") && isServiceUnit(parts[2])
}
