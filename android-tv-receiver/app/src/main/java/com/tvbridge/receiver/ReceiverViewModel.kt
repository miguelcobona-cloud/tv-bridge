package com.tvbridge.receiver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvbridge.receiver.data.ServerPreferences
import com.tvbridge.receiver.signaling.SignalingClient
import com.tvbridge.receiver.ui.AppScreen
import com.tvbridge.receiver.ui.ConnectionStatus
import com.tvbridge.receiver.ui.ReceiverUiState
import com.tvbridge.receiver.webrtc.WebRtcReceiverSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

/**
 * Orquesta señalización WebSocket, negociación WebRTC y navegación entre pantallas.
 */
class ReceiverViewModel(application: Application) : AndroidViewModel(application), SignalingClient.SignalingListener {

    private val preferences = ServerPreferences(application)
    private val signalingClient = SignalingClient(this)
    private val eglBase: EglBase = EglBase.create()

    private var webRtcSession: WebRtcReceiverSession? = null
    private var boundRenderer: SurfaceViewRenderer? = null

    private val _uiState = MutableStateFlow(ReceiverUiState())
    val uiState: StateFlow<ReceiverUiState> = _uiState.asStateFlow()

    init {
        val savedIp = preferences.getServerIp()
        val savedName = preferences.getTvName()
        _uiState.update {
            it.copy(
                serverIp = savedIp,
                tvName = savedName,
                screen = if (preferences.hasConfiguration()) AppScreen.Waiting else AppScreen.Setup,
            )
        }

        if (preferences.hasConfiguration()) {
            connectToSignalingServer(savedIp, savedName)
        }
    }

    fun onServerIpChange(value: String) {
        _uiState.update { it.copy(serverIp = value, errorMessage = null) }
    }

    fun onTvNameChange(value: String) {
        _uiState.update { it.copy(tvName = value, errorMessage = null) }
    }

    fun saveAndConnect() {
        val state = _uiState.value
        val ip = state.serverIp.trim()
        val name = state.tvName.trim()

        when {
            ip.isBlank() -> {
                _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.error_ip_required)) }
                return
            }
            name.isBlank() -> {
                _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.error_name_required)) }
                return
            }
        }

        preferences.saveConfiguration(ip, name)
        _uiState.update {
            it.copy(
                screen = AppScreen.Waiting,
                errorMessage = null,
            )
        }
        connectToSignalingServer(ip, name)
    }

    fun openSetup() {
        signalingClient.disconnect()
        webRtcSession?.close()
        webRtcSession = null
        _uiState.update {
            it.copy(
                screen = AppScreen.Setup,
                connectionStatus = ConnectionStatus.Idle,
                statusMessage = "",
                tvId = null,
            )
        }
    }

    fun bindVideoRenderer(renderer: SurfaceViewRenderer) {
        boundRenderer = renderer
        webRtcSession?.attachRenderer(renderer)
    }

    fun unbindVideoRenderer(renderer: SurfaceViewRenderer) {
        webRtcSession?.detachRenderer(renderer)
        if (boundRenderer === renderer) {
            boundRenderer = null
        }
    }

    private fun connectToSignalingServer(serverIp: String, tvName: String) {
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.Connecting,
                statusMessage = getApplication<Application>().getString(R.string.status_connecting),
                errorMessage = null,
            )
        }
        signalingClient.connect(serverIp, tvName)
    }

    private fun ensureWebRtcSession() {
        if (webRtcSession != null) return

        webRtcSession = WebRtcReceiverSession(
            context = getApplication(),
            eglBase = eglBase,
            onSignalOut = { payload -> signalingClient.sendSignal(payload) },
            onStreamingStarted = {
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            screen = AppScreen.Streaming,
                            connectionStatus = ConnectionStatus.Streaming,
                            statusMessage = getApplication<Application>().getString(R.string.status_streaming),
                        )
                    }
                }
            },
            onStreamingEnded = {
                viewModelScope.launch {
                    webRtcSession?.close()
                    webRtcSession = null
                    _uiState.update {
                        it.copy(
                            screen = AppScreen.Waiting,
                            connectionStatus = ConnectionStatus.Connected,
                            statusMessage = getApplication<Application>().getString(R.string.status_connected),
                        )
                    }
                }
            },
            onError = { message ->
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.Error,
                            errorMessage = message,
                        )
                    }
                }
            },
        )

        boundRenderer?.let { renderer ->
            webRtcSession?.attachRenderer(renderer)
        }
    }

    override fun onConnected() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Connecting,
                    statusMessage = getApplication<Application>().getString(R.string.status_connecting),
                )
            }
        }
    }

    override fun onRegistered(tvId: String, tvName: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    tvId = tvId,
                    tvName = tvName,
                    connectionStatus = ConnectionStatus.Connected,
                    statusMessage = getApplication<Application>().getString(R.string.status_connected),
                    screen = if (it.screen == AppScreen.Streaming) AppScreen.Streaming else AppScreen.Waiting,
                )
            }
        }
    }

    override fun onSignal(from: String, payload: org.json.JSONObject) {
        ensureWebRtcSession()
        webRtcSession?.handleSignal(payload)
    }

    override fun onError(message: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Error,
                    errorMessage = message,
                    statusMessage = getApplication<Application>().getString(R.string.status_error),
                )
            }
        }
    }

    override fun onDisconnected() {
        viewModelScope.launch {
            webRtcSession?.close()
            webRtcSession = null
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Disconnected,
                    statusMessage = getApplication<Application>().getString(R.string.status_disconnected),
                )
            }
        }
    }

    override fun onCleared() {
        signalingClient.disconnect()
        webRtcSession?.release()
        eglBase.release()
        super.onCleared()
    }
}
