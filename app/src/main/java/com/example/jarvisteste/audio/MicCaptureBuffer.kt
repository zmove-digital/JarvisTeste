package com.example.jarvisteste.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Buffer circular com os últimos segundos do microfone.
 *
 * O `SpeechRecognizer` só devolve texto; para conferir quem falou precisamos
 * do áudio cru. O buffer fica apenas na memória e é usado no momento da
 * verificação — nada é gravado em arquivo nem enviado para a internet.
 */
class MicCaptureBuffer(
    private val context: Context,
    seconds: Int = 8
) {
    private val sampleRate = VoiceFeatures.SAMPLE_RATE
    private val capacity = sampleRate * seconds
    private val ring = ShortArray(capacity)
    private val lock = Any()
    private val running = AtomicBoolean(false)

    private var thread: Thread? = null
    private var stopped = CountDownLatch(0)
    private var writeIndex = 0
    private var totalWritten = 0L

    fun start(): Boolean {
        if (running.get()) return true
        val previous = thread
        if (previous?.isAlive == true) {
            running.set(false)
            if (!stopped.await(1, TimeUnit.SECONDS)) {
                Log.w(TAG, "A captura anterior ainda não liberou o microfone")
                return false
            }
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false

        val recorder = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuffer, sampleRate / 5 * 2))
                .build()
        } catch (error: Exception) {
            Log.w(TAG, "Não abriu o microfone para verificação", error)
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }

        try {
            recorder.startRecording()
        } catch (error: Exception) {
            recorder.release()
            Log.w(TAG, "Não iniciou a captura para verificação", error)
            return false
        }

        synchronized(lock) {
            writeIndex = 0
            totalWritten = 0
        }
        stopped = CountDownLatch(1)
        running.set(true)
        thread = Thread({
            try {
                val chunk = ShortArray(1_024)
                while (running.get()) {
                    val read = try {
                        recorder.read(chunk, 0, chunk.size)
                    } catch (error: Exception) {
                        Log.w(TAG, "Leitura do microfone falhou", error)
                        break
                    }
                    if (read <= 0) {
                        Thread.yield()
                        continue
                    }
                    synchronized(lock) {
                        for (i in 0 until read) {
                            ring[writeIndex] = chunk[i]
                            writeIndex = (writeIndex + 1) % capacity
                            totalWritten++
                        }
                    }
                }
            } finally {
                // A thread que leu é a mesma que libera: evita usar um
                // AudioRecord já fechado num restart rápido.
                runCatching {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder.stop()
                    }
                }
                recorder.release()
                synchronized(lock) {
                    writeIndex = 0
                    totalWritten = 0
                }
                stopped.countDown()
            }
        }, "jarvis-voice-capture").apply { start() }
        return true
    }

    /** Cópia dos últimos segundos, em float [-1, 1], para comparação. */
    fun snapshot(): FloatArray? {
        val available = synchronized(lock) {
            minOf(totalWritten, capacity.toLong()).toInt()
        }
        if (available < sampleRate / 2) return null
        val out = FloatArray(available)
        synchronized(lock) {
            val start = (writeIndex - available + capacity) % capacity
            for (i in 0 until available) {
                out[i] = ring[(start + i) % capacity] / 32_768f
            }
        }
        return out
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        val current = thread
        if (current == null) {
            stopped.countDown()
            return
        }
        try {
            current.join(1_000)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (current.isAlive) {
            Log.w(TAG, "A thread de captura não encerrou a tempo")
        }
    }

    companion object {
        private const val TAG = "MicCapture"
    }
}
