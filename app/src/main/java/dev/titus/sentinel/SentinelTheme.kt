package dev.titus.sentinel

import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

private data class WorkspacePalette(val ink: Color,val panel: Color,val muted: Color,val line: Color,val white: Color,val accent: Color)
private val NordPalette=WorkspacePalette(Color(0xFF2E3440),Color(0xFF3B4252),Color(0xFFD8DEE9),Color(0xFF4C566A),Color(0xFFECEFF4),Color(0xFF88C0D0))
private val LocalWorkspacePalette=staticCompositionLocalOf { NordPalette }
internal val Ink: Color @Composable get()=LocalWorkspacePalette.current.ink
internal val Panel: Color @Composable get()=LocalWorkspacePalette.current.panel
internal val Muted: Color @Composable get()=LocalWorkspacePalette.current.muted
internal val Line: Color @Composable get()=LocalWorkspacePalette.current.line
internal val White: Color @Composable get()=LocalWorkspacePalette.current.white
internal val Accent: Color @Composable get()=LocalWorkspacePalette.current.accent
private val LocalRetro=staticCompositionLocalOf { false }
internal val Retro: Boolean @Composable get()=LocalRetro.current
internal val Amber: Color @Composable get()=if(Retro) Accent else Color(0xFFEBCB8B)
internal val Danger: Color @Composable get()=if(Retro) Accent else Color(0xFFBF616A)

@Composable internal fun SentinelTheme(preferences: WorkspacePreferences=WorkspacePreferences(),content: @Composable () -> Unit) {
    val accent=when(preferences.accent) { "Ice" -> Color(0xFF8FBCBB);"Snow" -> Color(0xFFECEFF4);else -> Color(0xFF88C0D0) }
    val retro=preferences.theme=="Amber"
    // P3 phosphor-inspired amber, expressed in sRGB for consistent device support.
    val phosphor=Color(0xFFFFB000)
    val palette=if(retro) WorkspacePalette(Color.Black,Color.Black,phosphor,phosphor.copy(alpha=.45f),phosphor,phosphor) else if(preferences.theme=="Nord") NordPalette.copy(accent=accent) else WorkspacePalette(Color(0xFF080808),Color(0xFF161616),Color(0xFFBDBDBD),Color(0xFF555555),Color(0xFFF5F5F2),accent)
    CompositionLocalProvider(LocalWorkspacePalette provides palette,LocalRetro provides retro) {
        MaterialTheme(colorScheme=darkColorScheme(primary=Accent,onPrimary=Ink,primaryContainer=Line,onPrimaryContainer=White,
            secondary=Accent,onSecondary=Ink,secondaryContainer=Line,onSecondaryContainer=White,background=Ink,onBackground=White,
            surface=Panel,onSurface=White,onSurfaceVariant=Muted,surfaceVariant=Line,outline=Line,error=Danger),
            shapes=Shapes(small=androidx.compose.foundation.shape.RoundedCornerShape(if(retro) 0.dp else 8.dp),medium=androidx.compose.foundation.shape.RoundedCornerShape(if(retro) 0.dp else 12.dp),large=androidx.compose.foundation.shape.RoundedCornerShape(if(retro) 0.dp else 16.dp)),
            typography=if(retro) retroTypography() else Typography(headlineLarge=TextStyle(fontSize=32.sp,lineHeight=38.sp,fontWeight=FontWeight.Bold),
                titleLarge=TextStyle(fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.Bold),titleMedium=TextStyle(fontSize=20.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold),
                bodyLarge=TextStyle(fontSize=18.sp,lineHeight=26.sp),bodyMedium=TextStyle(fontSize=16.sp,lineHeight=23.sp),bodySmall=TextStyle(fontSize=14.sp,lineHeight=20.sp),
                labelLarge=TextStyle(fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.Bold)),content=content)
    }
}

private fun retroTypography(): Typography {
    val base=TextStyle(fontFamily=FontFamily.Monospace,fontSize=14.sp,lineHeight=18.sp)
    val title=base.copy(fontWeight=FontWeight.Bold,fontSize=16.sp,lineHeight=20.sp)
    return Typography(displayLarge=title,displayMedium=title,displaySmall=title,
        headlineLarge=title.copy(fontSize=22.sp,lineHeight=26.sp),headlineMedium=title,headlineSmall=title,
        titleLarge=title,titleMedium=title,titleSmall=title,bodyLarge=base,bodyMedium=base,bodySmall=base,
        labelLarge=base.copy(fontWeight=FontWeight.Bold),labelMedium=base,labelSmall=base)
}

/** Small code-drawn icons keep navigation assets consistent without new dependencies. */
@Composable internal fun NavigationGlyph(index: Int, color: Color, modifier: Modifier=Modifier) {
    Canvas(modifier) {
        val u=size.width/24f
        val stroke=Stroke(1.7f*u,cap=StrokeCap.Round)
        fun line(x: Float,y: Float,x2: Float,y2: Float)=drawLine(color,Offset(x*u,y*u),Offset(x2*u,y2*u),1.7f*u,StrokeCap.Round)
        when(index) {
            0 -> { drawRoundRect(color,Offset(3*u,4*u),Size(18*u,13*u),CornerRadius(2*u),style=stroke);line(12f,17f,12f,21f);line(8f,21f,16f,21f) }
            1 -> { val path=Path().apply { moveTo(2*u,13*u);lineTo(7*u,13*u);lineTo(10*u,5*u);lineTo(14*u,20*u);lineTo(17*u,11*u);lineTo(22*u,11*u) };drawPath(path,color,style=stroke) }
            2 -> { drawRoundRect(color,Offset(5*u,4*u),Size(14*u,16*u),CornerRadius(2*u),style=stroke);line(9f,9f,15f,9f);line(9f,13f,15f,13f);line(9f,17f,13f,17f) }
            3 -> { for(y in listOf(6f,12f,18f)) { drawCircle(color,1.2f*u,Offset(4*u,y*u));line(9f,y,21f,y) } }
            4 -> { line(4f,6f,10f,12f);line(10f,12f,4f,18f);line(13f,18f,21f,18f) }
        }
    }
}
