package dev.titus.sentinel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.titus.sentinel.monitor.*
import org.json.JSONObject
import org.json.JSONArray
import java.util.Locale

private fun JSONObject.number(key: String): Double? = if(isNull(key)) null else getDouble(key)
private fun Double?.display(unit: String=""): String = this?.let { String.format(Locale.US,"%.1f%s",it,unit) } ?: "Unavailable"
private fun Double?.bytes(): String = this?.let {
    when { it>=1099511627776.0 -> (it/1099511627776.0).display(" TiB"); it>=1073741824.0 -> (it/1073741824.0).display(" GiB"); else -> (it/1048576.0).display(" MiB") }
} ?: "Unavailable"
private fun Double?.rate(): String = this?.let {
    when { it>=1048576.0 -> (it/1048576.0).display(" MiB/s"); it>=1024.0 -> (it/1024.0).display(" KiB/s"); else -> it.display(" B/s") }
} ?: "—"
private fun JSONArray.rows(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun percent(used: Double?,total: Double?): Double? = if(used!=null && total!=null && total>0) (100*used/total).coerceIn(0.0,100.0) else null
@Composable internal fun HealthPage(ui: HealthUi, details: Boolean, configure: (() -> Unit)?=null, refresh: () -> Unit) {
    SampleBar(ui.phase,ui.busy,ui.lastContactMillis,refresh,configure)
    val j=ui.snapshot?.data ?: run {
        SurfaceCard { Note(statusMessage(ui.phase).ifEmpty { "Waiting for health readings…" }) }
        return
    }
    if(!details) {
        val warnings=j.getJSONArray("warnings")
        if(warnings.length()>0) {
            SurfaceCard {
                Eyebrow(if(ui.phase==Phase.Online) "HEALTH NOTICES" else "LAST KNOWN NOTICES")
                Spacer(Modifier.height(8.dp))
                for(i in 0 until warnings.length()) Note(warnings.getString(i))
            }
            Spacer(Modifier.height(10.dp))
        }
        val cpu=j.getJSONObject("cpu"); val memory=j.getJSONObject("memory")
        val busy=cpu.number("utilisationPercent")
        Metric("CPU",cpu.getString("name").replace("(R)","").replace("(TM)",""),busy?.toInt(),busy?.display("%") ?: "Unavailable","Temperature · ${cpu.number("temperatureC").display("°C")}")
        val ram=percent(memory.number("usedBytes"),memory.number("totalBytes"))
        Metric("RAM","${memory.number("totalBytes").bytes()} usable",ram?.toInt(),ram.display("%"),"${memory.number("usedBytes").bytes()} used · ${memory.number("availableBytes").bytes()} available")
        j.getJSONArray("volumes").rows().forEach { v ->
            val p=percent(v.number("usedBytes"),v.number("totalBytes"))
            Metric(v.getString("label"),v.getString("path")+" · "+v.number("totalBytes").bytes(),p?.toInt(),p?.display("%") ?: "Unavailable",if(v.getBoolean("mounted")) "${v.number("usedBytes").bytes()} used · ${v.number("availableBytes").bytes()} available" else "Not mounted")
        }
        j.getJSONArray("gpus").rows().forEach { gpu ->
            val p=gpu.number("utilisationPercent")
            Metric("GPU",gpu.getString("name"),p?.toInt(),p?.display("%") ?: "Unavailable",if(p==null && gpu.number("temperatureC")==null && gpu.number("vramUsedBytes")==null) "Sensors unavailable" else "${gpu.number("temperatureC").display("°C")} · VRAM ${gpu.number("vramUsedBytes").bytes()}")
        }
        SurfaceCard {
            Eyebrow("NETWORK · ↓ / ↑")
            val networks=j.getJSONArray("networks").rows().filter { it.getBoolean("physical") }
            if(networks.isEmpty()) Note("Network readings unavailable")
            networks.forEach { n -> PairLine(n.getString("name")+" · "+n.getString("state"),"${n.number("rxBytesPerSecond").rate()} / ${n.number("txBytesPerSecond").rate()}") }
        }
        Spacer(Modifier.height(10.dp))
        SurfaceCard {
            Eyebrow("SWAP & PRESSURE")
            PairLine("Swap used / total","${memory.number("swapUsedBytes").bytes()} / ${memory.number("swapTotalBytes").bytes()}")
            PairLine("Memory stalls · 10 s",memory.number("pressureSomeAvg10").display("%"))
            PairLine("CPU I/O wait",cpu.number("iowaitPercent").display("%"))
        }
    } else {
        SurfaceCard {
            Eyebrow("SYSTEM")
            PairLine("Operating system",j.getString("osName")); PairLine("Kernel",j.getString("kernel"))
            PairLine("Uptime",readableUptime(j.getDouble("uptimeSeconds")))
            val load=j.getJSONArray("load")
            PairLine("Load · 1 / 5 / 15 min",(0 until load.length()).joinToString(" / ") { load.getDouble(it).display() })
            PairLine("Logical CPUs",j.getJSONObject("cpu").optString("logicalCores","Unavailable"))
        }
        Spacer(Modifier.height(10.dp))
        SurfaceCard {
            Eyebrow("NETWORK · ↓ / ↑")
            j.getJSONArray("networks").rows().forEach { n -> PairLine(n.getString("name")+" · "+n.getString("state"),"${n.number("rxBytesPerSecond").rate()} / ${n.number("txBytesPerSecond").rate()}") }
        }
        Spacer(Modifier.height(10.dp))
        SurfaceCard {
            Eyebrow("DISKS · READ / WRITE")
            j.getJSONArray("disks").rows().forEach { d -> PairLine(d.getString("name")+" · "+d.getString("model"),"${d.number("readBytesPerSecond").rate()} / ${d.number("writeBytesPerSecond").rate()}") }
            PairLine("Drive health (SMART)","Unavailable")
        }
        Spacer(Modifier.height(10.dp))
        Disclosure("Hardware sensors") {
            listOf("temperatures" to "°C","fans" to " RPM","componentPower" to " W").forEach { (key,unit) ->
                val rows=j.getJSONArray(key).rows()
                if(rows.isEmpty()) PairLine(when(key) { "fans" -> "Fan speed"; "componentPower" -> "Component power"; else -> "Temperature" },"Unavailable")
                rows.forEach { PairLine(it.getString("label"),it.number("value").display(unit)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        SurfaceCard {
            Eyebrow("FAILED SERVICES")
            listOf("failedSystemServices" to "System","failedUserServices" to "Current user").forEach { (key,label) ->
                val services=j.optJSONObject(key)
                PairLine(label,services?.getInt("count")?.toString() ?: "Unavailable")

            }
        }
        val hasFailures=listOf("failedSystemServices","failedUserServices").any { (j.optJSONObject(it)?.optInt("count") ?: 0)>0 }
        if(hasFailures) {
            Spacer(Modifier.height(10.dp))
            Disclosure("View failed services") {
                listOf("failedSystemServices","failedUserServices").forEach { key ->
                    j.optJSONObject(key)?.getJSONArray("names")?.let { names ->
                        for(i in 0 until names.length()) { Note(names.getString(i)); Spacer(Modifier.height(6.dp)) }
                    }
                }
            }
        }

    }
    Spacer(Modifier.height(12.dp))
    Disclosure("About these readings") {
        Note("Refreshes every 10 seconds while open. CPU and transfer rates use a short sample; brief peaks may differ from desktop monitors.")
        Spacer(Modifier.height(8.dp))
        Note("RAM used excludes reclaimable memory. Swap use alone is not a fault. Network interfaces are measured separately.")
        Spacer(Modifier.height(8.dp))
        Note("Unavailable sensors are not exposed by the current driver. Slow motherboard firmware sensors are excluded from live polling. Component power is not whole-PC power.")
    }
}
