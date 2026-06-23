package com.tvbridge.sender.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Sesión WebRTC del emisor (offerer) con captura de pantalla vía MediaProjection.
 */
class WebRtcSenderSession(
    private val context: Context,
    private val eglBase: EglBase,
    private val onSignalOut: (String, JSONObject) -> Unit,
    private val onStreamingStarted: () -> Unit,
    private val onStreamingEnded: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val audioDeviceModule: JavaAudioDeviceModule
    private val playbackAudioBridge: PlaybackCaptureAudioBridge
    private val localPlaybackSilencer: SenderLocalPlaybackSilencer
    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioSource: org.webrtc.AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var activeTargetId: String? = null

    init {
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        lateinit var audioModule: JavaAudioDeviceModule
        playbackAudioBridge = PlaybackCaptureAudioBridge { audioModule }
        localPlaybackSilencer = SenderLocalPlaybackSilencer(context.applicationContext)
        audioModule = JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .setUseStereoInput(true)
            .setInputSampleRate(PlaybackCaptureAudioBridge.SAMPLE_RATE_HZ)
            .setOutputSampleRate(PlaybackCaptureAudioBridge.SAMPLE_RATE_HZ)
            .setSampleRate(PlaybackCaptureAudioBridge.SAMPLE_RATE_HZ)
            .setAudioRecordStateCallback(playbackAudioBridge.audioRecordStateCallback)
            .createAudioDeviceModule()
        audioDeviceModule = audioModule

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    fun startScreenCast(
        targetTvId: String,
        mediaProjectionPermissionData: Intent,
        mediaProjectionCallback: MediaProjection.Callback,
        includeSystemAudio: Boolean,
    ) {
        stopInternal(notify = false)
        activeTargetId = targetTvId

        try {
            if (includeSystemAudio) {
                localPlaybackSilencer.silenceWhileCasting()
                audioDeviceModule.setSpeakerMute(true)
            }
            setupScreenCapture(
                mediaProjectionPermissionData = mediaProjectionPermissionData,
                mediaProjectionCallback = mediaProjectionCallback,
                includeSystemAudio = includeSystemAudio,
            )
            createPeerConnectionAndOffer()
        } catch (error: Exception) {
            onError(error.message ?: "No se pudo iniciar la captura de pantalla")
            stopInternal(notify = true)
        }
    }

    fun handleSignal(fromTvId: String, payload: JSONObject) {
        if (fromTvId != activeTargetId || peerConnection == null) return

        when (payload.optString("kind")) {
            "answer" -> handleAnswer(payload)
            "ice-candidate" -> handleIceCandidate(payload)
            else -> onError("Tipo de señal desconocido: ${payload.optString("kind")}")
        }
    }

    fun stop() {
        stopInternal(notify = true)
    }

    private fun setupScreenCapture(
        mediaProjectionPermissionData: Intent,
        mediaProjectionCallback: MediaProjection.Callback,
        includeSystemAudio: Boolean,
    ) {
        val capturer = ScreenCapturerAndroid(mediaProjectionPermissionData, mediaProjectionCallback)
        val helper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBase.eglBaseContext)
        val source = peerConnectionFactory.createVideoSource(capturer.isScreencast)
        capturer.initialize(helper, context, source.capturerObserver)
        capturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS)

        val track = peerConnectionFactory.createVideoTrack("screen_track", source)
        track.setEnabled(true)

        if (includeSystemAudio) {
            setupSystemAudioTrack(capturer)
        }

        videoCapturer = capturer
        surfaceTextureHelper = helper
        videoSource = source
        videoTrack = track
    }

    private fun setupSystemAudioTrack(capturer: ScreenCapturerAndroid) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.w(TAG, "Audio del sistema no disponible en Android < 10")
                return
            }

            val projection = capturer.getMediaProjection()
            if (projection == null) {
                Log.w(TAG, "MediaProjection no disponible para audio")
                return
            }

            val audioConstraints = MediaConstraints()
            val systemAudioSource = peerConnectionFactory.createAudioSource(audioConstraints)
            val systemAudioTrack = peerConnectionFactory.createAudioTrack("screen_audio", systemAudioSource)
            systemAudioTrack.setEnabled(true)
            audioSource = systemAudioSource
            audioTrack = systemAudioTrack
            playbackAudioBridge.pendingMediaProjection = projection
        } catch (error: Exception) {
            Log.e(TAG, "Error configurando audio del sistema; transmitiendo solo vídeo", error)
            clearAudioTrack()
        }
    }

    private fun clearAudioTrack() {
        playbackAudioBridge.pendingMediaProjection = null
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        playbackAudioBridge.stop()
    }

    private fun createPeerConnectionAndOffer() {
        val track = videoTrack ?: throw IllegalStateException("Video track no inicializado")
        val targetId = activeTargetId ?: throw IllegalStateException("TV destino no definida")

        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            ),
        )

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                val payload = JSONObject()
                    .put("kind", "ice-candidate")
                    .put(
                        "candidate",
                        JSONObject()
                            .put("candidate", candidate.sdp)
                            .put("sdpMid", candidate.sdpMid)
                            .put("sdpMLineIndex", candidate.sdpMLineIndex),
                    )
                onSignalOut(targetId, payload)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(
                receiver: org.webrtc.RtpReceiver?,
                streams: Array<out org.webrtc.MediaStream>?,
            ) = Unit

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> onStreamingStarted()
                    PeerConnection.PeerConnectionState.FAILED,
                    PeerConnection.PeerConnectionState.DISCONNECTED,
                    -> stopInternal(notify = true)
                    else -> Unit
                }
            }
        }) ?: throw IllegalStateException("No se pudo crear PeerConnection")

        val streamIds = listOf("screen_stream")
        peerConnection?.addTrack(track, streamIds)
        audioTrack?.let { audio -> peerConnection?.addTrack(audio, streamIds) }

        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                description ?: return
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        val payload = JSONObject()
                            .put("kind", "offer")
                            .put("sdp", description.description)
                        onSignalOut(targetId, payload)
                    }

                    override fun onSetFailure(error: String?) {
                        onError(error ?: "Error al establecer offer local")
                    }
                }, description)
            }

            override fun onCreateFailure(error: String?) {
                onError(error ?: "Error al crear offer")
            }
        }, constraints)
    }

    private fun handleAnswer(payload: JSONObject) {
        val sdp = payload.optString("sdp")
        if (sdp.isBlank()) {
            onError("Answer SDP vacío")
            return
        }

        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetFailure(error: String?) {
                onError(error ?: "Error al aplicar answer remoto")
            }
        }, answer)
    }

    private fun handleIceCandidate(payload: JSONObject) {
        val candidateJson = payload.optJSONObject("candidate") ?: return
        val sdpMid = candidateJson.optString("sdpMid")
        val sdpMLineIndex = candidateJson.optInt("sdpMLineIndex")
        val candidateSdp = candidateJson.optString("candidate")

        if (sdpMid.isBlank() || candidateSdp.isBlank()) return

        peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidateSdp))
    }

    private fun stopInternal(notify: Boolean) {
        localPlaybackSilencer.restore()
        audioDeviceModule.setSpeakerMute(false)
        playbackAudioBridge.stop()

        runCatching { videoCapturer?.stopCapture() }
        videoCapturer?.dispose()
        videoCapturer = null

        audioTrack?.dispose()
        audioTrack = null

        audioSource?.dispose()
        audioSource = null

        videoTrack?.dispose()
        videoTrack = null

        videoSource?.dispose()
        videoSource = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null

        activeTargetId = null

        if (notify) {
            onStreamingEnded()
        }
    }

    fun dispose() {
        stopInternal(notify = false)
        audioDeviceModule.release()
    }

    private open class SimpleSdpObserver : org.webrtc.SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }

    companion object {
        private const val TAG = "WebRtcSenderSession"
        private const val CAPTURE_WIDTH = 1280
        private const val CAPTURE_HEIGHT = 720
        private const val CAPTURE_FPS = 30
    }
}
