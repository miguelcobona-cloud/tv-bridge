package com.tvbridge.sender.webrtc

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import android.util.Log
import org.webrtc.audio.JavaAudioDeviceModule
import java.lang.reflect.Method
import java.nio.ByteBuffer

/**
 * Inyecta audio del sistema (AudioPlaybackCapture) en WebRTC cuando el módulo de audio
 * nativo ya inició la grabación (callback oficial, sin bloquear el hilo principal).
 */
class PlaybackCaptureAudioBridge(
    private val audioDeviceModuleProvider: () -> JavaAudioDeviceModule,
) {
    private var playbackRecord: AudioRecord? = null
    private var injectorThread: Thread? = null
    @Volatile private var running = false
    @Volatile var pendingMediaProjection: MediaProjection? = null

    val audioRecordStateCallback = object : JavaAudioDeviceModule.AudioRecordStateCallback {
        override fun onWebRtcAudioRecordStart() {
            try {
                val projection = pendingMediaProjection ?: return
                if (!start(projection)) {
                    Log.w(TAG, "No se pudo capturar audio del sistema; la transmisión seguirá solo con vídeo")
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Error al iniciar captura de audio del sistema", error)
            }
        }

        override fun onWebRtcAudioRecordStop() {
            stopInjectorOnly()
        }
    }

    fun isRunning(): Boolean = running

    fun start(mediaProjection: MediaProjection): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        if (running) return true

        return runCatching {
            val input = audioDeviceModuleProvider().audioInput
            if (!waitForAudioInputReady(input)) {
                Log.w(TAG, "Audio input de WebRTC no inicializado")
                return false
            }

            val captureFormat = readCaptureFormat(input)
            val byteBuffer = reflectField<ByteBuffer>(input, "byteBuffer")
                ?: return false
            val nativePtr = reflectLongField(input, "nativeAudioRecord")
            if (nativePtr == 0L) {
                Log.w(TAG, "Puntero nativo de audio no disponible")
                return false
            }
            val nativeDataIsRecorded = reflectNativeDataMethod(input)
                ?: return false

            haltBuiltinRecording(input)

            val record = createPlaybackRecord(mediaProjection, captureFormat)
                ?: return false
            record.startRecording()
            playbackRecord = record
            running = true

            injectorThread = Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val capacity = byteBuffer.capacity()
                while (running) {
                    byteBuffer.clear()
                    val read = record.read(byteBuffer, capacity)
                    if (read > 0) {
                        if (read < capacity) {
                            byteBuffer.position(read)
                            while (byteBuffer.position() < capacity) {
                                byteBuffer.put(0)
                            }
                        }
                        byteBuffer.flip()
                        nativeDataIsRecorded.invoke(input, nativePtr, capacity, 0L)
                    }
                }
            }.apply {
                name = "SystemAudioInjector"
                start()
            }

            Log.i(
                TAG,
                "Captura de audio del sistema activa (${captureFormat.sampleRate}Hz, ${captureFormat.channelCount}ch)",
            )
            true
        }.onFailure { error ->
            Log.e(TAG, "Fallo al iniciar audio del sistema", error)
            stopInjectorOnly()
        }.getOrDefault(false)
    }

    fun stop() {
        pendingMediaProjection = null
        stopInjectorOnly()
    }

    private fun stopInjectorOnly() {
        running = false
        injectorThread?.interrupt()
        injectorThread = null
        runCatching { playbackRecord?.stop() }
        playbackRecord?.release()
        playbackRecord = null
    }

    private fun waitForAudioInputReady(input: Any): Boolean {
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val byteBuffer = reflectField<ByteBuffer>(input, "byteBuffer")
            val nativePtr = reflectLongField(input, "nativeAudioRecord")
            if (byteBuffer != null && nativePtr != 0L) {
                return true
            }
            Thread.sleep(5)
        }
        return false
    }

    private fun readCaptureFormat(input: Any): CaptureFormat {
        val micRecord = reflectField<AudioRecord>(input, "audioRecord")
        return CaptureFormat(
            sampleRate = micRecord?.sampleRate ?: SAMPLE_RATE_HZ,
            channelCount = micRecord?.channelCount ?: 2,
        )
    }

    private fun haltBuiltinRecording(input: Any) {
        val clazz = input.javaClass
        val hasActiveThread = reflectField<Any>(input, "audioThread") != null
        if (hasActiveThread) {
            clazz.getDeclaredMethod("stopRecording").apply { isAccessible = true }.invoke(input)
        } else {
            clazz.getDeclaredMethod("releaseAudioResources").apply { isAccessible = true }.invoke(input)
        }
        audioDeviceModuleProvider().setMicrophoneMute(true)
    }

    private fun reflectNativeDataMethod(input: Any): Method? = runCatching {
        input.javaClass.getDeclaredMethod(
            "nativeDataIsRecorded",
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
        ).apply { isAccessible = true }
    }.getOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun <T> reflectField(input: Any, fieldName: String): T? = runCatching {
        val field = input.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
        field.get(input) as T
    }.getOrNull()

    private fun reflectLongField(input: Any, fieldName: String): Long = runCatching {
        input.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }.getLong(input)
    }.getOrDefault(0L)

    private fun createPlaybackRecord(
        mediaProjection: MediaProjection,
        format: CaptureFormat,
    ): AudioRecord? {
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .addMatchingUsage(AudioAttributes.USAGE_ASSISTANT)
            .build()

        val channelMasks = if (format.channelCount >= 2) {
            listOf(AudioFormat.CHANNEL_IN_STEREO, AudioFormat.CHANNEL_IN_MONO)
        } else {
            listOf(AudioFormat.CHANNEL_IN_MONO)
        }

        for (channelMask in channelMasks) {
            val record = buildPlaybackRecord(config, format.sampleRate, channelMask)
            if (record != null) return record
        }
        return null
    }

    private fun buildPlaybackRecord(
        config: AudioPlaybackCaptureConfiguration,
        sampleRate: Int,
        channelMask: Int,
    ): AudioRecord? {
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding)
        if (minBuffer <= 0) return null

        val record = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * 4)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        return if (record.state == AudioRecord.STATE_INITIALIZED) record else {
            record.release()
            null
        }
    }

    private data class CaptureFormat(
        val sampleRate: Int,
        val channelCount: Int,
    )

    companion object {
        private const val TAG = "PlaybackCaptureAudio"
        private const val READY_TIMEOUT_MS = 2_000L
        const val SAMPLE_RATE_HZ = 48_000
    }
}
