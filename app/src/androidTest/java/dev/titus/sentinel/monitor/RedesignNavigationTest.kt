package dev.titus.sentinel.monitor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.titus.sentinel.DemoGalleryActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs isolated debug UI. No configured PC is used. */
@RunWith(AndroidJUnit4::class)
class RedesignNavigationTest {
    @Test fun simpleWorkspaceMigratesAndAdvancedWidgetsRemainAddable() {
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("sentinel-workspace",0)
        prefs.edit().clear().commit()
        val store=dev.titus.sentinel.WorkspaceStore(context)
        val base=dev.titus.sentinel.WorkspaceLayout.defaults()
        val home=base.tabs.first()
        store.save(base.update(home.copy(widgets=home.widgets+dev.titus.sentinel.WidgetSpec(kind=dev.titus.sentinel.WidgetKind.Terminal))))
        val old=prefs.getString("layout",null)
        prefs.edit().remove("simple-layout").commit()
        try {
            launch();find("Home");find("Status");find("CPU")
            val simple=store.load()
            assertEquals(listOf("Home","Users","Terminal","Power","Settings"),simple.tabs.map { it.name })
            assertFalse(simple.tabs.first().widgets.any { it.kind==dev.titus.sentinel.WidgetKind.Terminal })
            assertEquals(old,prefs.getString("previous-layout",null))
            screenshot("14-simple-home")
            click("Edit");click("Add widget");click("Sensors")
            assertTrue(store.load().tabs.first().widgets.any { it.kind==dev.titus.sentinel.WidgetKind.Sensors })
            click("Done");click("Settings");find("Theme");screenshot("15-simple-settings")
        } finally { activity?.let { instrumentation.runOnMainSync { it.finish() } } }
    }

    @Test fun presetTabsCanBeAddedCustomizedAndRemoved() {
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("sentinel-workspace",0)
        prefs.edit().clear().commit()
        val store=dev.titus.sentinel.WorkspaceStore(context)
        val old=dev.titus.sentinel.WorkspaceTab(name="My graphs",widgets=listOf(dev.titus.sentinel.WidgetSpec(kind=dev.titus.sentinel.WidgetKind.Cpu)))
        val settings=dev.titus.sentinel.TabPreset.Settings.create()
        store.save(dev.titus.sentinel.WorkspaceLayout(listOf(old,settings),settings.id))
        prefs.edit().remove("essential-tabs").commit()
        try {
            launch();find("Add preset")
            val upgraded=store.load()
            assertEquals(old,upgraded.tabs.first())
            assertTrue(upgraded.tabs.any { it.name=="Power" })
            click("Add preset");click("Hardware")
            assertEquals("Hardware",store.load().tabs.first { it.id==store.load().selected }.name)
            click("Edit");click("Add widget");click("CPU")
            assertTrue(store.load().tabs.first { it.name=="Hardware" }.widgets.any { it.kind==dev.titus.sentinel.WidgetKind.Cpu })
            click("Edit");click("Remove widget")
            assertFalse(store.load().tabs.first { it.name=="Hardware" }.widgets.any { it.kind==dev.titus.sentinel.WidgetKind.CpuDetails })
            click("Tabs");find("Manage tabs");screenshot("16-presets")
            click("Done");click("Done")
            launch();find("Hardware")
            assertEquals(old,store.load().tabs.first())
        } finally { activity?.let { instrumentation.runOnMainSync { it.finish() } } }
    }

