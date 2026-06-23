package com.tvbridge.sender

import android.app.Application
import android.content.Intent
import android.media.projection.MediaProjection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvbridge.sender.data.ServerPreferences
import com.tvbridge.sender.service.ProjectionForegroundService
import com.tvbridge.sender.signaling.EmitterSignalingClient
import com.tvbridge.sender.ui.AppScreen
import com.tvbridge.sender.ui.ConnectionStatus
import com.tvbridge.sender.ui.SenderUiState
import com.tvbridge.sender.ui.ProjectionRequest
import com.tvbridge.sender.ui.TvItemUi
import com.tvbridge.sender.webrtc.WebRtcSenderSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.webrtc.EglBase

class SenderViewModel(application: Application) : AndroidViewModel(application), EmitterSignalingClient.EmitterListener {

    private val preferences = ServerPreferences(application)
    private val signalingClient = EmitterSignalingClient(this)
    private val eglBase: EglBase = EglBase.create()
    private var webRtcSession: WebRtcSenderSession? = null
    private var pendingTv: TvItemUi? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private val sessionLock = Any()

    /** Solo para inicializar WebRTC y señalización; MediaProjection va en el hilo principal. */
    private val webRtcDispatcher = Dispatchers.Default.limitedParallelism(1)

    private val _uiState = MutableStateFlow(SenderUiState())
    val uiState: StateFlow<SenderUiState> = _uiState.asStateFlow()

    private val _projectionRequests = MutableSharedFlow<ProjectionRequest>(extraBufferCapacity = 1)
    val projectionRequests: SharedFlow<ProjectionRequest> = _projectionRequests.asSharedFlow()

    init {
        val savedIp = preferences.getServerIp()
        val shareAudio = preferences.isShareSystemAudioEnabled()
        _uiState.update {
            it.copy(
                serverIp = savedIp,
                shareSystemAudio = shareAudio,
                screen = if (preferences.hasConfiguration()) AppScreen.TvList else AppScreen.Setup,
            )
        }
        if (preferences.hasConfiguration()) {
            connectToServer(savedIp)
        }
    }

    fun onServerIpChange(value: String) {
        _uiState.update { it.copy(serverIp = value, errorMessage = null) }
    }

