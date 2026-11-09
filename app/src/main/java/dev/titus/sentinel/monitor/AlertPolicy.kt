package dev.titus.sentinel.monitor

/** Daily quiet hours are local phone hours; equal endpoints mean all day. */
internal data class AlertPolicy(val quiet: Boolean=false,val startHour: Int=22,val endHour: Int=8,
    val minimumSeverity: Int=1,val cooldownSeconds: Int=60,val health: Boolean=false,val muted: Boolean=false) {
    init {
        require(startHour in 0..23 && endHour in 0..23)
        require(minimumSeverity in 1..3 && cooldownSeconds in 0..3600)
    }
    fun quietAt(hour: Int): Boolean {
        require(hour in 0..23)
        return quiet && when {
            startHour==endHour -> true
            startHour<endHour -> hour>=startHour && hour<endHour
            else -> hour>=startHour || hour<endHour
        }
    }
    fun canHandoff(hour: Int,elapsed: Long,lastHandoff: Long?): Boolean = !quietAt(hour) &&
        (lastHandoff==null || elapsed-lastHandoff>=cooldownSeconds*1000L || elapsed<lastHandoff)
}
