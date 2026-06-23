package com.tvbridge.sender.signaling

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente WebSocket del emisor (mismo protocolo que public/index.js).
 *
 * Saliente: register-web | signal { targetId, payload }
 * Entrante: tv-list | signal { from, payload } | error
 */
class EmitterSignalingClient(
    private val listener: EmitterListener,
) {
    interface EmitterListener {
        fun onConnected()
        fun onTvListUpdated(tvs: List<TvInfo>)
        fun onSignal(fromTvId: String, payload: JSONObject)
        fun onError(message: String)
        fun onDisconnected()
    }

    data class TvInfo(
        val id: String,
        val name: String,
    )

    private val client = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_SEC, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    fun connect(serverIp: String) {
        disconnect()

        val request = Request.Builder()
            .url(buildWebSocketUrl(serverIp))
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onConnected()
                sendRegisterWeb()
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

    fun sendSignal(targetId: String, payload: JSONObject) {
        val message = JSONObject()
            .put("type", "signal")
            .put("targetId", targetId)
            .put("payload", payload)
        sendJson(message)
    }

    fun disconnect() {
        webSocket?.close(NORMAL_CLOSURE, "Emitter disconnect")
        webSocket = null
    }

    private fun sendRegisterWeb() {
        sendJson(JSONObject().put("type", "register-web"))
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
            "tv-list" -> {
                val tvsArray = message.optJSONArray("tvs") ?: return
                val tvs = buildList {
                    for (index in 0 until tvsArray.length()) {
                        val tv = tvsArray.optJSONObject(index) ?: continue
                        val id = tv.optString("id")
                        val name = tv.optString("name")
                        if (id.isNotBlank() && name.isNotBlank()) {
                            add(TvInfo(id, name))
                        }
                    }
                }
                listener.onTvListUpdated(tvs)
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

            "error" -> listener.onError(message.optString("message", "Error desconocido"))

            else -> listener.onError("Tipo de mensaje desconocido: ${message.optString("type")}")
        }
    }

    private fun buildWebSocketUrl(serverIp: String): String {
        val host = serverIp.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")

        val hostWithPort = if (host.contains(":")) host else "$host:3000"
        return "ws://$hostWithPort/"
    }

    companion object {
        private const val PING_INTERVAL_SEC = 30L
        private const val NORMAL_CLOSURE = 1000
    }
}
