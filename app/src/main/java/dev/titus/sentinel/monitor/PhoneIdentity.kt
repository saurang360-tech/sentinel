package dev.titus.sentinel.monitor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.jcraft.jsch.Identity
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** The SSH private key stays in Android Keystore; only signing handles leave it. */
class PhoneIdentity(context: Context, private val terminal: Boolean = false) : Identity {
    private val alias = if(terminal) "sentinel.terminal.ecdsa.v1" else "sentinel.status.ecdsa.v1"
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    init {
        if (!store.containsAlias(alias)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256).build())
            }.generateKeyPair()
        }
        // Public export for pairing; this contains no private key or credential secret.
        File(context.noBackupFilesDir,if(terminal) "sentinel-terminal-public-key.pub" else "sentinel-public-key.pub").writeText(publicLine() + "\n")
    }
    override fun getPublicKeyBlob(): ByteArray = SshEncoding.publicBlob(store.getCertificate(alias).publicKey as ECPublicKey)
    fun publicLine(): String = SshEncoding.ALGORITHM + " " + Base64.getEncoder().encodeToString(publicKeyBlob) + if(terminal) " sentinel-terminal" else " sentinel-monitor"
    override fun getSignature(data: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(store.getKey(alias,null) as PrivateKey); update(data); SshEncoding.signatureFromDer(sign())
    }
    override fun getSignature(data: ByteArray, alg: String): ByteArray {
        require(alg == SshEncoding.ALGORITHM); return getSignature(data)
    }
    override fun getAlgName(): String = SshEncoding.ALGORITHM
    override fun getName(): String = "Sentinel device key"
    override fun isEncrypted(): Boolean = false
    override fun setPassphrase(passphrase: ByteArray?): Boolean = passphrase == null || passphrase.isEmpty()
    override fun clear() { /* Keystore retains the key across SSH connection disposal. */ }
}
