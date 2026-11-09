package dev.titus.sentinel.monitor

import android.content.Context

/** Global test controls; mute scopes are hashes, never plaintext connection/task identities. */
internal class AlertPreferences(context: Context,namespace: String="sentinel-alert-policy") {
    private val prefs=context.getSharedPreferences(namespace,Context.MODE_PRIVATE)
    fun read()=AlertPolicy(prefs.getBoolean("quiet",false),prefs.getInt("start",22).coerceIn(0,23),
        prefs.getInt("end",8).coerceIn(0,23),prefs.getInt("severity",1).coerceIn(1,3),
        prefs.getInt("cooldown",60).coerceIn(0,3600),prefs.getBoolean("health",false),prefs.getBoolean("audioMuted",false))
    fun save(value: AlertPolicy) {
        check(prefs.edit().putBoolean("quiet",value.quiet).putInt("start",value.startHour).putInt("end",value.endHour)
            .putInt("severity",value.minimumSeverity).putInt("cooldown",value.cooldownSeconds).putBoolean("health",value.health).putBoolean("audioMuted",value.muted).commit())
    }
    fun muted(scope: String)=TaskNotifications.digest(scope) in prefs.getStringSet("muted",emptySet())!!
    fun setMuted(scope: String,value: Boolean) {
        val keys=prefs.getStringSet("muted",emptySet())!!.toMutableSet()
        val key=TaskNotifications.digest(scope)
        if(value) { require(keys.size<256 || key in keys) { "256 mute rules already saved" };keys.add(key) } else keys.remove(key)
        check(prefs.edit().putStringSet("muted",keys).commit())
    }
}
