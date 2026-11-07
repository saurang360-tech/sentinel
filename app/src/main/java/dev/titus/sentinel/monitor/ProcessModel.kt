package dev.titus.sentinel.monitor

import org.json.JSONObject

data class PcUser(val uid: Int,val name: String) {
    fun validate() { require(uid>=0 && name.length in 1..64 && name.none { it.isISOControl() }) }
}
enum class ProcessSort(val wire: String,val label: String) { CPU("cpu","CPU"), RAM("ram","RAM") }
data class ProcessQuery(val uid: Int?=null,val sort: ProcessSort=ProcessSort.CPU,val includeSystem: Boolean=false) {
    fun command(): String {
        require(uid==null || uid>=0)
        return "sentinel-processes-v2 ${uid ?: "all"} ${sort.wire} ${if(includeSystem) "all" else "user"}"
    }
}
internal fun decodeUsers(j: JSONObject): List<PcUser> {
    val values=j.optJSONArray("users") ?: return emptyList()
    require(values.length()<=256)
    return (0 until values.length()).map { values.getJSONObject(it).let { u -> PcUser(u.getInt("uid"),u.getString("name")).also { it.validate() } } }
        .also { users -> require(users.map { it.uid }.distinct().size==users.size) }
}
data class ProcessRow(val pid: Int, val name: String, val uid: Int, val owner: String, val state: String,
    val startTicks: Long, val cpuPercent: Double?, val rssBytes: Long?, val runtimeSeconds: Double) {
    fun validate() {
        require(pid > 0 && uid >= 0 && startTicks >= 0)
        require(name.length in 1..32 && name.none { it.isISOControl() })
        require(owner.length in 1..64 && owner.none { it.isISOControl() })
        require(state.length == 1 && state[0] in "RSDZTtXxKWPIN")
        require(cpuPercent == null || cpuPercent.isFinite() && cpuPercent in 0.0..100.0)
        require(rssBytes == null || rssBytes >= 0)
        require(runtimeSeconds.isFinite() && runtimeSeconds >= 0)
    }
    val stateLabel: String get() = when(state) {
        "R" -> "Running"; "S" -> "Sleeping"; "D" -> "Waiting for I/O"; "T","t" -> "Stopped";
        "Z" -> "Zombie"; "I" -> "Idle"; "X","x" -> "Exiting"; else -> "Kernel state $state"
    }
}
data class ProcessSnapshot(val host: String, val sampledAt: String, val query: ProcessQuery,
    val sampleSeconds: Double, val totalMatching: Int, val limited: Boolean, val scanLimited: Boolean,
    val denied: Int, val vanished: Int, val rows: List<ProcessRow>, val users: List<PcUser> = emptyList(), val serverSorted: Boolean = true) {
    fun validate() {
        require(host.length in 1..64 && host.none { it.isISOControl() })
        java.time.Instant.parse(sampledAt)
        require(sampleSeconds.isFinite() && sampleSeconds > 0 && sampleSeconds < 30)
        require(totalMatching >= rows.size && denied >= 0 && vanished >= 0 && rows.size <= 200)
        require(rows.map { it.pid }.distinct().size == rows.size)
        rows.forEach { it.validate() }
        if(query.uid != null) require(rows.all { it.uid == query.uid })
        users.forEach { it.validate() }
        require(users.map { it.uid }.distinct().size==users.size)
        if(!query.includeSystem) require(rows.all { row -> users.any { it.uid==row.uid } })
    }
}
internal fun decodeProcesses(raw: String, query: ProcessQuery): ProcessSnapshot {
    require(raw.toByteArray().size <= 131072)
    val j = JSONObject(raw)
    require(j.getInt("protocol") == 2 && j.getString("kind") == "processes" && j.getString("cpuBasis") == "total-machine")
    require(j.getString("ownerFilter") == (query.uid?.toString() ?: "all"))
    require(j.getString("sort")==query.sort.wire && j.getString("scope")==if(query.includeSystem) "all" else "user")
    val values = j.getJSONArray("processes"); require(values.length() <= 200)
    val rows = (0 until values.length()).map { i -> values.getJSONObject(i).let { p ->
        ProcessRow(p.getInt("pid"),p.getString("name"),p.getInt("uid"),p.getString("owner"),p.getString("state"),
            p.getLong("startTicks"),if(p.isNull("cpuPercent")) null else p.getDouble("cpuPercent"),
            if(p.isNull("rssBytes")) null else p.getLong("rssBytes"),p.getDouble("runtimeSeconds"))
    } }
    return ProcessSnapshot(j.getString("host"),j.getString("sampledAt"),query,j.getDouble("sampleSeconds"),
        j.getInt("totalMatching"),j.getBoolean("limited"),j.getBoolean("scanLimited"),j.getInt("denied"),j.getInt("vanished"),rows,decodeUsers(j)).also { it.validate() }
}
data class ProcessUi(val phase: Phase = Phase.Unknown, val message: String = "No process sample received yet.",
    val snapshot: ProcessSnapshot? = null, val lastContactMillis: Long? = null, val receivedElapsed: Long? = null, val busy: Boolean = false) {
    fun aged(now: Long): ProcessUi = if(phase == Phase.Online && (receivedElapsed == null || now-receivedElapsed !in 0..20_000))
        copy(phase=Phase.Stale,message="This process sample is stale. Reconnect for current activity.") else this
}

internal fun sortProcessRows(rows: List<ProcessRow>,sort: ProcessSort): List<ProcessRow> {
    val cpu=compareByDescending<ProcessRow> { it.cpuPercent ?: -1.0 }
    val ram=compareByDescending<ProcessRow> { it.rssBytes ?: -1L }
    return rows.sortedWith((if(sort==ProcessSort.CPU) cpu.then(ram) else ram.then(cpu)).thenBy { it.pid })
}
/** Older reviewed helpers return a CPU-capped sample. Never imply global RAM sorting. */
internal fun decodeLegacyProcesses(raw: String,query: ProcessQuery): ProcessSnapshot {
    require(raw.toByteArray().size<=131072)
    val j=JSONObject(raw)
    require(j.getInt("protocol")==1 && j.getString("kind")=="processes" && j.getString("ownerFilter")=="all")
    val input=j.getJSONArray("processes"); require(input.length()<=200)
    val accounts=linkedMapOf<Int,String>()
    for(i in 0 until input.length()) {
        val row=input.getJSONObject(i);val uid=row.getInt("uid")
        if(uid in 1000..59999) accounts[uid]=row.getString("owner")
    }
    val users=org.json.JSONArray()
    accounts.forEach { (uid,name)->users.put(JSONObject().put("uid",uid).put("name",name)) }
    val selected=org.json.JSONArray()
    for(i in 0 until input.length()) {
        val row=input.getJSONObject(i);val uid=row.getInt("uid")
        if((query.uid==null || uid==query.uid) && (query.includeSystem || accounts.containsKey(uid))) selected.put(row)
    }
    val limited=j.getBoolean("limited")
    j.put("protocol",2).put("ownerFilter",query.uid?.toString() ?: "all").put("sort",query.sort.wire)
        .put("scope",if(query.includeSystem) "all" else "user").put("users",users).put("processes",selected)
        .put("totalMatching",selected.length())
    return decodeProcesses(j.toString(),query).let { sample ->
        sample.copy(rows=sortProcessRows(sample.rows,query.sort),limited=limited,serverSorted=false)
    }
}
