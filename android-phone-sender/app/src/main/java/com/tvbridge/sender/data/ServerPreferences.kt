package com.tvbridge.sender.data

import android.content.Context

/**
 * Persiste la IP del servidor de señalización en el dispositivo.
 */
class ServerPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getServerIp(): String = prefs.getString(KEY_SERVER_IP, "") ?: ""

    fun saveServerIp(ip: String) {
        prefs.edit().putString(KEY_SERVER_IP, ip.trim()).apply()
    }

    fun hasConfiguration(): Boolean = getServerIp().isNotBlank()

    fun isShareSystemAudioEnabled(): Boolean = prefs.getBoolean(KEY_SHARE_SYSTEM_AUDIO, true)

    fun setShareSystemAudioEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHARE_SYSTEM_AUDIO, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "tv_bridge_sender"
        private const val KEY_SERVER_IP = "server_ip"
        private const val KEY_SHARE_SYSTEM_AUDIO = "share_system_audio"
    }
}
