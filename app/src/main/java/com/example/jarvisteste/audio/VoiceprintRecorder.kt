package com.example.jarvisteste.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat

/** Grava uma amostra curta da voz do usuário para o cadastro da impressão vocal. */
class VoiceprintRecorder(private val context: Context) {

    val sampleRate: Int = VoiceFeatures.SAMPLE_RATE

    /** Chamada de uma coroutine de I/O: bloqueia até capturar [durationMs]. */
    fun record(durationMs: Long): FloatArray? {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null

        val needed = (sampleRate * durationMs / 1_000).toInt()
        val record = try {
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
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }

        val samples = ShortArray(needed)
        try {
            record.startRecording()
            var total = 0
            while (total < samples.size) {
                val read = record.read(samples, total, samples.size - total)
                if (read <= 0) break
                total += read
            }
            if (total < samples.size * 3 / 5) return null
            return FloatArray(total) { samples[it] / 32_768f }
        } catch (error: Exception) {
            return null
        } finally {
            runCatching {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            }
            record.release()
        }
    }
}