    fun connect() {
        val ip = _uiState.value.serverIp.trim()
        if (ip.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = getApplication<Application>().getString(R.string.error_ip_required))
            }
            return
        }

        preferences.saveServerIp(ip)
        _uiState.update {
            it.copy(
                screen = AppScreen.TvList,
                errorMessage = null,
            )
        }
        connectToServer(ip)
    }

    fun applyServerFromLink(rawLink: String) {
        val ip = com.tvbridge.sender.util.ConnectLinkParser.parseServerIp(rawLink)
        if (ip.isNullOrBlank()) return
        _uiState.update { it.copy(serverIp = ip, errorMessage = null) }
        preferences.saveServerIp(ip)
        _uiState.update { it.copy(screen = AppScreen.TvList) }
        connectToServer(ip)
    }

    fun openSetup() {
        viewModelScope.launch(Dispatchers.Main) {
            stopStreamingInternal()
            signalingClient.disconnect()
            _uiState.update {
                it.copy(
                    screen = AppScreen.Setup,
                    connectionStatus = ConnectionStatus.Idle,
                    tvs = emptyList(),
                    activeTvName = null,
                    statusMessage = "",
                )
            }
        }
    }

    fun onShareSystemAudioChange(enabled: Boolean) {
        preferences.setShareSystemAudioEnabled(enabled)
        _uiState.update { it.copy(shareSystemAudio = enabled) }
    }

    fun onTvSelected(tv: TvItemUi) {
        if (_uiState.value.connectionStatus == ConnectionStatus.Streaming) return
        pendingTv = tv
        _projectionRequests.tryEmit(
            ProjectionRequest(
                tv = tv,
                shareSystemAudio = _uiState.value.shareSystemAudio,
            ),
        )
    }

    fun onProjectionPermissionDenied() {
        pendingTv = null
        _uiState.update {
            it.copy(errorMessage = getApplication<Application>().getString(R.string.error_projection_denied))
        }
    }

    fun onAudioPermissionDenied() {
        pendingTv = null
        _uiState.update {
            it.copy(errorMessage = getApplication<Application>().getString(R.string.error_audio_permission_denied))
        }
    }

    fun onProjectionPermissionGranted(resultData: Intent) {
        val tv = pendingTv ?: return
        pendingTv = null

        mediaProjectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                viewModelScope.launch { stopStreaming() }
            }
        }

        val callback = mediaProjectionCallback!!
        val shareAudio = _uiState.value.shareSystemAudio

        _uiState.update {
            it.copy(
                screen = AppScreen.Streaming,
                connectionStatus = ConnectionStatus.Connecting,
                activeTvName = tv.name,
                statusMessage = getApplication<Application>().getString(R.string.status_starting),
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                ensureWebRtcSession()
            }
            withContext(Dispatchers.Main) {
                try {
                    webRtcSession?.startScreenCast(
                        targetTvId = tv.id,
                        mediaProjectionPermissionData = resultData,
                        mediaProjectionCallback = callback,
                        includeSystemAudio = shareAudio,
                    )
                    _uiState.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.Streaming,
                            statusMessage = getApplication<Application>().getString(R.string.status_streaming),
                        )
                    }
                } catch (error: Exception) {
                    _uiState.update {
                        it.copy(
                            errorMessage = error.message
                                ?: getApplication<Application>().getString(R.string.error_projection_denied),
                        )
                    }
                    stopStreamingInternal()
                }
            }
        }
    }

    fun stopStreaming() {
        viewModelScope.launch(Dispatchers.Main) {
            stopStreamingInternal()
        }
    }

    private fun stopStreamingInternal() {
        webRtcSession?.stop()
        webRtcSession = null
        mediaProjectionCallback = null
        ProjectionForegroundService.stop(getApplication())
        _uiState.update {
            it.copy(
                screen = AppScreen.TvList,
                connectionStatus = ConnectionStatus.Connected,
                activeTvName = null,
                statusMessage = getApplication<Application>().getString(R.string.status_connected),
            )
        }
    }

    private fun connectToServer(serverIp: String) {
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.Connecting,
                statusMessage = getApplication<Application>().getString(R.string.status_connecting),
                errorMessage = null,
            )
        }
        signalingClient.connect(serverIp)
    }

    private fun ensureWebRtcSession() {
        synchronized(sessionLock) {
            if (webRtcSession != null) return

            webRtcSession = WebRtcSenderSession(
                context = getApplication(),
                eglBase = eglBase,
                onSignalOut = { targetId, payload -> signalingClient.sendSignal(targetId, payload) },
                onStreamingStarted = {
                    viewModelScope.launch(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                connectionStatus = ConnectionStatus.Streaming,
                                statusMessage = getApplication<Application>().getString(R.string.status_streaming),
                            )
                        }
                    }
                },
                onStreamingEnded = {
                    viewModelScope.launch { stopStreaming() }
                },
                onError = { message ->
                    viewModelScope.launch(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                connectionStatus = ConnectionStatus.Error,
                                errorMessage = message,
                            )
                        }
                        stopStreamingInternal()
                    }
                },
            )
        }
    }

    private fun prewarmWebRtcSession() {
        viewModelScope.launch(Dispatchers.Main) {
            runCatching { ensureWebRtcSession() }
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
        prewarmWebRtcSession()
    }

    override fun onTvListUpdated(tvs: List<EmitterSignalingClient.TvInfo>) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    tvs = tvs.map { tv -> TvItemUi(tv.id, tv.name) },
                    connectionStatus = ConnectionStatus.Connected,
                    statusMessage = getApplication<Application>().getString(R.string.status_connected),
                )
            }
        }
        prewarmWebRtcSession()
    }

    override fun onSignal(fromTvId: String, payload: org.json.JSONObject) {
        viewModelScope.launch(webRtcDispatcher) {
            ensureWebRtcSession()
            webRtcSession?.handleSignal(fromTvId, payload)
        }
    }

    override fun onError(message: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Error,
                    errorMessage = message,
                    statusMessage = getApplication<Application>().getString(R.string.error_connection),
                )
            }
        }
    }

    override fun onDisconnected() {
        viewModelScope.launch {
            stopStreaming()
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Disconnected,
                    statusMessage = getApplication<Application>().getString(R.string.status_disconnected),
                    tvs = emptyList(),
                )
            }
        }
    }

    override fun onCleared() {
        signalingClient.disconnect()
        runBlocking(Dispatchers.Main) {
            webRtcSession?.dispose()
            webRtcSession = null
            eglBase.release()
            ProjectionForegroundService.stop(getApplication())
        }
        super.onCleared()
    }
}
