package dev.titus.sentinel

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.titus.sentinel.monitor.AppAccess

@Composable internal fun LegalDialog(file: String, title: String, close: () -> Unit) {
    val context = LocalContext.current
    val text = remember(file) { context.assets.open("legal/$file").bufferedReader().use { it.readText() } }
    AlertDialog(onDismissRequest=close, title={ Text(title) },
        text={ SelectionContainer { Text(text, modifier=Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState())) } },
        confirmButton={ TextButton(onClick=close) { Text("Close") } })
}

@Composable internal fun PrivacyLinks() {
    var document by remember { mutableStateOf<String?>(null) }
    TextButton(onClick={ document="privacy-policy.txt" }) { Text("Privacy policy") }
    TextButton(onClick={ document="terms.txt" }) { Text("Terms of use") }
    TextButton(onClick={ document="third-party-notices.txt" }) { Text("Open-source licences") }
    document?.let { file -> LegalDialog(file, when(file) {
        "privacy-policy.txt" -> "Privacy policy"; "terms.txt" -> "Terms of use"; else -> "Open-source licences"
    }) { document=null } }
}

@Composable internal fun AccessGate(content: @Composable () -> Unit, demoContent: @Composable (() -> Unit) -> Unit) {
    val context=LocalContext.current
    var acknowledged by remember { mutableStateOf(AppAccess.acknowledged(context)) }
    var allowed by remember { mutableStateOf(AppAccess.networkGranted(context)) }
    var requested by remember { mutableStateOf(false) }
    var acceptedTerms by remember { mutableStateOf(false) }
    var demo by remember { mutableStateOf(false) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed=it;requested=true }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_RESUME) allowed=AppAccess.networkGranted(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if(demo) { demoContent { demo=false };return }
    if(acknowledged && allowed) { content();return }
    MaterialTheme(colorScheme=darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState())) {
                Text(if(!acknowledged) "Welcome to Sentinel" else "Connect to your PC",style=MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(20.dp))
                Text("Monitor your Linux PC over your existing SSH connection. A compatible Sentinel helper is required on the PC. Terminal access is optional and uses a separate key.")
                Spacer(Modifier.height(16.dp))
                Text("Your data stays between your phone and the PC you configure. Sentinel has no developer backend, accounts, advertising, analytics or automatic crash reporting. It does not scan your network.")
                Spacer(Modifier.height(16.dp))
                Text("Connection settings are saved on this phone. Live readings and terminal output stay in memory. SSH sends your login, public key and requests to your PC; terminal commands may change files or settings and may be recorded by your PC.")
                PrivacyLinks()
                TextButton(onClick={ demo=true }) { Text("Explore demo — no connection") }
                if(!acknowledged) {
                    Row { Checkbox(acceptedTerms,{ acceptedTerms=it });Text("I have read the privacy notice and agree to the terms of use.",Modifier.padding(top=12.dp)) }
                    Button(onClick={ AppAccess.acknowledge(context);acknowledged=true },enabled=acceptedTerms) { Text("Continue") }
                } else {
                    Spacer(Modifier.height(16.dp))
                    Text("Android requires nearby-device permission for local-network SSH connections on this version. Sentinel uses it only to reach your configured PC. It does not access contacts, photos, microphone or location.")
                    Button(onClick={ launcher.launch(AppAccess.LOCAL_NETWORK) }) { Text("Allow PC connection") }
                    if(requested) {
                        Text("Access was not granted. You can try again or change the permission in Android Settings.")
                        TextButton(onClick={ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) }) { Text("Open Android Settings") }
                    }
                }
            }
        }
    }
}

@Composable internal fun PrivacySettings(beforeErase: () -> Unit) {
    val context=LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var eraseError by remember { mutableStateOf("") }
    SurfaceCard {
        Eyebrow("PRIVACY & APP DATA")
        Spacer(Modifier.height(8.dp))
        Note("No developer data collection, ads or analytics. Settings stay on this phone; live readings and terminal output are memory-only. Android app backup is disabled.")
        PrivacyLinks()
        OutlinedButton(onClick={ confirm=true },modifier=Modifier.fillMaxWidth()) { Text("Erase local app data") }
        Note("To revoke PC access, remove this phone’s Sentinel public keys from the PC’s authorised-key file. Erasing the phone does not edit your PC.")
        if(eraseError.isNotBlank()) Text(eraseError)
    }
    if(confirm) AlertDialog(onDismissRequest={ confirm=false },title={ Text("Erase Sentinel data?") },
        text={ Text("This disconnects sessions, deletes saved connection settings, acknowledgements and both SSH keys, and closes Sentinel. You will need to pair again. It does not remove authorised keys or logs on your PC, or erase files you exported or copied.") },
        confirmButton={ TextButton(onClick={
            runCatching {
                beforeErase()
                java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.let { store ->
                    listOf("sentinel.status.ecdsa.v1","sentinel.terminal.ecdsa.v1","sentinel.power.ecdsa.v1","sentinel.connections.aes.v1").forEach { store.deleteEntry(it) }
                }
                check(context.getSystemService(ActivityManager::class.java).clearApplicationUserData())
            }.onFailure {
                confirm=false
                eraseError="Android could not complete erasure. Close Sentinel and use Android Settings → Apps → Sentinel → Storage → Clear storage before reconnecting."
            }
        }) { Text("Erase and close") } },
        dismissButton={ TextButton(onClick={ confirm=false }) { Text("Cancel") } })
}
