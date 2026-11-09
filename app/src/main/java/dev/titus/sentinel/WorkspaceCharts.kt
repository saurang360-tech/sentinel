package dev.titus.sentinel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.titus.sentinel.monitor.TelemetryPoint
import java.util.Locale

internal fun displayNumber(v: Double?,unit: String="")=v?.takeIf { it.isFinite() }?.let { String.format(Locale.US,"%.1f%s",it,unit) } ?: "Unavailable"
internal fun bytesValue(v: Double?): String = when { v==null || !v.isFinite() -> "Unavailable";v>=1073741824 -> displayNumber(v/1073741824," GiB");v>=1048576 -> displayNumber(v/1048576," MiB");v>=1024 -> displayNumber(v/1024," KiB");else -> displayNumber(v," B") }

@Composable internal fun Capacity(label: String,used: Double?,total: Double?) {
    val valid=used!=null && total!=null && total>0 && used>=0 && used<=total
    val fraction=if(valid) (used/total).toFloat() else null
    if(label.isNotBlank()) Text(label,fontWeight=FontWeight.Bold,fontSize=18.sp)
    Text(if(valid) "${bytesValue(used)} / ${bytesValue(total)}" else "Unavailable",fontSize=16.sp,color=Muted)
    Spacer(Modifier.height(8.dp))
    if(fraction!=null && Retro) BlockMeter(fraction)
    else if(fraction!=null) LinearProgressIndicator(progress={ fraction },modifier=Modifier.fillMaxWidth().height(9.dp),color=Accent,trackColor=Line)
    else Text("No capacity sample",fontSize=14.sp,color=Muted)
    Spacer(Modifier.height(if(Retro) 4.dp else 16.dp))
}

internal fun blockBar(fraction: Float,segments: Int=12): String {
    require(segments in 1..64)
    val filled=(fraction.coerceIn(0f,1f)*segments).toInt()
    return "["+"█".repeat(filled)+"░".repeat(segments-filled)+"]"
}
@Composable internal fun BlockMeter(fraction: Float) {
    Text(blockBar(fraction),fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace,color=Accent,
        modifier=Modifier.semantics { contentDescription="${(fraction*100).toInt()} percent; block meter rounded down" })
}

@Composable internal fun Trend(points: List<TelemetryPoint>,a: String,b: String?=null,percent: Boolean=false,threshold: Double?=null) {
    val primary=Accent;val secondary=White;val grid=Line;val warning=Amber
    val values=points.flatMap { p -> listOfNotNull(p.values[a],b?.let { p.values[it] }) }
    val max=if(percent) 100.0 else maxOf(1.0,values.maxOrNull() ?: 1.0,threshold ?: 0.0)*1.05
    Canvas(Modifier.fillMaxWidth().height(if(Retro) 48.dp else 80.dp).semantics { contentDescription="Trend with ${points.size} samples; latest ${displayNumber(points.lastOrNull()?.values?.get(a))}. Missing samples are gaps." }) {
        for(i in 0..2) drawLine(grid,Offset(0f,size.height*i/2),Offset(size.width,size.height*i/2),1.dp.toPx())
        val from=points.firstOrNull()?.elapsed ?: 0L;val to=points.lastOrNull()?.elapsed ?: from
        fun x(t: Long)=if(to==from) size.width else size.width*(t-from).toFloat()/(to-from)
        fun y(v: Double)=(size.height*(1-v/max)).toFloat().coerceIn(0f,size.height)
        if(threshold!=null) drawLine(warning,Offset(0f,y(threshold)),Offset(size.width,y(threshold)),2.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(),4.dp.toPx())))
        listOfNotNull(a,b).forEachIndexed { index,key ->
            val path=Path();var previous: TelemetryPoint?=null;var connected=false
            points.forEach { p ->
                val value=p.values[key]
                if(value==null) { connected=false;previous=p }
                else {
                    val gap=previous?.let { p.elapsed-it.elapsed>30_000 } ?: false
                    if(!connected || gap) path.moveTo(x(p.elapsed),y(value)) else path.lineTo(x(p.elapsed),y(value))
                    connected=true;previous=p
                }
            }
            drawPath(path,if(index==0) primary else secondary,style=Stroke(2.dp.toPx(),pathEffect=if(index==0) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),4.dp.toPx()))))
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(if(points.size<2) "Collecting…" else "${((points.last().elapsed-points.first().elapsed)/1000)}s",fontSize=14.sp,color=Muted)
        Text(if(percent) "0–100%" else "0–${displayNumber(max)}",fontSize=14.sp,color=Muted)
    }
    if(b!=null) Text(if(a.startsWith("networks")) "Solid ↓ · Dashed ↑" else "Solid R · Dashed W",fontSize=14.sp,color=Muted)
}
