package dev.titus.sentinel.monitor

import org.json.JSONArray
import org.json.JSONObject

/** No exit status is available for arbitrary processes observed through /proc. */
data class TaskWatch(val identity: String, val name: String, val state: String = "unavailable",
    val observedMillis: Long = 0, val events: List<String> = emptyList()) {
    val terminal get() = state == "ended_unknown"
    val label get() = when(state) {
        "running" -> "Running at last observation"
        "ended_unknown" -> "Ended · outcome unknown"
        else -> "Disconnected or observation unavailable"
    }
    fun observe(next: String, now: Long): TaskWatch {
        require(next in setOf("running", "ended_unknown", "unavailable"))
        if(terminal) return this
        return copy(state=next, observedMillis=if(next=="unavailable") observedMillis else now,
            events=if(next==state) events else (events + "$now: $next").takeLast(20))
    }
    fun encode() = JSONObject().put("identity",identity).put("name",name).put("state",state)
        .put("observedMillis",observedMillis).put("events",JSONArray(events))
    companion object {
        fun decode(j: JSONObject): TaskWatch {
            val identity=j.getString("identity")
            require(identity.matches(Regex("[1-9][0-9]{0,9}:[0-9]{1,20}:[0-9a-f-]{36}")))
            val events=j.getJSONArray("events")
            require(events.length()<=20)
            return TaskWatch(identity,j.getString("name").take(32),j.getString("state"),j.getLong("observedMillis"),
                (0 until events.length()).map { events.getString(it).take(100) })
        }
    }
}
internal fun watchHost(config: ConnectionSettings) = "${config.host.lowercase()}:${config.port}:${config.username}:${config.fingerprint}"
internal fun decodeWatchResult(raw: String, requested: List<TaskWatch>, now: Long): List<TaskWatch> {
    require(raw.toByteArray().size<=32768)
    val root=JSONObject(raw)
    require(root.getInt("protocol")==1 && root.getString("kind")=="watches")
    java.time.Instant.parse(root.getString("sampledAt"))
    val rows=root.getJSONArray("watches")
    require(rows.length()==requested.size)
    val states=(0 until rows.length()).map { rows.getJSONObject(it) }
    require(states.map { it.getString("identity") }.toSet()==requested.map { it.identity }.toSet())
    require(states.map { it.getString("identity") }.distinct().size==states.size)
    return requested.map { watch -> watch.observe(states.first { it.getString("identity")==watch.identity }.getString("state"),now) }
}
