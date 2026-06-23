package com.tvbridge.receiver.signaling

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente WebSocket OkHttp para el protocolo TV-Bridge (ver signaling-server/server.js).
 *
 * Mensajes salientes:
 *   register-tv  → { type, name }
 *   signal       → { type, payload }
 *
 * Mensajes entrantes:
 *   registered → { type, id, name }
 *   signal   → { type, from, payload }
 *   error    → { type, message }
 */
class SignalingClient(
    private val listener: SignalingListener,
) {
    interface SignalingListener {
        fun onConnected()
        fun onRegistered(tvId: String, tvName: String)
        fun onSignal(from: String, payload: JSONObject)
        fun onError(message: String)
        fun onDisconnected()
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_SEC, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    fun connect(serverIp: String, tvName: String) {
        disconnect()

        val wsUrl = buildWebSocketUrl(serverIp)
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onConnected()
                sendRegisterTv(tvName)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSURE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onError(t.message ?: "Error de conexión WebSocket")
                listener.onDisconnected()
            }
        })
    }

    fun sendSignal(payload: JSONObject) {
        val message = JSONObject()
            .put("type", "signal")
            .put("payload", payload)
        sendJson(message)
    }

    fun disconnect() {
        webSocket?.close(NORMAL_CLOSURE, "Client disconnect")
        webSocket = null
    }

    private fun sendRegisterTv(tvName: String) {
        val message = JSONObject()
            .put("type", "register-tv")
            .put("name", tvName)
        sendJson(message)
    }

    private fun sendJson(json: JSONObject) {
        webSocket?.send(json.toString())
    }

    private fun handleMessage(raw: String) {
        val message = try {
            JSONObject(raw)
        } catch (_: Exception) {
            listener.onError("Mensaje JSON inválido del servidor")
            return
        }

        when (message.optString("type")) {
            "registered" -> {
                val id = message.optString("id")
                val name = message.optString("name")
                if (id.isNotBlank() && name.isNotBlank()) {
                    listener.onRegistered(id, name)
                } else {
                    listener.onError("Respuesta registered incompleta")
                }
            }

            "signal" -> {
                val from = message.optString("from")
                val payload = message.optJSONObject("payload")
                if (from.isNotBlank() && payload != null) {
                    listener.onSignal(from, payload)
                } else {
                    listener.onError("Señal WebRTC incompleta")
                }
            }

            "error" -> {
                listener.onError(message.optString("message", "Error desconocido"))
            }

            else -> {
                listener.onError("Tipo de mensaje desconocido: ${message.optString("type")}")
            }
        }
    }

    private fun buildWebSocketUrl(serverIp: String): String {
        val host = serverIp.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")

        // Puerto 3000 por defecto (signaling-server/server.js)
        val hostWithPort = if (host.contains(":")) host else "$host:3000"
        return "ws://$hostWithPort/"
    }

    companion object {
        private const val PING_INTERVAL_SEC = 30L
        private const val NORMAL_CLOSURE = 1000
    }
}
