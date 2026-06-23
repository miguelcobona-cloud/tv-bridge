package com.tvbridge.sender.webrtc

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Silencia el altavoz del teléfono mientras se transmite audio a la TV.
 *
 * AudioPlaybackCapture lee del mezclador del sistema (antes de la atenuación del
 * altavoz), así que la TV puede seguir recibiendo sonido aunque el móvil esté mudo.
 */
class SenderLocalPlaybackSilencer(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var savedMusicVolume: Int? = null
    private var silenced = false

    fun silenceWhileCasting() {
        if (silenced) return

        runCatching {
            savedMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE,
                    AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE,
                )
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true)
            }

            silenced = true
            Log.i(TAG, "Reproducción local silenciada (el audio sigue yendo a la TV)")
        }.onFailure { error ->
            Log.w(TAG, "No se pudo silenciar el altavoz del emisor", error)
        }
    }

    fun restore() {
        if (!silenced) return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_UNMUTE,
                    0,
                )
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false)
            }

            savedMusicVolume?.let { volume ->
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    volume.coerceIn(0, max),
                    0,
                )
            }

            silenced = false
            savedMusicVolume = null
        }.onFailure { error ->
            Log.w(TAG, "No se pudo restaurar el volumen del emisor", error)
            silenced = false
            savedMusicVolume = null
        }
    }

    companion object {
        private const val TAG = "SenderLocalSilencer"
    }
}
