package com.tvbridge.receiver.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Enruta el audio remoto de WebRTC al altavoz de la TV (modo comunicación + focus).
 */
class TvAudioRouteManager(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMode = AudioManager.MODE_NORMAL
    private var active = false
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> Log.w(TAG, "Audio focus perdido ($focusChange)")
            else -> Unit
        }
    }

    fun activateForRemotePlayback() {
        if (active) return

        runCatching {
            previousMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build()
                audioManager.requestAudioFocus(focusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN,
                )
            }

            active = true
            Log.i(TAG, "Salida de audio TV activada (speaker + MODE_IN_COMMUNICATION)")
        }.onFailure { error ->
            Log.e(TAG, "No se pudo activar ruta de audio en TV", error)
        }
    }

    fun deactivate() {
        if (!active) return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusListener)
            }

            audioManager.mode = previousMode
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            active = false
        }.onFailure { error ->
            Log.e(TAG, "No se pudo desactivar ruta de audio en TV", error)
            active = false
        }
    }

    companion object {
        private const val TAG = "TvAudioRouteManager"
    }
}
