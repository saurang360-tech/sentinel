package dev.titus.sentinel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.titus.sentinel.monitor.ConnectionVault
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class TerminalMacro(val id: String=UUID.randomUUID().toString(),val name: String,val template: String,val run: Boolean=false)
internal fun macroParameters(template: String)=Regex("\\{\\{([A-Za-z][A-Za-z0-9_]{0,31})}}").findAll(template).map { it.groupValues[1] }.distinct().toList()
internal fun expandMacro(template: String,values: Map<String,String>): String {
    require(template.length in 1..2048 && template.none { it=='\u0000' || it=='\r' || it=='\n' })
    val matches=Regex("\\{\\{([A-Za-z][A-Za-z0-9_]{0,31})}}").findAll(template).toList()
    matches.forEach { match ->
        var quote: Char?=null;var escaped=false
        for(c in template.substring(0,match.range.first)) {
            if(escaped) escaped=false
            else if(c=='\\' && quote!='\'') escaped=true
            else if(quote!=null) { if(c==quote) quote=null }
            else if(c=='\'' || c=='"' || c=='`') quote=c
        }
        require(quote==null && !escaped) { "Parameters must be unquoted standalone shell arguments" }
        val before=template.getOrNull(match.range.first-1);val after=template.getOrNull(match.range.last+1)
        require(before==null || before.isWhitespace() || before in "|&;(<>")
        require(after==null || after.isWhitespace() || after in "|&;)<>")
    }
    val names=macroParameters(template)
    require(names.all { it in values })
    val result=Regex("\\{\\{([A-Za-z][A-Za-z0-9_]{0,31})}}").replace(template) { match ->
        val value=values.getValue(match.groupValues[1]);require(value.length<=512 && value.none { it=='\u0000' || it=='\r' || it=='\n' })
        "'"+value.replace("'","'\\''")+"'"
    }
    require(result.length<=4096)
    return result
}
private val builtInMacros=listOf(TerminalMacro("uptime","Uptime","uptime"),TerminalMacro("disk","Disk space","df -h"),
    TerminalMacro("service-status","Service status","systemctl --user status -- {{unit}}"),
    TerminalMacro("service-start","Start user service","systemctl --user start -- {{unit}}"),TerminalMacro("service-stop","Stop user service","systemctl --user stop -- {{unit}}"),
    TerminalMacro("service-restart","Restart user service","systemctl --user restart -- {{unit}}"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun TerminalMacroDrawer(close: ()->Unit,insert: (String,Boolean)->Unit) {
    val context=LocalContext.current
    var error by remember { mutableStateOf("") }
    var macros by remember { mutableStateOf(runCatching {
        val rows=ConnectionVault(context).read().optJSONArray("terminalMacros") ?: JSONArray()
        require(rows.length()<=32)
        builtInMacros+(0 until rows.length()).map { i -> val row=rows.getJSONObject(i);TerminalMacro(row.getString("id"),row.getString("name"),row.getString("template"),row.optBoolean("run")) }
    }.getOrElse { error="Saved macros are unavailable. Built-in macros remain usable.";builtInMacros }) }
    var selected by remember { mutableStateOf<TerminalMacro?>(null) }
    var values by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf("") }
    var run by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    fun save(list: List<TerminalMacro>) {
        runCatching { val vault=ConnectionVault(context);val data=vault.read();val rows=JSONArray()
            list.filter { it.id !in builtInMacros.map { m -> m.id } }.forEach { m -> rows.put(JSONObject().put("id",m.id).put("name",m.name).put("template",m.template).put("run",m.run)) }
            require(rows.length()<=32);vault.write(data.put("terminalMacros",rows));macros=list
        }.onFailure { error="Macros could not be saved. The encrypted vault was not replaced." }
    }
    ModalBottomSheet(onDismissRequest=close) {
        Column(Modifier.fillMaxWidth().heightIn(max=600.dp).imePadding().padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("Terminal macros",style=MaterialTheme.typography.titleLarge)
            Text("Parameters use {{name}} as a standalone shell argument and are shell-quoted. Do not save passwords in templates.")
            if(error.isNotEmpty()) Text(error,color=Danger)
            if(creating) {
                OutlinedTextField(name,{ name=it.take(48) },label={ Text("Macro name") })
                OutlinedTextField(template,{ template=it.take(2048) },label={ Text("Command template") },modifier=Modifier.fillMaxWidth())
                Row { Checkbox(run,{ run=it });Text("Run after confirmation (otherwise insert only)") }
                Button(enabled=name.isNotBlank() && template.isNotBlank(),onClick={ runCatching { expandMacro(template,macroParameters(template).associateWith { "" });save(macros+TerminalMacro(name=name.trim(),template=template,run=run));creating=false;name="";template="" }.onFailure { error="Use a single-line command template, at most 2048 characters." } }) { Text("Save encrypted macro") }
            } else {
                macros.forEach { m -> Row {
                    TextButton(modifier=Modifier.weight(1f),onClick={ selected=m;values=emptyMap() }) { Text(m.name+if(m.run) " · run" else " · insert") }
                    if(m.id !in builtInMacros.map { it.id }) TextButton(onClick={ save(macros.filterNot { it.id==m.id }) }) { Text("Delete") }
                } }
                TextButton(onClick={ creating=true }) { Text("Create macro") }
            }
            selected?.let { m ->
                Text(m.name,style=MaterialTheme.typography.titleMedium)
                macroParameters(m.template).forEach { parameter -> OutlinedTextField(values[parameter].orEmpty(),{ values=values+(parameter to it) },label={ Text(parameter) },singleLine=true,modifier=Modifier.fillMaxWidth()) }
                Text(m.template)
                Button(onClick={ runCatching { val command=expandMacro(m.template,macroParameters(m.template).associateWith { values[it].orEmpty() });if(m.run) confirmation=command else insert(command,false) }.onFailure { error="Parameters must be single-line values and the expanded command at most 4096 characters." } }) { Text(if(m.run) "Review & run" else "Insert into command field") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    confirmation?.let { command -> AlertDialog(onDismissRequest={ confirmation=null },title={ Text("Execute this command?") },text={ Text(command) },confirmButton={ TextButton(onClick={ confirmation=null;insert(command,true) }) { Text("Run once") } },dismissButton={ TextButton(onClick={ confirmation=null }) { Text("Cancel") } }) }
}
