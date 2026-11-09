package dev.titus.sentinel.monitor

internal data class HealthReading(val key: String,val label: String,val value: Double?,val trigger: Double,val recovery: Double,val low: Boolean=false) {
    fun breached()=value?.let { if(low) it<trigger else it>=trigger } ?: false
    fun recovered()=value?.let { if(low) it>=recovery else it<recovery } ?: false
}
internal data class HealthTransition(val key: String,val label: String,val recovered: Boolean,val episode: Long)
/** Contiguous observations only. Missing sensors and gaps never count as recovery or dwell time. */
internal class SustainedHealth(private val dwellMillis: Long=60_000,private val maxGapMillis: Long=90_000) {
    private data class State(var active: Boolean=false,var since: Long?=null,var last: Long?=null,var episode: Long=0)
    private val states=mutableMapOf<String,State>()
    var summary="Health warnings are waiting for samples."
        private set
    fun observe(readings: List<HealthReading>,elapsed: Long): List<HealthTransition> {
        require(readings.size<=64 && readings.map { it.key }.distinct().size==readings.size)
        val events=mutableListOf<HealthTransition>()
        val keys=readings.map { it.key }.toSet()
        var unavailable=states.keys.count { it !in keys }
        states.keys.filter { it !in keys }.forEach { states[it]?.apply { since=null;last=null } }
        readings.forEach { reading ->
            val state=states.getOrPut(reading.key) { State() }
            if(reading.value==null || !reading.value.isFinite()) { unavailable++;state.since=null;state.last=null;return@forEach }
            if(state.last==null || elapsed-state.last!! !in 0..maxGapMillis) state.since=null
            state.last=elapsed
            val candidate=if(state.active) reading.recovered() else reading.breached()
            if(!candidate) state.since=null
            else {
                if(state.since==null) state.since=elapsed
                if(elapsed-state.since!!>=dwellMillis) {
                    state.active=!state.active
                    if(state.active) state.episode=elapsed
                    events.add(HealthTransition(reading.key,reading.label,!state.active,state.episode))
                    state.since=null
                }
            }
        }
        // Disappeared volumes/sensors stay unknown, not falsely recovered; bound retained identities.
        if(states.size>128) states.keys.filter { it !in keys }.take(states.size-128).forEach { states.remove(it) }
        summary="${states.values.count { it.active }} last-known active health warning(s); $unavailable unavailable sensor(s). Threshold and recovery each require 60 seconds of observations."
        return events
    }
    fun interrupted() { states.values.forEach { it.since=null;it.last=null };summary="Health observation interrupted; no recovery can be confirmed." }
}
