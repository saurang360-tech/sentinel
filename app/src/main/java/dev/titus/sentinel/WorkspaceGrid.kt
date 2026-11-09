package dev.titus.sentinel

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable internal fun WorkspaceGrid(tab: WorkspaceTab,layout: WorkspaceLayout,editing: Boolean,onChange: (WorkspaceLayout)->Unit,
    content: @Composable (WidgetSpec)->Unit) {
    val density=LocalDensity.current.density
    val bounds=remember { mutableMapOf<String,Rect>() }
    var dragged by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<String?>(null) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }
    var menu by remember { mutableStateOf<WidgetSpec?>(null) }
    fun update(w: WidgetSpec) { onChange(layout.update(tab.copy(widgets=tab.widgets.map { if(it.id==w.id) w else it }))) }
    fun reorder(id: String,at: String) {
        val rows=tab.widgets.toMutableList();val from=rows.indexOfFirst { it.id==id };val to=rows.indexOfFirst { it.id==at }
        if(from>=0 && to>=0 && from!=to) { val w=rows.removeAt(from);rows.add(to,w);onChange(layout.update(tab.copy(widgets=rows))) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns=if(maxWidth<340.dp || LocalDensity.current.fontScale>1.3f) 1 else 2
        LazyVerticalGrid(columns=GridCells.Fixed(columns),modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(if(Retro) 2.dp else 16.dp),
            horizontalArrangement=Arrangement.spacedBy(if(Retro) 2.dp else 12.dp),verticalArrangement=Arrangement.spacedBy(if(Retro) 2.dp else 12.dp)) {
            items(tab.widgets,key={ it.id },span={ GridItemSpan(if(columns==1) 1 else it.span) }) { widget ->
                DisposableEffect(widget.id) { onDispose { bounds.remove(widget.id) } }
                val shape=RoundedCornerShape(if(Retro) 0.dp else 12.dp)
                Column(Modifier.fillMaxWidth().height((if(Retro && !editing) (widget.height*.8f).toInt().coerceAtLeast(200) else widget.height).dp).onGloballyPositioned { bounds[widget.id]=it.boundsInRoot() }
                    .clip(shape).background(Panel).border(if(target==widget.id) 2.dp else 1.dp,if(target==widget.id) Accent else Line,shape)
                    .semantics { contentDescription="${widget.kind.title} widget" }) {
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).background(Ink.copy(alpha=.35f)).padding(horizontal=if(Retro) 4.dp else 12.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(editing) Text("⠿",fontSize=24.sp,color=Accent,modifier=Modifier.size(44.dp).pointerInput(widget.id,tab.widgets) {
                            detectDragGesturesAfterLongPress(onDragStart={ dragged=widget.id;target=null;dragPoint=bounds[widget.id]?.center ?: Offset.Zero },
                                onDragCancel={ dragged=null;target=null },onDragEnd={ target?.let { reorder(widget.id,it) };dragged=null;target=null },
                                onDrag={ change,amount -> change.consume();dragPoint+=amount;target=bounds.entries.firstOrNull { it.key!=widget.id && it.value.contains(dragPoint) }?.key })
                        }.semantics { contentDescription="Hold and drag to reorder ${widget.kind.title}" })
                        Text(widget.kind.title,fontSize=16.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                        if(editing) TextButton(onClick={ menu=widget }) { Text("Edit") }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().padding(if(Retro) 4.dp else 12.dp)) {
                        if(editing) Column { Text(if(dragged==widget.id) "Release over another widget" else "${if(widget.span==2) "Full" else "Half"} width · ${widget.height} dp",color=Muted)
                            Spacer(Modifier.height(12.dp));Note("Drag to arrange") }
                        else content(widget)
                    }
                    if(editing) Box(Modifier.fillMaxWidth().height(40.dp),contentAlignment=Alignment.CenterEnd) {
                        Text("↘",fontSize=26.sp,color=Accent,modifier=Modifier.size(48.dp).pointerInput(widget.id,widget.height,widget.span) {
                            var dy=0f;var dx=0f
                            detectDragGestures(onDragStart={dy=0f;dx=0f},onDragCancel={},onDragEnd={
                                update(widget.copy(height=(widget.height+dy/density).roundToInt().coerceIn(200,1000),span=when { dx/density>48 -> 2;dx/density< -48 -> 1;else -> widget.span }))
                            },onDrag={ change,amount -> change.consume();dy+=amount.y;dx+=amount.x })
                        }.semantics { contentDescription="Drag to resize ${widget.kind.title}" })
                    }
                }
            }
            if(tab.widgets.isEmpty()) item(span={ GridItemSpan(columns) }) { SurfaceCard { Text("Build this tab",style=MaterialTheme.typography.titleLarge);Note("Choose Add widget to place telemetry, operations, a terminal or controls here.") } }
        }
    }
    menu?.let { w -> AlertDialog(onDismissRequest={ menu=null },title={ Text("Edit ${w.kind.title}") },text={ Column(Modifier.heightIn(max=480.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        Row { TextButton(onClick={ update(w.copy(span=1));menu=null }) { Text("Half width") };TextButton(onClick={ update(w.copy(span=2));menu=null }) { Text("Full width") } }
        Row { TextButton(onClick={ update(w.copy(height=(w.height-100).coerceAtLeast(200)));menu=null }) { Text("Shorter") };TextButton(onClick={ update(w.copy(height=(w.height+100).coerceAtMost(1000)));menu=null }) { Text("Taller") } }
        val index=tab.widgets.indexOfFirst { it.id==w.id }
        Row { TextButton(enabled=index>0,onClick={ reorder(w.id,tab.widgets[index-1].id);menu=null }) { Text("Move up") };TextButton(enabled=index<tab.widgets.lastIndex,onClick={ reorder(w.id,tab.widgets[index+1].id);menu=null }) { Text("Move down") } }
        Text("Move to tab",fontWeight=FontWeight.Bold)
        layout.tabs.filter { it.id!=tab.id }.forEach { t -> TextButton(enabled=t.widgets.size<32,onClick={ onChange(layout.moveWidget(tab.id,t.id,w.id));menu=null }) { Text(t.name) } }
        TextButton(onClick={ onChange(layout.update(tab.copy(widgets=tab.widgets.filterNot { it.id==w.id })));menu=null }) { Text("Remove widget",color=Danger) }
    } },confirmButton={ TextButton(onClick={ menu=null }) { Text("Close") } }) }
}

@Composable internal fun TabManager(layout: WorkspaceLayout,onChange: (WorkspaceLayout)->Unit,close: ()->Unit) {
    var name by remember { mutableStateOf("") }
    var rename by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest=close,title={ Text("Manage tabs") },text={ Column(Modifier.heightIn(max=520.dp)) {
        Column(Modifier.weight(1f,fill=false).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        layout.tabs.forEachIndexed { index,t ->
            Row(verticalAlignment=Alignment.CenterVertically) { Text(t.name,modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold)
                TextButton(enabled=index>0,onClick={ val tabs=layout.tabs.toMutableList();tabs.removeAt(index);tabs.add(index-1,t);onChange(layout.copy(tabs=tabs)) }) { Text("↑") }
                TextButton(enabled=index<layout.tabs.lastIndex,onClick={ val tabs=layout.tabs.toMutableList();tabs.removeAt(index);tabs.add(index+1,t);onChange(layout.copy(tabs=tabs)) }) { Text("↓") }
            }
            Row { TextButton(onClick={ rename=t.id;name=t.name }) { Text("Rename") };TextButton(enabled=layout.tabs.size>1,onClick={ delete=t.id }) { Text("Delete",color=Danger) } }
        }
        }
        OutlinedTextField(name,{ name=it.take(24) },label={ Text(if(rename==null) "New tab name" else "Rename tab") },singleLine=true)
        Button(enabled=name.isNotBlank() && (rename!=null || layout.tabs.size<12),onClick={
            if(rename!=null) onChange(layout.update(layout.tabs.first { it.id==rename }.copy(name=name.trim())))
            else { val tab=WorkspaceTab(name=name.trim(),widgets=emptyList());onChange(layout.copy(tabs=layout.tabs+tab,selected=tab.id)) }
            name="";rename=null
        }) { Text(if(rename==null) "Add tab" else "Save name") }
    } },confirmButton={ TextButton(onClick=close) { Text("Done") } })
    delete?.let { id -> AlertDialog(onDismissRequest={ delete=null },title={ Text("Delete this tab?") },text={ Text("Its widget layout will be removed. Saved connections, keys and task history are retained.") },
        confirmButton={ TextButton(onClick={ val tabs=layout.tabs.filterNot { it.id==id };onChange(layout.copy(tabs=tabs,selected=if(layout.selected==id) tabs.first().id else layout.selected));delete=null }) { Text("Delete tab",color=Danger) } },dismissButton={ TextButton(onClick={ delete=null }) { Text("Cancel") } }) }
}


@Composable internal fun PresetPicker(layout: WorkspaceLayout,onAdd: (TabPreset)->Unit,close: ()->Unit) {
    AlertDialog(onDismissRequest=close,title={ Text("Add preset") },text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            TabPreset.entries.forEach { preset ->
                TextButton(enabled=layout.tabs.size<12,onClick={ onAdd(preset) },modifier=Modifier.fillMaxWidth()) { Text(preset.title) }
            }
            if(layout.tabs.size>=12) Note("12-tab limit")
        }
    },confirmButton={ TextButton(onClick=close) { Text("Cancel") } })
}
