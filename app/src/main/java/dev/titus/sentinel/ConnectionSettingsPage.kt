package dev.titus.sentinel

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.titus.sentinel.monitor.*

@Composable internal fun ConnectionSettingsPage(config: ConnectionSettings, publicKey: String, status: MonitorUi,users: List<PcUser>,terminalPublicKey: String,
    save: (ConnectionSettings) -> String?, check: () -> Unit, erase: () -> Unit, profiles: List<SavedConnection>, selectProfile: (String)->String?,
    powerUi: PowerUi,checkPower: ()->Unit) {
    var deviceName by remember(config) { mutableStateOf(config.deviceName) }
    var host by remember(config) { mutableStateOf(config.host) }
    var port by remember(config) { mutableStateOf(config.port.toString()) }
    var user by remember(config) { mutableStateOf(config.username) }
    var fingerprint by remember(config) { mutableStateOf(config.fingerprint) }
    var feedback by remember { mutableStateOf("") }
    var keyFeedback by remember { mutableStateOf("") }
    val clipboard=LocalContext.current.getSystemService(ClipboardManager::class.java)
    var step by remember { mutableIntStateOf(0) }
    var submitted by remember { mutableStateOf(false) }
    val draft=ConnectionSettings(host.trim().removeSurrounding("[","]"),port.toIntOrNull() ?: 0,user.trim(),fingerprint.trim(),deviceName.trim())
    val stepTop=remember { BringIntoViewRequester() }
    LaunchedEffect(step) { androidx.compose.runtime.withFrameNanos { };stepTop.bringIntoView() }
    val validation=draft.validationError()
    val validPin="SHA256:"+"A".repeat(43)
    val hostError=draft.copy(port=22,username="user",fingerprint=validPin,deviceName="").validationError()
    val userError=draft.copy(host="example.com",port=22,fingerprint=validPin,deviceName="").validationError()
    val pinError=draft.copy(host="example.com",port=22,username="user",deviceName="").validationError()
    val nameError=draft.copy(host="example.com",port=22,username="user",fingerprint=validPin).validationError()
    if(profiles.isNotEmpty()) {
        Disclosure("Saved PCs & logins") {
            profiles.forEach { profile ->
                OutlinedButton(shape=RoundedCornerShape(4.dp),onClick={ feedback=selectProfile(profile.id) ?: "Saved connection selected.";submitted=false },modifier=Modifier.fillMaxWidth()) {
                    Text((if(profile.settings==config) "✓ " else "")+profile.settings.deviceName.ifBlank { profile.settings.host }+" · "+profile.settings.username)
                }
            }
            Note("Each PC and SSH login is saved separately. Phone keys are kept in Android Keystore.")
        }
        Spacer(Modifier.height(12.dp))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).bringIntoViewRequester(stepTop))
    Note("Three steps to connect your Linux PC. Use your existing private network or VPN; Sentinel does not configure it.")
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("Connection","Pair phone","Verify").forEachIndexed { index,label ->
            FilterChip(selected=step==index,onClick={ step=index },label={ Text("${index+1}. $label") },modifier=Modifier.heightIn(min=48.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
    when(step) {
        0 -> SurfaceCard {
            Eyebrow("STEP 1 OF 3")
            Spacer(Modifier.height(8.dp));Text("Connection details",style=MaterialTheme.typography.titleLarge)
            Note("Use the SSH login and host fingerprint supplied by your PC administrator.")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(deviceName,{ deviceName=it;feedback="" },label={ Text("Device name (optional)") },singleLine=true,modifier=Modifier.fillMaxWidth(),isError=submitted && nameError!=null,
                supportingText={ if(submitted && nameError!=null) Text(nameError) })
            OutlinedTextField(host,{ host=it;feedback="" },label={ Text("Hostname or IP address") },singleLine=true,modifier=Modifier.fillMaxWidth(),isError=submitted && hostError!=null,
                supportingText={ Text(if(submitted && hostError!=null) hostError else "For example: workstation.local or 192.168.1.20") },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri))
            OutlinedTextField(port,{ port=it;feedback="" },label={ Text("SSH port") },singleLine=true,modifier=Modifier.fillMaxWidth(),isError=submitted && draft.port !in 1..65535,
                supportingText={ if(submitted && draft.port !in 1..65535) Text("Enter a port from 1 to 65535.") },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            OutlinedTextField(user,{ user=it;feedback="" },label={ Text("SSH login") },singleLine=true,modifier=Modifier.fillMaxWidth(),isError=submitted && userError!=null,
                supportingText={ if(submitted && userError!=null) Text(userError) })
            OutlinedTextField(fingerprint,{ fingerprint=it;feedback="" },label={ Text("Verified host fingerprint") },modifier=Modifier.fillMaxWidth(),minLines=2,isError=submitted && pinError!=null,
                supportingText={ Text(if(submitted && pinError!=null) pinError else "Starts with SHA256:. Verify independently on your PC.") })
            Note("A changed fingerprint blocks access. Verify it independently before saving a replacement.")
            Spacer(Modifier.height(12.dp))
            Button(shape=RoundedCornerShape(4.dp),onClick={ submitted=true;if(validation==null) step=1 },modifier=Modifier.fillMaxWidth()) { Text("Continue to pairing") }
        }
        1 -> {
            SurfaceCard {
                Eyebrow("STEP 2 OF 3");Spacer(Modifier.height(8.dp));Text("Pair this phone",style=MaterialTheme.typography.titleLarge)
                Note("The monitoring key must use the restricted helper command. The private key stays on this phone.")
                Spacer(Modifier.height(12.dp))
                if(publicKey.isBlank()) Note("Opening phone key…") else {
                    OutlinedButton(shape=RoundedCornerShape(4.dp),onClick={ clipboard.setPrimaryClip(ClipData.newPlainText("Sentinel public key",publicKey));keyFeedback="Monitoring public key copied." },modifier=Modifier.fillMaxWidth()) { Text("Copy monitoring public key") }
                    Disclosure("View public key") { SelectionContainer { Text(publicKey,fontFamily=FontFamily.Monospace,fontSize=16.sp) } }
                }
                if(keyFeedback.isNotBlank()) Note(keyFeedback)
            }
            Spacer(Modifier.height(12.dp));SetupGuide(publicKey,initiallyExpanded=true)
            Spacer(Modifier.height(12.dp))
            Button(shape=RoundedCornerShape(4.dp),onClick={ step=2 },modifier=Modifier.fillMaxWidth()) { Text("Continue to verification") }
        }
        2 -> SurfaceCard {
            Eyebrow("STEP 3 OF 3");Spacer(Modifier.height(8.dp));Text("Save & verify",style=MaterialTheme.typography.titleLarge)
            PairLine("PC",draft.deviceName.ifBlank { draft.host.ifBlank { "Not entered" } })
            PairLine("SSH login",draft.username.ifBlank { "Not entered" })
            if(validation!=null) { Text(validation,color=Danger);TextButton(onClick={ step=0;submitted=true }) { Text("Fix connection details") } }
            Button(shape=RoundedCornerShape(4.dp),onClick={ feedback=save(draft) ?: "Connection saved. Test it below." },modifier=Modifier.fillMaxWidth(),enabled=validation==null && !status.busy && publicKey.isNotBlank()) { Text("Save connection") }
            if(feedback.isNotBlank()) Text(feedback,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
            Spacer(Modifier.height(12.dp))
            SampleBar(status.phase,status.busy,status.lastContactMillis,check,refreshEnabled=config.validationError()==null && config==draft)
            OutlinedButton(shape=RoundedCornerShape(4.dp),onClick=check,enabled=!status.busy && config.validationError()==null && config==draft,modifier=Modifier.fillMaxWidth()) { Text(if(status.busy) "Testing…" else "Test saved connection") }
            if(config!=draft) Note("Save your changes before testing. The test uses the selected saved connection.")
            if(status.phase==Phase.Online && config==draft) Text("✓ Monitoring connection verified",color=Accent)
            else Note("If pairing fails, review the restricted key entry and helper installation in Step 2.")
        }
    }
    Spacer(Modifier.height(12.dp))
    Disclosure("Discovered PC users") {
        Spacer(Modifier.height(8.dp))
        if(users.isEmpty()) Note("Test the saved connection to discover local login accounts. User discovery requires the current Sentinel helper.")
        else users.forEach { PairLine(it.name,"UID ${it.uid}") }
        Spacer(Modifier.height(8.dp))
        Note("The SSH login connects to the PC; these accounts filter its processes. Older helpers infer users from the process sample. Full account discovery requires the current helper.")
    }
    Spacer(Modifier.height(12.dp))
    Disclosure("Terminal access") {
        Note("Terminal uses a separate key with interactive shell access. Monitoring keeps its read-only key. Authorise this terminal key on the PC before connecting.")
        Spacer(Modifier.height(12.dp))
        if(terminalPublicKey.isBlank()) Note("Opening terminal key…") else {
            SelectionContainer { Text(terminalPublicKey,fontFamily=FontFamily.Monospace,fontSize=16.sp) }
            OutlinedButton(shape=RoundedCornerShape(4.dp),onClick={ clipboard.setPrimaryClip(ClipData.newPlainText("Sentinel terminal public key",terminalPublicKey));keyFeedback="Terminal public key copied." }) { Text("Copy terminal key") }
            if(keyFeedback.isNotBlank()) Note(keyFeedback)
        }
    }
    Spacer(Modifier.height(20.dp))
    Disclosure("Protected SSH shutdown") {
        Note("Uses the same SSH access already authorised for Terminal. No PC helper, new PC key, sudo rule, service or download is added. Every shutdown asks for the PC account’s administrator (sudo) password. It is masked, sent only over verified SSH and never saved.")
        Spacer(Modifier.height(10.dp))
        Note("The account must already have sudo permission. Authentication failures and shutdown inhibitors are reported; no sudo policy is changed. Shutdown respects blocking inhibitors and affects every user.")
        OutlinedButton(shape=RoundedCornerShape(4.dp),onClick=checkPower,enabled=!powerUi.busy) { Text("Verify PC authentication — no shutdown") }
        if(powerUi.message.isNotBlank()) Note(powerUi.message)
    }
    Spacer(Modifier.height(12.dp))
    PrivacySettings(erase)
    Spacer(Modifier.height(20.dp))
    Note("Sentinel · ${BuildConfig.VERSION_NAME}")
}
