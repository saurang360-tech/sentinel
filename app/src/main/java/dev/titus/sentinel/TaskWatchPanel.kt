package dev.titus.sentinel

import androidx.compose.runtime.Composable
import androidx.compose.material3.*
import dev.titus.sentinel.monitor.TaskWatch
import dev.titus.sentinel.monitor.VerifiedJob
import dev.titus.sentinel.monitor.JobExpectation

@Composable internal fun TaskWatchPanel(watches: List<TaskWatch>, jobs: List<VerifiedJob>, jobMessage: String, host: String, expectations: List<JobExpectation>, clear: () -> Unit) {
    SurfaceCard {
        Eyebrow("TASK WATCHING")
        Note("Expand a process and choose Watch task. Normal checks run while Sentinel is visible; use Start background monitor for an optional ongoing session. Only jobs launched through the PC runner have verified exit results. Requires the updated PC helper.")
        if(jobMessage.isNotEmpty()) Note(jobMessage)
        jobs.forEach { job -> Text("${job.label} · ${job.state}" + (job.exitCode?.let { " · exit $it" } ?: ""));TaskMuteButton("$host:job:${job.id}") }
        expectations.forEach { rule ->
            Text("${rule.label} · ${rule.state}")
            Note("Expected-success deadline: ${java.time.Instant.ofEpochMilli(rule.deadlineMillis)}. This checks runner exit records, not backup contents or proof that a schedule ran.")
            TaskMuteButton("$host:expectation:${rule.id}")
        }
        watches.forEach { watch ->
            Text(watch.name + " · " + watch.label)
            TaskMuteButton("$host:process:${watch.identity}")
            Note("Last confirmed observation: " + if(watch.observedMillis==0L) "none" else java.time.Instant.ofEpochMilli(watch.observedMillis).toString())
            watch.events.takeLast(3).forEach { Note(it) }
        }
        if(watches.isNotEmpty()) TextButton(onClick=clear) { Text("Delete watches and history") }
    }
}
