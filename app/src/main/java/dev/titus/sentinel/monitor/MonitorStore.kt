package dev.titus.sentinel.monitor

import android.content.Context
import org.json.JSONObject

internal fun decodeSnapshot(raw: String): Snapshot {
    require(raw.toByteArray().size <= 32768)
    val json = JSONObject(raw)
    require(json.getInt("protocol") == 1)
    return Snapshot(json.getString("host"),json.getString("bootId"),json.getDouble("uptimeSeconds"),
        json.getString("systemState"),json.getString("sampledAt"),decodeUsers(json)).also { it.validate() }
}
class MonitorStore(context: Context) {
    private val vault=ConnectionVault(context)
    var storageError: String?=null
        private set
    private fun load(): JSONObject? = runCatching { vault.read() }.onFailure {
        storageError="Saved connections could not be decrypted. Unlock the phone and reopen Sentinel. Do not erase data unless you intend to pair again."
    }.getOrNull()
    init { load() }
    fun profiles(): List<SavedConnection> = load()?.getJSONArray("profiles")?.let { rows ->
        (0 until rows.length()).map { ConnectionVault.decode(rows.getJSONObject(it)) }
    }.orEmpty()
    fun settings(): ConnectionSettings {
        val data=load() ?: return ConnectionSettings()
        val rows=data.getJSONArray("profiles")
        return (0 until rows.length()).map { ConnectionVault.decode(rows.getJSONObject(it)) }
            .find { it.id==data.getString("selected") }?.settings ?: ConnectionSettings()
    }
    fun saveSettings(config: ConnectionSettings) {
        require(config.validationError()==null)
        val data=load() ?: error("Saved connections are locked or damaged")
        val rows=data.getJSONArray("profiles")
        val profiles=(0 until rows.length()).map { ConnectionVault.decode(rows.getJSONObject(it)) }.toMutableList()
        val index=profiles.indexOfFirst { it.settings.host.equals(config.host,true) && it.settings.port==config.port && it.settings.username==config.username }
        val profile=SavedConnection(if(index>=0) profiles[index].id else java.util.UUID.randomUUID().toString(),config)
        if(index>=0) profiles[index]=profile else { require(profiles.size<64);profiles.add(profile) }
        val saved=org.json.JSONArray();profiles.forEach { saved.put(ConnectionVault.encode(it)) }
        data.put("profiles",saved).put("selected",profile.id);vault.write(data)
    }
    fun select(id: String): ConnectionSettings {
        val data=load() ?: error("Saved connections are locked or damaged")
        val rows=data.getJSONArray("profiles")
        val profile=(0 until rows.length()).map { ConnectionVault.decode(rows.getJSONObject(it)) }.first { it.id==id }
        require(profile.settings.validationError()==null)
        data.put("selected",id);vault.write(data);return profile.settings
    }
    fun cached(): MonitorUi = MonitorUi()
}
