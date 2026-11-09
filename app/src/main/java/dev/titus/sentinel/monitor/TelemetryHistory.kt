package dev.titus.sentinel.monitor

import org.json.JSONObject

internal data class TelemetryPoint(val time: Long,val elapsed: Long,val values: Map<String,Double?>)
internal fun telemetry(snapshot: HealthSnapshot,time: Long,elapsed: Long): TelemetryPoint {
    val j=snapshot.data;val values=linkedMapOf<String,Double?>()
    fun number(o: JSONObject?,key: String)=if(o==null || o.isNull(key)) null else o.optDouble(key).takeIf { it.isFinite() }
    val cpu=j.optJSONObject("cpu");val memory=j.optJSONObject("memory")
    values["cpu"]=number(cpu,"utilisationPercent");values["temperature"]=number(cpu,"temperatureC")
    values["thermalLimit"]=number(cpu,"temperatureCriticalC")
    val total=number(memory,"totalBytes");val used=number(memory,"usedBytes")
    values["ram"]=if(total!=null && total>0 && used!=null) 100*used/total else null
    for((array,a,b) in listOf(Triple("networks","rxBytesPerSecond","txBytesPerSecond"),Triple("disks","readBytesPerSecond","writeBytesPerSecond"))) {
        val rows=j.optJSONArray(array);val first=mutableListOf<Double>();val second=mutableListOf<Double>()
        if(rows!=null) for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            if(array=="networks" && !row.optBoolean("physical")) continue
            number(row,a)?.let { first.add(it) };number(row,b)?.let { second.add(it) }
        }
        values["$array:a"]=first.takeIf { it.isNotEmpty() }?.sum();values["$array:b"]=second.takeIf { it.isNotEmpty() }?.sum()
    }
    return TelemetryPoint(time,elapsed,values)
}
