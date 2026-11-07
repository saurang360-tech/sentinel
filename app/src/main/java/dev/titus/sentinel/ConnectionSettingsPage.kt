package dev.titus.sentinel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    if(profiles.isNotEmpty()) {
        Disclosure("Saved PCs & logins") {
            profiles.forEach { profile ->
                OutlinedButton(onClick={ feedback=selectProfile(profile.id) ?: "Saved connection selected." },modifier=Modifier.fillMaxWidth()) {
                    Text(profile.settings.deviceName.ifBlank { profile.settings.host }+" · "+profile.settings.username)
                }
            }
            Note("Each host, port and SSH login is remembered separately after Save connection. Existing phone keys stay in Android Keystore; authorise them once for each PC account. Sentinel does not store SSH passwords.")
        }
        Spacer(Modifier.height(12.dp))
    }
    SurfaceCard {
        Eyebrow("CONNECTION")
        Spacer(Modifier.height(8.dp))
        Note("Connect over Tailscale or your private network. The PC needs Linux, SSH and the Sentinel helper.")
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(deviceName,{ deviceName=it;feedback="" },label={ Text("Device name (optional)") },singleLine=true,modifier=Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(host,{ host=it; feedback="" },label={ Text("Hostname or IP address") },singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(port,{ port=it; feedback="" },label={ Text("SSH port") },singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(user,{ user=it; feedback="" },label={ Text("SSH login") },singleLine=true,modifier=Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(fingerprint,{ fingerprint=it; feedback="" },label={ Text("Host fingerprint") },modifier=Modifier.fillMaxWidth(),minLines=2)
        Spacer(Modifier.height(8.dp))
        Note("Verify a changed fingerprint independently before saving. A mismatch blocks the connection.")
        Spacer(Modifier.height(14.dp))
        Button(onClick={ feedback=save(ConnectionSettings(host.trim().removeSurrounding("[","]"),port.toIntOrNull() ?: 0,user.trim(),fingerprint.trim(),deviceName.trim())) ?: "Connection saved." },modifier=Modifier.fillMaxWidth(),enabled=!status.busy && publicKey.isNotEmpty()) { Text("Save connection") }
        if(feedback.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Note(feedback) }
    }
    Spacer(Modifier.height(12.dp))
    SetupGuide(publicKey)
    Spacer(Modifier.height(12.dp))
    Disclosure("Security & pairing") {

        Note("Install the Sentinel helper on the PC, then authorise this public key with its read-only forced command. Keys from other apps are not imported.")
        Spacer(Modifier.height(16.dp))
        Eyebrow("THIS PHONE’S PUBLIC KEY")
        Spacer(Modifier.height(8.dp))
        Note("The private key stays securely on this phone.")
        Spacer(Modifier.height(12.dp))
        if(publicKey.isEmpty()) Note("Opening device key…") else {
            SelectionContainer { Text(publicKey,fontFamily=FontFamily.Monospace,fontSize=10.sp) }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick={ clipboard.setPrimaryClip(ClipData.newPlainText("Sentinel public key",publicKey)); keyFeedback="Public key copied." },modifier=Modifier.fillMaxWidth()) { Text("Copy public key") }
            if(keyFeedback.isNotEmpty()) Note(keyFeedback)
        }
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Eyebrow("CONNECTION TEST")
        Spacer(Modifier.height(10.dp))
        Text(statusLabel(status.phase),fontSize=16.sp)
        if(status.phase!=Phase.Online) { Spacer(Modifier.height(7.dp)); Note(statusMessage(status.phase)) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick=check,enabled=!status.busy && publicKey.isNotEmpty(),modifier=Modifier.fillMaxWidth()) { Text(if(status.busy) "Checking…" else "Test connection") }
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Eyebrow("PC USERS")
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
            SelectionContainer { Text(terminalPublicKey,fontFamily=FontFamily.Monospace,fontSize=10.sp) }
            OutlinedButton(onClick={ clipboard.setPrimaryClip(ClipData.newPlainText("Sentinel terminal public key",terminalPublicKey));keyFeedback="Terminal public key copied." }) { Text("Copy terminal key") }
            if(keyFeedback.isNotBlank()) Note(keyFeedback)
        }
    }
    Spacer(Modifier.height(20.dp))
    Disclosure("Protected SSH shutdown") {
        Note("Uses the same SSH access already authorised for Terminal. No PC helper, new PC key, sudo rule, service or download is added. Every shutdown asks for the PC account’s administrator (sudo) password. It is masked, sent only over verified SSH and never saved.")
        Spacer(Modifier.height(10.dp))
        Note("The account must already have sudo permission. Authentication failures and shutdown inhibitors are reported; no sudo policy is changed. Shutdown respects blocking inhibitors and affects every user.")
        OutlinedButton(onClick=checkPower,enabled=!powerUi.busy) { Text("Verify PC authentication — no shutdown") }
        if(powerUi.message.isNotBlank()) Note(powerUi.message)
    }
    Spacer(Modifier.height(12.dp))
    PrivacySettings(erase)
    Spacer(Modifier.height(20.dp))
    Note("Sentinel · ${BuildConfig.VERSION_NAME}")
}
