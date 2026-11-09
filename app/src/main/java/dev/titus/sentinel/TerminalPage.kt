package dev.titus.sentinel

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.titus.sentinel.monitor.*

@Composable internal fun TerminalPage(ui: TerminalUi,connect: ()->Unit,disconnect: ()->Unit,send: (String)->Unit,clear: ()->Unit) {
    // Secrets and scrollback are never saved across recreation.
    var input by remember { mutableStateOf("") }
    var privateInput by remember { mutableStateOf(false) }
    var macros by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var followOutput by remember { mutableStateOf(true) }
    val scroll=rememberScrollState()
    LaunchedEffect(ui.output) { if(followOutput) scroll.scrollTo(scroll.maxValue) }
    LaunchedEffect(ui.connected) { if(!ui.connected) input="" }
    fun submit() { if(ui.connected) { send(input+"\r");input="" } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(ui.connected) "● Connected" else if(ui.connecting) "◌ Connecting" else "○ Disconnected",color=if(ui.connected) Accent else Amber,modifier=Modifier.weight(1f),fontSize=18.sp)
            TextButton(enabled=ui.connected,onClick={ macros=true }) { Text("Macros") }
            TextButton(onClick={ help=true }) { Text("Help") }
            OutlinedButton(shape=RoundedCornerShape(4.dp),onClick=if(ui.connected || ui.connecting) disconnect else connect) { Text(if(ui.connected || ui.connecting) "Disconnect" else "Connect") }
        }
        Text(ui.message,color=Muted,fontSize=16.sp,maxLines=2,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Ink).border(1.dp,Line,RoundedCornerShape(4.dp))
            .padding(12.dp).verticalScroll(scroll).horizontalScroll(rememberScrollState())) {
            SelectionContainer { Text(ui.output.ifEmpty { "Your shell will appear here.\nConnect to start a new SSH session." },fontFamily=FontFamily.Monospace,fontSize=if(Retro) 14.sp else 16.sp,lineHeight=if(Retro) 18.sp else 24.sp,color=White,softWrap=false) }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={ followOutput=!followOutput }) { Text(if(followOutput) "Following output ✓" else "Follow output") }
            Spacer(Modifier.weight(1f));TextButton(onClick=clear) { Text("Clear") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Ctrl+C" to "\u0003","Tab" to "\t","Esc" to "\u001b","↑" to "\u001b[A","↓" to "\u001b[B","Ctrl+D" to "\u0004").forEach { (label,value) ->
                OutlinedButton(shape=RoundedCornerShape(4.dp),onClick={ if(input.isNotEmpty()) { send(input);input="" };send(value) },enabled=ui.connected,modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription=when(label) { "↑" -> "Previous command";"↓" -> "Next command";else -> label } },contentPadding=PaddingValues(horizontal=12.dp,vertical=0.dp)) { Text(label,fontSize=16.sp) }
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input,{ input=it },label={ Text(if(privateInput) "Private input" else "Command") },enabled=ui.connected,singleLine=true,modifier=Modifier.weight(1f),
                visualTransformation=if(privateInput) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions=KeyboardOptions(keyboardType=if(privateInput) KeyboardType.Password else KeyboardType.Ascii,autoCorrectEnabled=false,imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={ submit() }))
            Button(shape=RoundedCornerShape(4.dp),onClick={ submit() },enabled=ui.connected) { Text("Send") }
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(privateInput,{ privateInput=it },modifier=Modifier.semantics { contentDescription="Mask private input" })
            Text("Mask private input",fontSize=18.sp,color=Muted)
        }
    }
    if(macros) TerminalMacroDrawer(close={ macros=false },insert={ command,run -> if(ui.connected) { if(run) send(command+"\r") else input=command;macros=false } })
    if(help) AlertDialog(onDismissRequest={ help=false },title={ Text("Using Terminal") },text={ Column {
        Note("Basic SSH shell. Full-screen tools are not supported. Leaving Terminal or backgrounding Sentinel disconnects; running jobs may stop.")
        Spacer(Modifier.height(12.dp));Note("Mask private input before entering passwords. Masking hides this input field; the remote program controls what appears in output.")
        Spacer(Modifier.height(12.dp));Note("Turn off Following output to read earlier lines. Reconnecting opens a new shell; commands are never replayed.")
    } },confirmButton={ TextButton(onClick={ help=false }) { Text("Got it") } })
}
