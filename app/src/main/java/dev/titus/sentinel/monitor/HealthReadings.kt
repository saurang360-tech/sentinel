package dev.titus.sentinel.monitor

import org.json.JSONObject

private fun JSONObject.number(key: String): Double? = if(isNull(key) || !has(key)) null else optDouble(key).takeIf { it.isFinite() }
internal fun healthReadings(snapshot: HealthSnapshot): List<HealthReading> {
    val j=snapshot.data
    val memory=j.getJSONObject("memory")
    fun percent(available: Double?,total: Double?): Double? = if(available!=null && total!=null && total>0 && available in 0.0..total) 100*available/total else null
    val cpu=j.getJSONObject("cpu")
    val critical=cpu.number("temperatureCriticalC")?.takeIf { it in 1.0..200.0 }
    val readings=mutableListOf(
        HealthReading("memory-available","Available RAM below 10%",percent(memory.number("availableBytes"),memory.number("totalBytes")),10.0,15.0,true),
        HealthReading("memory-pressure","Memory pressure stalls at or above 1%",memory.number("pressureSomeAvg10")?.takeIf { it in 0.0..100.0 },1.0,0.5),
        HealthReading("cpu-temperature","CPU within 10°C of its hardware critical limit",if(critical==null) null else cpu.number("temperatureC")?.takeIf { it in -100.0..200.0 },
            (critical ?: 0.0)-10,(critical ?: 0.0)-15))
    val volumes=j.getJSONArray("volumes")
    for(i in 0 until minOf(volumes.length(),61)) {
        val volume=volumes.getJSONObject(i)
        readings.add(HealthReading("volume:"+volume.getString("path"),volume.getString("label")+" available space below 10%",
            if(volume.optBoolean("mounted")) percent(volume.number("availableBytes"),volume.number("totalBytes")) else null,10.0,15.0,true))
    }
    return readings.distinctBy { it.key }
}
