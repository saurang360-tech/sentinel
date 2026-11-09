package dev.titus.sentinel.monitor

import org.json.JSONObject

/** Synthetic examples only. Demo mode never constructs an SSH transport or key. */
internal object DemoData {
    val users=listOf(PcUser(1000,"alex"),PcUser(1001,"sam"))
    val status=MonitorUi(Phase.Online,"Demo data",Snapshot("Demo workstation",
        "123e4567-e89b-12d3-a456-426614174000",18240.0,"running","2026-11-06T12:00:00Z",users))
    val health=HealthUi(Phase.Online,"Demo data",HealthSnapshot(JSONObject("""
        {"protocol":1,"kind":"health","host":"Demo workstation","sampledAt":"2026-11-06T12:00:00Z",
        "sampleSeconds":0.4,"uptimeSeconds":18240,"osName":"Linux (example)","kernel":"Example kernel",
        "load":[1.4,1.1,0.9],"cpu":{"name":"Example 8-core CPU","logicalCores":16,"utilisationPercent":38.5,"temperatureC":56,"iowaitPercent":0.3},
        "memory":{"totalBytes":34359738368,"usedBytes":12884901888,"availableBytes":21474836480,"swapTotalBytes":4294967296,"swapUsedBytes":0,"pressureSomeAvg10":0.1},
        "volumes":[{"label":"System drive","path":"/","totalBytes":1000000000000,"usedBytes":410000000000,"availableBytes":590000000000,"mounted":true}],
        "networks":[{"name":"ethernet","physical":true,"state":"up","rxBytesPerSecond":524288,"txBytesPerSecond":81920}],
        "disks":[{"name":"nvme0n1","model":"Example SSD","readBytesPerSecond":10485760,"writeBytesPerSecond":5242880}],
        "temperatures":[{"label":"Example CPU","value":56}],"fans":[],"componentPower":[],"gpus":[],"warnings":[],
        "failedSystemServices":{"count":0,"names":[]},"failedUserServices":{"count":0,"names":[]}}
    """.trimIndent())))
    fun processes(query: ProcessQuery): ProcessUi {
        val rows=listOf(
            ProcessRow(1201,"blender",1000,"alex","R",100,36.0,2147483648,2400.0),
            ProcessRow(1202,"firefox",1001,"sam","S",110,2.5,3221225472,7200.0),
            ProcessRow(1203,"backup",1000,"alex","S",120,0.5,134217728,600.0),
            ProcessRow(1,"systemd",0,"root","S",1,0.1,16777216,18240.0)
        ).filter { (query.uid==null || it.uid==query.uid) && (query.includeSystem || it.uid!=0) }
        return ProcessUi(Phase.Online,"Demo data",ProcessSnapshot("Demo workstation","2026-11-06T12:00:00Z",
            query,0.4,rows.size,false,false,0,0,sortProcessRows(rows,query.sort),users))
    }
}
