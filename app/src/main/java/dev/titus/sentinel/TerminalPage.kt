package dev.titus.sentinel

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.titus.sentinel.monitor.*

@Composable internal fun TerminalPage(ui: TerminalUi,connect: ()->Unit,disconnect: ()->Unit,send: (String)->Unit,clear: ()->Unit) {
    // Input and scrollback are intentionally not saved to disk or across app recreation.
    var input by remember { mutableStateOf("") }
    var privateInput by remember { mutableStateOf(false) }
    val scroll=rememberScrollState()
    LaunchedEffect(ui.output) { scroll.scrollTo(scroll.maxValue) }
    LaunchedEffect(ui.connected) { if(!ui.connected) input="" }
    fun submit() { if(ui.connected) { send(input+"\r");input="" } }
    Note(ui.message)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick=if(ui.connected || ui.connecting) disconnect else connect) { Text(if(ui.connected || ui.connecting) "Disconnect" else "Connect") }
        TextButton(onClick=clear) { Text("Clear") }
    }
    Box(Modifier.fillMaxWidth().heightIn(min=260.dp,max=420.dp).background(Color(0xFF111111)).padding(12.dp).verticalScroll(scroll).horizontalScroll(rememberScrollState())) {
        SelectionContainer { Text(ui.output.ifEmpty { "SSH terminal\nConnect to open a new shell." },fontFamily=FontFamily.Monospace,fontSize=12.sp,lineHeight=17.sp,color=Color(0xFFF4F4F1),softWrap=false) }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        listOf("Ctrl+C" to "\u0003","Tab" to "\t","Esc" to "\u001b","↑" to "\u001b[A","↓" to "\u001b[B","Ctrl+D" to "\u0004").forEach { (label,value) ->
            OutlinedButton(onClick={ if(input.isNotEmpty()) { send(input);input="" };send(value) },enabled=ui.connected,contentPadding=PaddingValues(horizontal=10.dp,vertical=0.dp)) { Text(label,fontSize=11.sp) }
        }
    }
    OutlinedTextField(input,{ input=it },label={ Text(if(privateInput) "Private input" else "Command or input") },
        enabled=ui.connected,singleLine=true,modifier=Modifier.fillMaxWidth(),
        visualTransformation=if(privateInput) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions=KeyboardOptions(keyboardType=if(privateInput) KeyboardType.Password else KeyboardType.Ascii,autoCorrectEnabled=false,imeAction=ImeAction.Send),
        keyboardActions=KeyboardActions(onSend={ submit() }))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        FilterChip(selected=privateInput,onClick={ privateInput=!privateInput },label={ Text("Private input") })
        Button(onClick={ submit() },enabled=ui.connected) { Text("Send") }
    }
    Spacer(Modifier.height(10.dp))
    Disclosure("Terminal help") {
        Note("Basic line-oriented SSH, using your PC’s shell. Full-screen tools and Kitty graphics are not supported. Use Private input before typing passwords.")
        Spacer(Modifier.height(8.dp))
        Note("Leaving Terminal or backgrounding Sentinel disconnects the session. Running jobs may stop. Reconnecting opens a new shell; commands are never replayed automatically.")
    }
}
