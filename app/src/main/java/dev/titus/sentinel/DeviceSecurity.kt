package dev.titus.sentinel

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DeviceSecurity(private val activity: ComponentActivity) {
    private val unlocked=MutableStateFlow(false)
    val session=unlocked.asStateFlow()
    @Volatile var authenticating=false
        private set
    private var cancellation: CancellationSignal?=null
    private var credentialDone: ((Boolean)->Unit)?=null
    private val credentialLauncher=activity.registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val done=credentialDone;credentialDone=null;authenticating=false
        val ok=result.resultCode==android.app.Activity.RESULT_OK && !activity.getSystemService(KeyguardManager::class.java).isDeviceLocked
        if(ok) unlocked.value=true
        done?.invoke(ok)
    }
    fun lock() { unlocked.value=false }
    fun cancel() { cancellation?.cancel();credentialDone=null;authenticating=false;lock() }
    fun ready()=activity.getSystemService(KeyguardManager::class.java).isDeviceSecure
    fun authenticate(title: String,done: (Unit?,Boolean)->Unit) {
        activity.runOnUiThread {
            if(authenticating || !ready() || activity.isFinishing || activity.isDestroyed ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { done(null,false);return@runOnUiThread }
            if(Build.VERSION.SDK_INT<30) {
                val intent=activity.getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent(title,"Verify your phone screen lock")
                if(intent==null) { done(null,false);return@runOnUiThread }
                authenticating=true;credentialDone={ ok -> done(null,ok) };credentialLauncher.launch(intent);return@runOnUiThread
            }
            val signal=CancellationSignal();cancellation=signal;authenticating=true
            val builder=BiometricPrompt.Builder(activity).setTitle(title)
                .setSubtitle("Use your fingerprint or phone screen lock")
            if(Build.VERSION.SDK_INT>=30) builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)

            val callback=object: BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if(cancellation!==signal) return
                    cancellation=null;authenticating=false
                    val safe=activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                        !activity.getSystemService(KeyguardManager::class.java).isDeviceLocked && !activity.isFinishing
                    if(safe) unlocked.value=true
                    done(null,safe)
                }
                override fun onAuthenticationError(code: Int,message: CharSequence) {
                    if(cancellation!==signal) return
                    cancellation=null;authenticating=false;done(null,false)
                }
            }
            try {
                builder.build().authenticate(signal,activity.mainExecutor,callback)
            } catch(_: Exception) { cancellation=null;authenticating=false;done(null,false) }
        }
    }
}

