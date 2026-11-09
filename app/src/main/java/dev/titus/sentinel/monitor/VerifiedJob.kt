package dev.titus.sentinel.monitor

import org.json.JSONObject

data class VerifiedJob(val id: String, val label: String, val state: String, val exitCode: Int?, val startedMillis: Long, val endedMillis: Long?)
internal fun decodeJobs(raw: String): List<VerifiedJob> {
    require(raw.toByteArray().size<=65536)
    val root=JSONObject(raw)
    require(root.getInt("protocol")==1 && root.getString("kind")=="jobs")
    java.time.Instant.parse(root.getString("sampledAt"))
    val rows=root.getJSONArray("jobs");require(rows.length()<=64)
    return (0 until rows.length()).map { index ->
        val j=rows.getJSONObject(index)
        val job=VerifiedJob(j.getString("id"),j.getString("label"),j.getString("state"),
            if(j.isNull("exitCode")) null else j.getInt("exitCode"),j.getLong("startedMillis"),
            if(j.isNull("endedMillis")) null else j.getLong("endedMillis"))
        require(java.util.UUID.fromString(job.id).toString()==job.id)
        require(job.label.length in 1..64 && job.label.none { it.isISOControl() })
        require(job.state in setOf("running","succeeded","failed","timed_out","ended_unknown"))
        require(job.startedMillis>0 && (job.endedMillis==null || job.endedMillis>=job.startedMillis))
        require(job.state!="succeeded" || job.exitCode==0)
        require(job.state!="failed" || job.exitCode!=0)
        require(job.state !in setOf("running","ended_unknown") || job.exitCode==null)
        job
    }.also { require(it.map { job -> job.id }.distinct().size==it.size) }
}
