package dev.titus.sentinel.monitor

import dev.titus.sentinel.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceTest {
    @Test fun retroMeterNeverClaimsMoreThanObservedAndThemeMigrationIsStable() {
        assertEquals("[████░░░░]",blockBar(.5f,8))
        assertEquals("[░░░░░░░░]",blockBar(0f,8))
        assertEquals("[████████]",blockBar(1f,8))
        assertEquals("[███░░░░░]",blockBar(.49f,8))
        assertEquals("Nord",WorkspacePreferences().theme)
        assertEquals("Mono",WorkspacePreferences(nord=false).theme)
        val prefs=WorkspacePreferences(theme="Amber")
        assertEquals(prefs,prefs.copy(theme="Nord").copy(theme="Amber"))
    }

    @Test fun simpleDefaultsKeepAdvancedWidgetsOptionalAndMovingRetainsIdentity() {
        val layout=WorkspaceLayout.defaults().checked()
        assertEquals(listOf("Home","Users","Terminal","Power","Settings"),layout.tabs.map { it.name })
        assertEquals(setOf(WidgetKind.Connection,WidgetKind.Cpu,WidgetKind.Memory,WidgetKind.Network,WidgetKind.Disk,WidgetKind.Storage),layout.tabs.first().widgets.map { it.kind }.toSet())
        assertFalse(layout.tabs.flatMap { it.widgets }.any { it.kind in setOf(WidgetKind.Alerts,WidgetKind.CpuDetails) })
        assertTrue(WidgetKind.entries.all { it.title.split(" ").size<=2 })
        val source=layout.tabs.first();val target=layout.tabs.last();val widget=source.widgets.first()
        val moved=layout.moveWidget(source.id,target.id,widget.id)
        assertFalse(moved.tabs.first().widgets.any { it.id==widget.id })
        assertEquals(widget,moved.tabs.last().widgets.last())
    }
    @Test fun presetsAreIndependentAndDeletedEssentialsStayDeletedAfterSaving() {
        val base=WorkspaceLayout.defaults()
        val next=base.addPreset(TabPreset.Terminal)
        assertEquals("Terminal 2",next.tabs.first { it.id==next.selected }.name)
        assertEquals(base.tabs.size+1,next.tabs.size)
        val original=base.tabs.first { it.name=="Terminal" }
        val duplicate=next.tabs.first { it.name=="Terminal 2" }
        assertNotEquals(original.id,duplicate.id)
        assertNotEquals(original.widgets.single().id,duplicate.widgets.single().id)
        assertEquals(next,next.withEssentialTabs())
        assertEquals(base.selected,base.withEssentialTabs().selected)
        val empty=WorkspaceTab(name="Custom",widgets=emptyList())
        val upgraded=WorkspaceLayout(listOf(empty),empty.id).withEssentialTabs()
        assertEquals(empty,upgraded.tabs.first())
        assertEquals(setOf(WidgetKind.Users,WidgetKind.Terminal,WidgetKind.Power),upgraded.tabs.drop(1).map { it.widgets.first().kind }.toSet())
        val full=WorkspaceLayout(List(12) { WorkspaceTab(name="Tab $it",widgets=emptyList()) },"unused")
        assertThrows(IllegalArgumentException::class.java) { full.copy(selected=full.tabs.first().id).addPreset(TabPreset.Hardware) }
    }
    @Test fun userTotalsPreserveMissingValuesAndSeparateAccounts() {
        fun row(pid: Int,uid: Int,cpu: Double?,rss: Long?)=ProcessRow(pid,"work",uid,"user$uid","S",1,cpu,rss,1.0)
        val totals=userUsage(listOf(row(1,1000,4.0,10),row(2,1000,6.0,20),row(3,1001,null,null)))
        assertEquals(2,totals.size)
        assertEquals(10.0,totals[0].cpu!!,0.0)
        assertEquals(30L,totals[0].rss)
        assertNull(totals[1].cpu);assertNull(totals[1].rss)
        assertEquals(emptyList<UserUsage>(),userUsage(emptyList()))
    }

    @Test fun malformedLayoutsCannotRemoveLastTabOrCreateInvalidGeometry() {
        val layout=WorkspaceLayout.defaults()
        assertThrows(IllegalArgumentException::class.java) { layout.copy(tabs=emptyList()).checked() }
        assertThrows(IllegalArgumentException::class.java) { layout.copy(selected="missing").checked() }
        val tab=layout.tabs.first()
        assertThrows(IllegalArgumentException::class.java) { layout.update(tab.copy(widgets=listOf(tab.widgets.first().copy(span=3)))) }
        assertThrows(IllegalArgumentException::class.java) { layout.update(tab.copy(widgets=listOf(tab.widgets.first().copy(height=1)))) }
    }
    @Test fun macroArgumentsAreLiteralAndCannotInsertAnotherCommand() {
        val command=expandMacro("systemctl --user status -- {{unit}}",mapOf("unit" to "x'; touch /tmp/injected; echo '"))
        assertEquals("systemctl --user status -- 'x'\\''; touch /tmp/injected; echo '\\'''",command)
        assertThrows(IllegalArgumentException::class.java) { expandMacro("echo {{value}}",mapOf("value" to "line\nbreak")) }
        assertThrows(IllegalArgumentException::class.java) { expandMacro("echo {{value}}",emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { expandMacro("echo '{{value}}'",mapOf("value" to "unsafe")) }
        assertThrows(IllegalArgumentException::class.java) { expandMacro("echo prefix{{value}}",mapOf("value" to "unsafe")) }
    }
    @Test fun journalRequestsAcceptOnlyOneNamedUnit() {
        assertTrue(validServiceRequest("sentinel-service-v1 system example.service"))
        assertTrue(validServiceRequest("sentinel-service-v1 user example@user.service"))
        assertFalse(validServiceRequest("sentinel-service-v1 system example.service;id"))
        assertFalse(validServiceRequest("sentinel-service-v1 system --all"))
        assertFalse(validServiceRequest("sentinel-service-v1 system x.service extra"))
    }

}