    @Test fun amberThemeSwitchesPersistsAndUsesBlackBackground() {
        try {
            instrumentation.targetContext.getSharedPreferences("sentinel-theme-review",0).edit().clear().commit()
            launch("theme");click("Amber")
            val prefs=instrumentation.targetContext.getSharedPreferences("sentinel-theme-review",0)
            assertEquals("Amber",prefs.getString("theme",null))
            launch("theme")
            assertEquals("Amber",prefs.getString("theme",null))
            val bitmap=automation.takeScreenshot()!!
            assertEquals(android.graphics.Color.BLACK,bitmap.getPixel(1,bitmap.height/2))
            screenshot("12-amber-settings")
            click("Nord")
            assertEquals("Nord",prefs.getString("theme",null))
            assertFalse(nodes().any { it.text?.toString()?.contains("DEMO")==true })
        } finally { activity?.let { instrumentation.runOnMainSync { it.finish() } } }
    }

    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val automation get()=instrumentation.uiAutomation
    private var activity: android.app.Activity?=null
    private fun launch(surface: String?=null,scale: Float=1f) {
        activity?.let { instrumentation.runOnMainSync { it.finish() } }
        val intent=Intent(instrumentation.targetContext,DemoGalleryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("surface",surface).putExtra("fontScale",scale)
        activity=instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync();SystemClock.sleep(700)
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        if(android.os.Build.VERSION.SDK_INT>=33) automation.clearCache()
        val result=mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?) { if(node==null) return;result.add(node);for(i in 0 until node.childCount) walk(node.getChild(i)) }
        walk(automation.rootInActiveWindow);return result
    }
    private fun find(text: String): AccessibilityNodeInfo {
        val end=SystemClock.uptimeMillis()+8000
        do { nodes().firstOrNull { it.text?.toString()?.contains(text)==true }?.let { return it };SystemClock.sleep(100) } while(SystemClock.uptimeMillis()<end)
        error("Missing UI text: $text. Visible: ${nodes().mapNotNull { it.text }.joinToString()}")
    }
    private fun click(text: String) {
        var node: AccessibilityNodeInfo?=find(text)
        while(node!=null && !node.isClickable) node=node.parent
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)
        instrumentation.waitForIdleSync();SystemClock.sleep(500)
    }
    private fun swipe(x1: Float,y1: Float,x2: Float,y2: Float) {
        val start=SystemClock.uptimeMillis()
        for(i in 0..20) {
            val event=MotionEvent.obtain(start,start+i*16L,when(i) { 0 -> MotionEvent.ACTION_DOWN;20 -> MotionEvent.ACTION_UP;else -> MotionEvent.ACTION_MOVE },x1+(x2-x1)*i/20,y1+(y2-y1)*i/20,0)
            event.source=InputDevice.SOURCE_TOUCHSCREEN;automation.injectInputEvent(event,true);event.recycle()
        }
        instrumentation.waitForIdleSync();SystemClock.sleep(650)
    }
    private fun screenshot(name: String) {
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"redesign/$name.png");file.parentFile!!.mkdirs()
        automation.takeScreenshot()!!.let { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
    }
    @Test fun swipeAndTabsStayInSyncAndScreensRender() {
        try {
            instrumentation.targetContext.getSharedPreferences("sentinel-workspace",0).edit().clear().commit()
            launch();find("Home");find("Status");screenshot("01-workspace-dashboard")
            val bounds=Rect();automation.rootInActiveWindow.getBoundsInScreen(bounds)
            swipe(bounds.width()*.85f,bounds.height()*.45f,bounds.width()*.15f,bounds.height()*.45f)
            find("Users");find("Processes");screenshot("02-workspace-operations")
            click("Edit");find("Add widget")
            click("Tabs");find("Manage tabs")
            val field=nodes().first { it.isEditable }
            field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Custom tab") })
            click("Add tab");click("Done");find("Custom tab")
            click("Add widget");click("Status")
            val stored=dev.titus.sentinel.WorkspaceStore(instrumentation.targetContext,"sentinel-workspace").load()
            assertEquals(6,stored.tabs.size);assertEquals("Custom tab",stored.tabs.last().name)
            assertEquals(dev.titus.sentinel.WidgetKind.Connection,stored.tabs.last().widgets.single().kind)
            click("Done");find("Not connected");screenshot("03-workspace-custom")
            launch();find("Custom tab");find("Not connected")
        } finally { activity?.let { instrumentation.runOnMainSync { it.finish() } } }
    }
    @Test fun widgetEditingCanResizeReorderMoveAndRemoveWithoutLosingOtherTabs() {
        try {
            val store=dev.titus.sentinel.WorkspaceStore(instrumentation.targetContext,"sentinel-workspace")
            val first=dev.titus.sentinel.WorkspaceTab(name="Widget source",widgets=listOf(
                dev.titus.sentinel.WidgetSpec(kind=dev.titus.sentinel.WidgetKind.Connection,span=1,height=240),
                dev.titus.sentinel.WidgetSpec(kind=dev.titus.sentinel.WidgetKind.Memory,span=1,height=240)))
            val second=dev.titus.sentinel.WorkspaceTab(name="Widget target",widgets=emptyList())
            store.save(dev.titus.sentinel.WorkspaceLayout(listOf(first,second),first.id))
            launch();click("Edit");click("Edit");find("Edit Status")
            click("Taller");assertEquals(340,store.load().tabs.first().widgets.first().height)
            click("Edit");click("Move down")
            assertEquals(dev.titus.sentinel.WidgetKind.Memory,store.load().tabs.first().widgets.first().kind)
            click("Edit");click("Widget target")
            assertEquals(dev.titus.sentinel.WidgetKind.Memory,store.load().tabs.last().widgets.single().kind)
            click("Edit");click("Remove widget")
            assertTrue(store.load().tabs.first().widgets.isEmpty())
            assertEquals(2,store.load().tabs.size)
            screenshot("04-workspace-editor")
            launch();find("Build this tab")
        } finally { activity?.let { instrumentation.runOnMainSync { it.finish() } } }
    }
    @Test fun dragHandlesReorderAndResizeSavedWidgets() {
        try {
            val store=dev.titus.sentinel.WorkspaceStore(instrumentation.targetContext,"sentinel-workspace")
            val first=dev.titus.sentinel.WidgetSpec(kind=dev.titus.sentinel.WidgetKind.Connection,span=1,height=240)
            val second=dev.titus.sentinel.WidgetSpec(kind=dev.titus.sentinel.WidgetKind.Memory,span=1,height=240)
            val tab=dev.titus.sentinel.WorkspaceTab(name="Drag test",widgets=listOf(first,second))
            store.save(dev.titus.sentinel.WorkspaceLayout(listOf(tab),tab.id))
            launch();click("Edit")
            fun handle(description: String): Rect {
                val node=nodes().first { it.contentDescription?.toString()==description }
                return Rect().also { node.getBoundsInScreen(it) }
            }
            fun drag(from: Rect,toX: Float,toY: Float,hold: Boolean) {
                val down=SystemClock.uptimeMillis()
                fun event(action: Int,x: Float,y: Float) { val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.source=InputDevice.SOURCE_TOUCHSCREEN;automation.injectInputEvent(e,true);e.recycle() }
                val x=from.exactCenterX();val y=from.exactCenterY()
                event(MotionEvent.ACTION_DOWN,x,y)
                if(hold) SystemClock.sleep(650)
                for(i in 1..20) { event(MotionEvent.ACTION_MOVE,x+(toX-x)*i/20,y+(toY-y)*i/20);SystemClock.sleep(16) }
                event(MotionEvent.ACTION_UP,toX,toY);instrumentation.waitForIdleSync();SystemClock.sleep(600)
            }
            val from=handle("Hold and drag to reorder Status")
            val to=handle("Hold and drag to reorder RAM")
            drag(from,to.exactCenterX(),to.exactCenterY(),true)
            assertEquals(second.id,store.load().tabs.single().widgets.first().id)
            val resize=handle("Drag to resize RAM")
            drag(resize,resize.exactCenterX()+140,resize.exactCenterY()+200,false)
            assertTrue(store.load().tabs.single().widgets.first().height>240)
            screenshot("05-workspace-drag-resize")
        } finally { activity?.let { instrumentation.runOnMainSync { it.finish() } } }
    }
    @Test fun setupValidatesFieldsAndKeepsTestDisabledUntilSave() {
        try {
            launch("settings");screenshot("06-setup")
            click("Continue to pairing");find("Enter a hostname or IP address, without a port or URL.")
            val edits=nodes().filter { it.isEditable }
            assertEquals(5,edits.size)
            val values=listOf("Workstation","workstation.local","22","alex","SHA256:"+"A".repeat(43))
            edits.zip(values).forEach { (node,value) -> assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value) })) }
            click("Continue to pairing");find("Pair this phone")
            click("3. Verify");find("Save & verify")
            var test=find("Test saved connection");while(test.parent!=null && !test.isClickable) test=test.parent
            assertFalse(test.isEnabled)
            click("Save connection");find("Connection saved. Test it below.")
            click("Test saved connection");find("✓ Monitoring connection verified");screenshot("07-verification")
        } finally { activity?.let { instrumentation.runOnMainSync { it.finish() } } }
    }
    @Test fun processControlsRemainVisibleWhileResultsScroll() {
        try {
            launch("processes")
            val before=Rect();find("Search name or PID").getBoundsInScreen(before)
            val bounds=Rect();automation.rootInActiveWindow.getBoundsInScreen(bounds)
            swipe(bounds.width()*.5f,bounds.height()*.85f,bounds.width()*.5f,bounds.height()*.45f)
            val after=Rect();find("Search name or PID").getBoundsInScreen(after)
            assertEquals(before,after);find("Include system processes");find("CPU ↓");screenshot("11-processes-scrolled")
        } finally { activity?.let { instrumentation.runOnMainSync { it.finish() } } }
    }
    @Test fun terminalAndLargeTextRenderWithoutRealShell() {
        try {
            launch("terminal");find("Mask private input");screenshot("08-terminal")
            click("Help");find("Using Terminal");click("Got it")
            nodes().first { it.isEditable }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            SystemClock.sleep(600);find("Send")
            val titleBounds=Rect();find("Terminal").getBoundsInScreen(titleBounds);assertTrue("Keyboard must not pan the title offscreen",titleBounds.top>=0)
            screenshot("08b-terminal-keyboard")
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            instrumentation.targetContext.getSharedPreferences("sentinel-workspace",0).edit().clear().commit()
            launch(scale=1.6f);find("Home");screenshot("09-large-text")
            click("Settings");find("Theme");screenshot("10-operations-large-text")
        } finally { activity?.let { instrumentation.runOnMainSync { it.finish() } } }
    }
}
