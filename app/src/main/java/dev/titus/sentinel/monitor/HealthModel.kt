package dev.titus.sentinel.monitor

import org.json.JSONObject
import org.json.JSONArray

/** Bounded, validated health response. Null sensors stay unavailable, never zero. */
data class HealthSnapshot(val data: JSONObject) {
    val host: String get() = data.getString("host")
}
internal fun decodeHealth(raw: String): HealthSnapshot {
    require(raw.toByteArray().size <= 131072)
    val j=JSONObject(raw)
    require(j.getInt("protocol")==1 && j.getString("kind")=="health")
    require(j.getString("host").length in 1..64)
    java.time.Instant.parse(j.getString("sampledAt"))
    require(j.getDouble("sampleSeconds") in 0.001..30.0)
    require(j.getDouble("uptimeSeconds").isFinite() && j.getDouble("uptimeSeconds")>=0)
    fun validate(value: Any?, key: String="", depth: Int=0) {
        require(depth<=8)
        when(value) {
            is JSONObject -> value.keys().forEach { validate(value.get(it),it,depth+1) }
            is JSONArray -> { require(value.length()<=64); for(i in 0 until value.length()) validate(value.get(i),key,depth+1) }
            is Number -> { val n=value.toDouble(); require(n.isFinite()); if(key.endsWith("Percent")) require(n in 0.0..100.0); if(key.contains("Bytes")) require(n>=0) }
            is String -> require(value.length<=512 && value.none { it.isISOControl() })
        }
    }
    validate(j)
    j.getJSONObject("cpu"); j.getJSONObject("memory")
    listOf("volumes","networks","disks","temperatures","fans","componentPower","gpus","warnings").forEach { j.getJSONArray(it) }
    return HealthSnapshot(j)
}
data class HealthUi(val phase: Phase=Phase.Unknown,val message: String="No health sample received yet.",
    val snapshot: HealthSnapshot?=null,val lastContactMillis: Long?=null,val receivedElapsed: Long?=null,val busy: Boolean=false) {
    fun aged(now: Long): HealthUi = if(phase==Phase.Online && (receivedElapsed==null || now-receivedElapsed !in 0..20_000))
        copy(phase=Phase.Stale,message="This health sample is stale. Refresh for current readings.") else this
}
