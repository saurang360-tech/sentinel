package dev.titus.sentinel.monitor

import org.json.JSONObject

internal data class JobExpectation(val id: String,val label: String,val lastSuccessMillis: Long?,val deadlineMillis: Long,val state: String)
internal fun decodeExpectations(raw: String): List<JobExpectation> {
    require(raw.toByteArray().size<=65536)
    val root=JSONObject(raw)
    require(root.getInt("protocol")==1 && root.getString("kind")=="jobs")
    val rows=root.optJSONArray("expectations") ?: return emptyList()
    require(rows.length()<=32)
    return (0 until rows.length()).map { i ->
        val row=rows.getJSONObject(i)
        JobExpectation(row.getString("id"),row.getString("label"),if(row.isNull("lastSuccessMillis")) null else row.getLong("lastSuccessMillis"),
            row.getLong("deadlineMillis"),row.getString("state")).also {
            require(java.util.UUID.fromString(it.id).toString()==it.id)
            require(it.label.length in 1..64 && it.label.none(Char::isISOControl))
            require(it.deadlineMillis>0 && (it.lastSuccessMillis==null || it.lastSuccessMillis in 1 until it.deadlineMillis))
            require(it.state in setOf("within_window","overdue_unknown"))
        }
    }.also { require(it.map { r -> r.id }.distinct().size==it.size) }
}
internal fun expectationEvents(host: String,rules: List<JobExpectation>)=rules.filter { it.state=="overdue_unknown" }.map {
    val scope="$host:expectation:${it.id}"
    OutcomeEvent("$scope:${it.deadlineMillis}","${it.label}: no verified success observed by the expected deadline; outcome unknown",2,scope)
}
