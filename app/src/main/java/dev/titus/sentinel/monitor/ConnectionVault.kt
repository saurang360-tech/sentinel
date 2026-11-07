package dev.titus.sentinel.monitor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

data class SavedConnection(val id: String, val settings: ConnectionSettings)
internal class ConnectionVault(context: Context) {
    private val prefs=context.getSharedPreferences("sentinel-status",Context.MODE_PRIVATE)
    private val keystore=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun key(): SecretKey {
        if(!keystore.containsAlias(ALIAS)) KeyGenerator.getInstance("AES","AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUnlockedDeviceRequired(true).build())
        }.generateKey()
        return keystore.getKey(ALIAS,null) as SecretKey
    }
    fun read(): JSONObject {
        val encrypted=prefs.getString("vault",null)
        if(encrypted!=null) {
            val envelope=JSONObject(encrypted)
            require(envelope.getInt("version")==1)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.getDecoder().decode(envelope.getString("iv"))))
            cipher.updateAAD(AAD)
            return JSONObject(String(cipher.doFinal(Base64.getDecoder().decode(envelope.getString("data"))),Charsets.UTF_8))
        }
        val profiles=JSONArray()
        var selected=""
        if(prefs.contains("host") || prefs.contains("username")) {
            selected=UUID.randomUUID().toString()
            profiles.put(encode(SavedConnection(selected,ConnectionSettings(prefs.getString("host","")!!,
                prefs.getInt("port",22),prefs.getString("username","")!!,prefs.getString("fingerprint","")!!,
                prefs.getString("deviceName","")!!))))
        }
        return JSONObject().put("profiles",profiles).put("selected",selected).also { write(it) }
    }
    fun write(data: JSONObject) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(AAD)
        val bytes=cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8))
        val envelope=JSONObject().put("version",1).put("iv",Base64.getEncoder().encodeToString(cipher.iv))
            .put("data",Base64.getEncoder().encodeToString(bytes))
        // Only remove the old plaintext after the encrypted replacement is ready.
        check(prefs.edit().clear().putString("vault",envelope.toString()).commit())
    }
    companion object {
        const val ALIAS="sentinel.connections.aes.v1"
        private val AAD="Sentinel connection profiles v1".toByteArray()
        fun encode(profile: SavedConnection)=JSONObject().put("id",profile.id).put("host",profile.settings.host)
            .put("port",profile.settings.port).put("username",profile.settings.username)
            .put("fingerprint",profile.settings.fingerprint).put("deviceName",profile.settings.deviceName)
        fun decode(j: JSONObject)=SavedConnection(j.getString("id"),ConnectionSettings(j.getString("host"),j.getInt("port"),
            j.getString("username"),j.getString("fingerprint"),j.getString("deviceName")))
    }
}
