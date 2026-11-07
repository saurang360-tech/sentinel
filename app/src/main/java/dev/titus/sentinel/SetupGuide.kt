package dev.titus.sentinel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun SetupGuide(publicKey: String) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var feedback by remember { mutableStateOf("") }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-python")) { uri ->
        if(uri!=null) scope.launch {
            feedback=withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        context.assets.open("sentinel-monitor.py").use { it.copyTo(output) }
                    } ?: error("No output stream")
                    "Helper saved. Review it before installing on your PC."
                }.getOrElse { "Could not save the helper. Choose another location and try again." }
            }
        }
    }
    Disclosure("Set up a Linux PC") {
        Note("1. Use your existing LAN or VPN route and SSH service. Sentinel does not configure networking or install services. You need Python 3 on the PC and administrator help to install its read-only helper.")
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick={ export.launch("sentinel-monitor.py") },modifier=Modifier.fillMaxWidth()) { Text("Save Linux helper") }
        if(feedback.isNotBlank()) Note(feedback)
        Note("2. Review and transfer the helper to the PC. An administrator should install it as /usr/local/libexec/sentinel-monitor.py, owned by root and not writable by the SSH login. Verify the Python path and SELinux context on your distribution.")
        Spacer(Modifier.height(10.dp))
        Note("3. Obtain the PC’s SSH host fingerprint independently from its administrator. For example, run this locally on the PC for the host key SSH actually uses:")
        SelectionContainer { Text("ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub -E sha256") }
        Spacer(Modifier.height(10.dp))
        Note("4. Append the following entry to your SSH login’s configured authorised-key file. Preserve existing entries and correct ownership/permissions. The file may be managed outside ~/.ssh; check your SSH configuration first. Never add this monitoring key as an unrestricted login key.")
        if(publicKey.isNotBlank()) SelectionContainer {
            Text("restrict,command=\"/usr/bin/python3 -I /usr/local/libexec/sentinel-monitor.py\" $publicKey")
        }
        Spacer(Modifier.height(10.dp))
        Note("An optional from= restriction can limit the key to your phone’s stable VPN address. Use the real address for your setup. The helper runs with the SSH account’s existing permissions and never needs sudo rights.")
        Spacer(Modifier.height(10.dp))
        Note("5. Enter the connection and verified fingerprint above, save, then test. CPU/RAM sorting and user discovery require this compatible helper. Unsupported sensors stay unavailable. Only local login accounts are discovered; directory accounts may be absent.")
        Spacer(Modifier.height(10.dp))
        Note("Optional terminal: authorising the separate terminal key permits commands with your login’s normal privileges. Ask your administrator to authorise it with restrict,pty and your configured login shell; keep forwarding disabled. Do not reuse the read-only monitoring entry. This app provides a basic shell, not remote desktop or Kitty mirroring.")
        Spacer(Modifier.height(10.dp))
        Note("To revoke access, remove only this phone’s Sentinel key entries. After all Sentinel phones are revoked, the administrator may remove the helper. No other SSH keys or services need to change.")
    }
}
