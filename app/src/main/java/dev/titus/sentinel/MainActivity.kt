package dev.titus.sentinel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    internal lateinit var security: DeviceSecurity
    internal lateinit var power: ShutdownController
    internal val settingsVisible=kotlinx.coroutines.flow.MutableStateFlow(false)
    internal val terminalVisible=kotlinx.coroutines.flow.MutableStateFlow(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        security=DeviceSecurity(this)
        power=ShutdownController(this)
        enableEdgeToEdge()
        setContent { AccessGate { Sentinel() } }
    }
    override fun onStop() { super.onStop();security.lock();settingsVisible.value=false;terminalVisible.value=false;power.cancel() }
    override fun onUserLeaveHint() { super.onUserLeaveHint();settingsVisible.value=false;terminalVisible.value=false;if(!security.authenticating) security.cancel();power.cancel() }
    override fun onDestroy() { security.cancel();power.close();super.onDestroy() }
}

@Composable internal fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Panel).border(1.dp,Line,RoundedCornerShape(4.dp)).padding(if(Retro) 4.dp else 18.dp), content = content)
}
@Composable internal fun Eyebrow(text: String) { Text(text, color = Muted, fontSize = 14.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold) }
@Composable internal fun Note(text: String) { Text(text, color = Muted, fontSize = if(Retro) 14.sp else 18.sp, lineHeight = if(Retro) 18.sp else 26.sp) }
@Composable internal fun PairLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = if(Retro) 2.dp else 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = if(Retro) 14.sp else 18.sp, modifier = Modifier.weight(0.9f))
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = if(Retro) 14.sp else 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
    }
}

@Composable internal fun Metric(name: String, description: String, percent: Int?, reading: String, secondary: String) {
    Column(Modifier.fillMaxWidth().border(1.dp,Line,RoundedCornerShape(4.dp)).padding(18.dp)) {
        Text(name.uppercase(),fontSize=22.sp,fontWeight=FontWeight.Black,letterSpacing=.8.sp)
        Spacer(Modifier.height(6.dp))
        Text(reading,color=White,fontSize=if(reading.length>8) 32.sp else 52.sp,lineHeight=58.sp,fontWeight=FontWeight.Black,fontFamily=FontFamily.Monospace,letterSpacing=(-2).sp)
        Spacer(Modifier.height(12.dp))
        if(percent!=null) {
            LinearProgressIndicator(progress={ percent.coerceIn(0,100)/100f },modifier=Modifier.fillMaxWidth().height(8.dp),color=White,trackColor=Line,strokeCap=StrokeCap.Butt,gapSize=0.dp,drawStopIndicator={})
            Spacer(Modifier.height(16.dp))
        }
        Text(secondary,color=White,fontSize=18.sp,lineHeight=26.sp,fontWeight=FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(description,color=Muted,fontSize=16.sp,lineHeight=23.sp)
    }
    Spacer(Modifier.height(16.dp))
}
