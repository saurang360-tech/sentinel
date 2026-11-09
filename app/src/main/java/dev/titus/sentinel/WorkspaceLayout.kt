package dev.titus.sentinel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal enum class WidgetKind(val title: String,val height: Int=340) {
    Connection("Status",200), Cpu("CPU",260), Memory("RAM",280), Storage("Storage",300),
    Network("Network",260), Disk("Disk",260), Sensors("Sensors",400), Services("Services",420),
    Processes("Processes",580), Watches("Watches",440), Jobs("Jobs",420), Monitor("Monitor",440),
    Alerts("Alerts",480), Delivery("Notifications",300), Terminal("Terminal",680), Power("Power",380),
    Preferences("Settings",600), Connections("Connection",700), System("System",340),
    CpuDetails("CPU details",480), MemoryDetails("RAM details",400), NetworkDetails("Interfaces",380), DiskDetails("Drives",380),
    ConnectionDetails("Session",300), Users("Users",340)

}
internal enum class TabPreset(val title: String,val kinds: List<WidgetKind>) {
    Home("Home",listOf(WidgetKind.Connection,WidgetKind.Cpu,WidgetKind.Memory,WidgetKind.Network,WidgetKind.Disk,WidgetKind.Storage)),
    Users("Users",listOf(WidgetKind.Users,WidgetKind.Processes)),
    Terminal("Terminal",listOf(WidgetKind.Terminal)),
    Power("Power",listOf(WidgetKind.Power)),
    Hardware("Hardware",listOf(WidgetKind.CpuDetails,WidgetKind.MemoryDetails,WidgetKind.Sensors,WidgetKind.NetworkDetails,WidgetKind.DiskDetails)),
    Tasks("Tasks",listOf(WidgetKind.Services,WidgetKind.Watches,WidgetKind.Jobs,WidgetKind.Monitor,WidgetKind.Alerts,WidgetKind.Delivery)),
    Settings("Settings",listOf(WidgetKind.Preferences,WidgetKind.Connections));
    fun create(name: String=title)=WorkspaceTab(name=name,widgets=kinds.map { WidgetSpec(kind=it) })
}
internal fun WorkspaceLayout.addPreset(preset: TabPreset): WorkspaceLayout {
    require(tabs.size<12)
    var name=preset.title;var suffix=2
    while(tabs.any { it.name==name }) { name="${preset.title} ${suffix++}" }
    val tab=preset.create(name)
    val settings=tabs.indexOfFirst { it.widgets.any { w -> w.kind==WidgetKind.Preferences } }
    val next=tabs.toMutableList().apply { add(if(settings>=0) settings else size,tab) }
    return copy(tabs=next,selected=tab.id).checked()
}
internal fun WorkspaceLayout.withEssentialTabs(): WorkspaceLayout {
    var result=this
    for(preset in listOf(TabPreset.Users,TabPreset.Terminal,TabPreset.Power)) {
        if(result.tabs.size<12 && result.tabs.none { tab -> tab.widgets.any { it.kind==preset.kinds.first() } }) {
            val selected=result.selected
            result=result.addPreset(preset).copy(selected=selected)
        }
    }
    return result.checked()
}
internal data class WidgetSpec(val id: String=UUID.randomUUID().toString(),val kind: WidgetKind,val span: Int=2,val height: Int=kind.height)
internal data class WorkspaceTab(val id: String=UUID.randomUUID().toString(),val name: String,val widgets: List<WidgetSpec>)
internal data class WorkspaceLayout(val tabs: List<WorkspaceTab>,val selected: String) {
    fun checked(): WorkspaceLayout {
        require(tabs.size in 1..12 && tabs.map { it.id }.distinct().size==tabs.size)
        require(tabs.any { it.id==selected })
        require(tabs.all { it.name.isNotBlank() && it.name.length<=24 && it.name.none { c -> c.isISOControl() } && it.widgets.size<=32 && it.widgets.all { w -> w.span in 1..2 && w.height in 200..1000 } })
        require(tabs.flatMap { it.widgets }.map { it.id }.distinct().size==tabs.sumOf { it.widgets.size })
        return this
    }
    fun update(tab: WorkspaceTab)=copy(tabs=tabs.map { if(it.id==tab.id) tab else it }).checked()
    fun moveWidget(from: String,to: String,id: String): WorkspaceLayout {
        if(from==to) return this
        val source=tabs.first { it.id==from };val target=tabs.first { it.id==to }
        val widget=source.widgets.first { it.id==id };require(target.widgets.size<32)
        return copy(tabs=tabs.map { when(it.id) { from -> it.copy(widgets=it.widgets.filterNot { w -> w.id==id });to -> it.copy(widgets=it.widgets+widget);else -> it } }).checked()
    }
    companion object {
        fun defaults(): WorkspaceLayout {
            val tabs=listOf(TabPreset.Home,TabPreset.Users,TabPreset.Terminal,TabPreset.Power,TabPreset.Settings).map { it.create() }
            return WorkspaceLayout(tabs,tabs.first().id)
        }
    }
}
internal data class WorkspacePreferences(val pollSeconds: Int=10,val accent: String="Frost",val nord: Boolean=true,val theme: String=if(nord) "Nord" else "Mono")
internal class WorkspaceStore(context: Context,namespace: String="sentinel-workspace") {
    private val prefs=context.getSharedPreferences(namespace,Context.MODE_PRIVATE)
    var error: String?=null
        private set
    fun load(): WorkspaceLayout = runCatching {
        val raw=prefs.getString("layout",null) ?: return WorkspaceLayout.defaults()
        if(!prefs.getBoolean("simple-layout",false)) {
            check(prefs.edit().putString("previous-layout",raw).commit())
            val simple=WorkspaceLayout.defaults()
            save(simple)
            return simple
        }
        require(raw.length<=512000)
        val root=JSONObject(raw);require(root.getInt("version")==1)
        val rows=root.getJSONArray("tabs");require(rows.length() in 1..12)
        val loaded=WorkspaceLayout((0 until rows.length()).map { i ->
            val t=rows.getJSONObject(i);val ws=t.getJSONArray("widgets");require(ws.length()<=32)
            WorkspaceTab(t.getString("id"),t.getString("name"),(0 until ws.length()).map { j -> val w=ws.getJSONObject(j)
                WidgetSpec(w.getString("id"),WidgetKind.valueOf(w.getString("kind")),w.getInt("span"),w.getInt("height")) })
        },root.getString("selected")).checked()
        if(!prefs.getBoolean("essential-tabs",false)) {
            val upgraded=loaded.withEssentialTabs()
            save(upgraded)
            upgraded
        } else loaded
    }.getOrElse { error="Saved layout could not be read. A temporary default is shown; save explicitly to replace it.";WorkspaceLayout.defaults() }
    fun save(layout: WorkspaceLayout) {
        layout.checked();val tabs=JSONArray()
        layout.tabs.forEach { t -> val widgets=JSONArray();t.widgets.forEach { w -> widgets.put(JSONObject().put("id",w.id).put("kind",w.kind.name).put("span",w.span).put("height",w.height)) }
            tabs.put(JSONObject().put("id",t.id).put("name",t.name).put("widgets",widgets)) }
        check(prefs.edit().putBoolean("simple-layout",true).putBoolean("essential-tabs",true).putString("layout",JSONObject().put("version",1).put("selected",layout.selected).put("tabs",tabs).toString()).commit())
        error=null
    }
    fun preferences()=WorkspacePreferences(prefs.getInt("poll",10).takeIf { it in listOf(1,2,5,10) } ?: 10,prefs.getString("accent","Frost")!!.takeIf { it in listOf("Frost","Ice","Snow") } ?: "Frost",prefs.getBoolean("nord",true),prefs.getString("theme",null)?.takeIf { it in listOf("Nord","Mono","Amber") } ?: if(prefs.getBoolean("nord",true)) "Nord" else "Mono")
    fun save(value: WorkspacePreferences) { require(value.pollSeconds in listOf(1,2,5,10) && value.theme in listOf("Nord","Mono","Amber"));check(prefs.edit().putInt("poll",value.pollSeconds).putString("accent",value.accent).putBoolean("nord",value.nord).putString("theme",value.theme).commit()) }
}
