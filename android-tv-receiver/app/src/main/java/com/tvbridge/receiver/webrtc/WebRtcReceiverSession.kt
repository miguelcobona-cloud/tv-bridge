package com.tvbridge.receiver.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Sesión WebRTC del receptor (answerer).
 * Recibe offer + ICE del emisor web y devuelve answer + ICE vía SignalingClient.
 */
class WebRtcReceiverSession(
    private val context: Context,
    private val eglBase: EglBase,
    private val onSignalOut: (JSONObject) -> Unit,
    private val onStreamingStarted: () -> Unit,
    private val onStreamingEnded: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val audioDeviceModule: JavaAudioDeviceModule
    private val audioRouteManager: TvAudioRouteManager
    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private val initializedRenderers = mutableSetOf<SurfaceViewRenderer>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun activateAudioRouteOnMain() {
        mainHandler.post { audioRouteManager.activateForRemotePlayback() }
    }

    init {
        audioRouteManager = TvAudioRouteManager(context.applicationContext)

        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        audioDeviceModule = JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setUseStereoOutput(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setAudioTrackStateCallback(object : JavaAudioDeviceModule.AudioTrackStateCallback {
                override fun onWebRtcAudioTrackStart() {
                    activateAudioRouteOnMain()
                    Log.i(TAG, "WebRTC audio track iniciado en TV")
                }

                override fun onWebRtcAudioTrackStop() {
                    mainHandler.post { audioRouteManager.deactivate() }
                }
            })
            .createAudioDeviceModule()
        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    fun attachRenderer(renderer: SurfaceViewRenderer) {
        if (renderer !in initializedRenderers) {
            renderer.init(eglBase.eglBaseContext, null)
            renderer.setEnableHardwareScaler(true)
            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            renderer.setMirror(false)
            initializedRenderers.add(renderer)
        }
        remoteVideoTrack?.addSink(renderer)
    }

    fun detachRenderer(renderer: SurfaceViewRenderer) {
        remoteVideoTrack?.removeSink(renderer)
        if (renderer in initializedRenderers) {
            renderer.release()
            initializedRenderers.remove(renderer)
        }
    }

    fun handleSignal(payload: JSONObject) {
        when (payload.optString("kind")) {
            "offer" -> handleOffer(payload)
            "ice-candidate" -> handleIceCandidate(payload)
            else -> onError("Tipo de señal desconocido: ${payload.optString("kind")}")
        }
    }

    fun close() {
        remoteVideoTrack?.dispose()
        remoteVideoTrack = null
        remoteAudioTrack?.setEnabled(false)
        remoteAudioTrack = null
        audioRouteManager.deactivate()
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
    }

    fun release() {
        close()
        audioDeviceModule.release()
    }

    private fun handleOffer(payload: JSONObject) {
        val sdp = payload.optString("sdp")
        if (sdp.isNullOrBlank()) {
            onError("Offer SDP vacío")
            return
        }

        close()
        createPeerConnection()

        val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
        val pc = peerConnection ?: return

        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                createAndSendAnswer()
            }

            override fun onSetFailure(error: String?) {
                onError(error ?: "Error al aplicar offer remoto")
            }
        }, offer)
    }

    private fun handleIceCandidate(payload: JSONObject) {
        val candidateJson = payload.optJSONObject("candidate") ?: return
        val sdpMid = candidateJson.optString("sdpMid")
        val sdpMLineIndex = candidateJson.optInt("sdpMLineIndex")
        val candidateSdp = candidateJson.optString("candidate")

        if (sdpMid.isNullOrBlank() || candidateSdp.isNullOrBlank()) {
            return
        }

        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidateSdp)
        peerConnection?.addIceCandidate(iceCandidate)
    }

    private fun createPeerConnection() {
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
                onSignalOut(payload)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(dataChannel: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit

            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    track.setEnabled(true)
                    onStreamingStarted()
                } else if (track is AudioTrack) {
                    remoteAudioTrack = track
                    track.setEnabled(true)
                    audioDeviceModule.setSpeakerMute(false)
                    Log.i(TAG, "Pista de audio remota recibida y habilitada")
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> onStreamingStarted()
                    PeerConnection.PeerConnectionState.FAILED,
                    PeerConnection.PeerConnectionState.DISCONNECTED,
                    -> onStreamingEnded()
                    else -> Unit
                }
            }
        })
    }

    private fun createAndSendAnswer() {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        pc.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                description ?: return
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        val payload = JSONObject()
                            .put("kind", "answer")
                            .put("sdp", description.description)
                        onSignalOut(payload)
                    }

                    override fun onSetFailure(error: String?) {
                        onError(error ?: "Error al establecer answer local")
                    }
                }, description)
            }

            override fun onCreateFailure(error: String?) {
                onError(error ?: "Error al crear answer")
            }
        }, constraints)
    }

    private open class SimpleSdpObserver : org.webrtc.SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }

    companion object {
        private const val TAG = "WebRtcReceiverSession"
    }
}
