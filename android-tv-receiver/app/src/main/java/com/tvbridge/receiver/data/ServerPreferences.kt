package com.tvbridge.receiver.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistencia local de la configuración del servidor de señalización.
 * La IP se conserva entre reinicios para un despliegue offline-first en LAN.
 */
class ServerPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getServerIp(): String = prefs.getString(KEY_SERVER_IP, "").orEmpty()

    fun getTvName(): String = prefs.getString(KEY_TV_NAME, DEFAULT_TV_NAME).orEmpty()

    fun hasConfiguration(): Boolean = getServerIp().isNotBlank() && getTvName().isNotBlank()

    fun saveConfiguration(serverIp: String, tvName: String) {
        prefs.edit()
            .putString(KEY_SERVER_IP, serverIp.trim())
            .putString(KEY_TV_NAME, tvName.trim())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "tv_bridge_prefs"
        private const val KEY_SERVER_IP = "server_ip"
        private const val KEY_TV_NAME = "tv_name"
        private const val DEFAULT_TV_NAME = "Android TV"
    }
}
