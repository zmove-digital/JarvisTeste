package com.example.jarvisteste.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.example.jarvisteste.data.sanitizeForSpeech
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Fala usando as vozes naturais do Gemini TTS.
 *
 * A API devolve o áudio em streaming (PCM 24 kHz). Os pedaços são tocados
 * assim que chegam, então o Jarvis começa a falar antes de a frase inteira
 * ficar pronta. O áudio completo fica em cache para repetir frases curtas
 * ("Pois não?", exemplos de voz) sem gastar rede nem cota.
 */
class GeminiTtsPlayer(
    context: Context,
    private val apiKeyProvider: () -> String
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheDir = File(appContext.cacheDir, "gemini_tts").apply { mkdirs() }

    @Volatile private var generation = 0
    @Volatile private var job: Job? = null
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var currentTrack: AudioTrack? = null
    @Volatile private var shutDown = false
    private val trackLock = Any()

    /**
     * Sintetiza [text] com [voiceId] e toca assim que o primeiro trecho chega.
     * Se nada puder ser tocado, [onFailure] é chamado para o chamador usar a
     * voz offline como reserva. Ambos os callbacks rodam na thread principal.
     */
    fun speak(
        text: String,
        voiceId: String,
        style: String,
        onDone: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val clean = sanitizeForSpeech(text)
        if (clean.isBlank()) {
            mainHandler.post { onDone() }
            return
        }

        stop()
        val myGeneration = generation
        Log.d(TAG, "Sintetizando com voz $voiceId (${clean.length} caracteres)")
        val work = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            var heardAudio = false
            var failure: Throwable? = null
            try {
                val cached = cacheFile(clean, voiceId, style)
                if (cacheable(clean) && cached.exists() && cached.length() > WAV_HEADER_BYTES) {
                    Log.d(TAG, "Usando áudio em cache (${cached.length()} bytes)")
                    heardAudio = playWav(cached.readBytes(), myGeneration)
                }
                if (!heardAudio && myGeneration == generation) {
                    heardAudio = stream(clean, voiceId, style, myGeneration)
                }
                if (!heardAudio) {
                    throw IllegalStateException("O Gemini TTS não retornou áudio.")
                }
            } catch (cancelled: CancellationException) {
                return@launch
            } catch (error: Throwable) {
                failure = error
                Log.w(TAG, "Falha na voz do Gemini", error)
            } finally {
                if (myGeneration == generation) releaseTrack()
                if (job === self) job = null
            }

            if (myGeneration != generation) return@launch
            if (heardAudio || failure == null) {
                mainHandler.post { onDone() }
            } else {
                mainHandler.post { onFailure(failure ?: IllegalStateException("Falha na voz")) }
            }
        }
        job = work
        work.start()
    }

    fun stop() {
        generation++
        job?.cancel()
        job = null
        runCatching { connection?.disconnect() }
        connection = null
        releaseTrack()
    }

    fun shutdown() {
        if (shutDown) return
        shutDown = true
        stop()
        scope.cancel()
    }

    // --------------------------------------------------------------- streaming

    /**
     * Sintetiza [text] em segundo plano só para deixar o áudio no cache, sem
     * tocar nada. Usado para o "Pois não?" do despertar: a primeira resposta
     * sai na hora, e as seguintes reaproveitam o mesmo arquivo.
     */
    fun prewarm(text: String, voiceId: String, style: String) {
        if (shutDown) return
        val clean = sanitizeForSpeech(text)
        if (clean.isBlank() || !cacheable(clean)) return
        val target = cacheFile(clean, voiceId, style)
        if (target.exists() && target.length() > WAV_HEADER_BYTES) return
        if (job?.isActive == true) return

        val myGeneration = generation
        val work = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            try {
                stream(clean, voiceId, style, myGeneration, play = false)
            } catch (cancelled: CancellationException) {
                return@launch
            } catch (error: Throwable) {
                Log.d(TAG, "Pré-aquecimento da voz falhou", error)
            } finally {
                if (job === self) job = null
            }
        }
        job = work
        work.start()
    }

    private fun stream(
        text: String,
        voiceId: String,
        style: String,
        myGeneration: Int,
        play: Boolean = true
    ): Boolean {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Chave da API não configurada.")
        }

        val conn = (URL(INTERACTIONS_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        connection = conn
        val startedAt = System.currentTimeMillis()

        try {
            conn.outputStream.use { output ->
                output.write(buildRequestBody(text, voiceId, style).toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val body = conn.errorStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                throw IllegalStateException(friendlyError(code, body))
            }
            Log.d(TAG, "HTTP $code; recebendo áudio")

            var track: AudioTrack? = null
            val pcm = ByteArrayOutputStream()
            var event = ""
            var firstChunkMs = -1L

            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (myGeneration == generation) {
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith("event:") -> {
                            event = line.removePrefix("event:").trim()
                        }

                        line.startsWith("data:") && event == "step.delta" -> {
                            val raw = line.removePrefix("data:").trim()
                            val delta = runCatching {
                                JSONObject(raw).optJSONObject("delta")
                            }.getOrNull() ?: continue
                            if (!delta.optString("mime_type").startsWith("audio")) continue
                            val encoded = delta.optString("data")
                            if (encoded.isBlank()) continue
                            val chunk = Base64.decode(encoded, Base64.DEFAULT)
                            if (chunk.isEmpty()) continue
                            if (firstChunkMs < 0) {
                                firstChunkMs = System.currentTimeMillis() - startedAt
                                Log.d(TAG, "Primeiro áudio em ${firstChunkMs}ms")
                            }

                            if (play && track == null) {
                                track = synchronized(trackLock) {
                                    createTrack().also {
                                        currentTrack = it
                                        it.play()
                                    }
                                }
                            }
                            track?.let { writeBlocking(it, chunk, myGeneration) }
                            pcm.write(chunk)
                        }
                    }
                }
            }

            // stop() no modo STREAM toca o que ainda está no buffer antes de parar.
            track?.let { active ->
                synchronized(trackLock) { runCatching { active.stop() } }
            }

            if (pcm.size() > 0 && myGeneration == generation) {
                Log.d(
                    TAG,
                    "Áudio completo: ${pcm.size()} bytes em " +
                        "${System.currentTimeMillis() - startedAt}ms"
                )
                if (cacheable(text)) {
                    saveWave(cacheFile(text, voiceId, style), pcm.toByteArray())
                }
                return true
            }
            if (pcm.size() == 0) Log.w(TAG, "Resposta HTTP sem áudio")
            return false
        } finally {
            if (connection === conn) connection = null
            runCatching { conn.disconnect() }
        }
    }

    private fun buildRequestBody(text: String, voiceId: String, style: String): String {
        val annotations = JSONArray().put(
            JSONObject()
                .put("type", "speech_metadata")
                .put("style", style)
        )
        val content = JSONArray().put(
            JSONObject()
                .put("type", "text")
                .put("text", text)
                .put("annotations", annotations)
        )
        val input = JSONArray().put(
            JSONObject()
                .put("type", "user_input")
                .put("content", content)
        )

        return JSONObject()
            .put("model", MODEL)
            .put("input", input)
            .put(
                "response_format",
                JSONObject()
                    .put("type", "audio")
                    .put("mime_type", "audio/l16")
                    .put("sample_rate", SAMPLE_RATE)
            )
            .put(
                "generation_config",
                JSONObject().put(
                    "speech_config",
                    JSONArray().put(JSONObject().put("voice", voiceId))
                )
            )
            .put("stream", true)
            .toString()
    }

    // ------------------------------------------------------------------- áudio

    private fun createTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE / 2)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()
    }

    private fun writeBlocking(track: AudioTrack, data: ByteArray, myGeneration: Int) {
        synchronized(trackLock) {
            var offset = 0
            while (offset < data.size && myGeneration == generation) {
                val written = track.write(data, offset, data.size - offset, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) return
                offset += written
            }
        }
    }

    private fun playWav(bytes: ByteArray, myGeneration: Int): Boolean {
        if (bytes.size <= WAV_HEADER_BYTES) return false
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte()) return false

        val dataOffset = wavDataOffset(bytes)
        if (dataOffset <= 0 || dataOffset >= bytes.size) return false

        val track = synchronized(trackLock) {
            createTrack().also {
                currentTrack = it
                it.play()
            }
        }
        try {
            writeBlocking(track, bytes.copyOfRange(dataOffset, bytes.size), myGeneration)
        } finally {
            synchronized(trackLock) {
                runCatching { track.stop() }
                if (currentTrack === track) currentTrack = null
                runCatching { track.release() }
            }
        }
        return myGeneration == generation
    }

    private fun releaseTrack() {
        val track = currentTrack
        currentTrack = null
        if (track != null) {
            synchronized(trackLock) {
                runCatching { track.pause() }
                runCatching { track.flush() }
                runCatching { track.release() }
            }
        }
    }

    // ------------------------------------------------------------------- cache

    private fun cacheFile(text: String, voiceId: String, style: String): File {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$voiceId|$style|$text".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$digest.wav")
    }

    /** Só frases curtas valem a pena no cache; respostas longas ocupariam disco à toa. */
    private fun cacheable(text: String) = text.length <= MAX_CACHEABLE_CHARS

    private fun saveWave(target: File, pcm: ByteArray) {
        runCatching {
            val header = ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + pcm.size)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // tamanho do bloco fmt
            header.putShort(1) // PCM
            header.putShort(1) // mono
            header.putInt(SAMPLE_RATE)
            header.putInt(SAMPLE_RATE * 2) // bytes por segundo
            header.putShort(2) // alinhamento do bloco
            header.putShort(16) // bits por amostra
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(pcm.size)

            val temp = File(target.parentFile, target.name + "." + System.nanoTime() + ".tmp")
            temp.outputStream().use { output ->
                output.write(header.array())
                output.write(pcm)
            }
            if (!temp.renameTo(target)) {
                target.outputStream().use { output ->
                    output.write(header.array())
                    output.write(pcm)
                }
                temp.delete()
            }
            pruneCache()
        }
    }

    private fun pruneCache() {
        val files = cacheDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_CACHE_FILES).forEach { runCatching { it.delete() } }
    }

    private fun wavDataOffset(bytes: ByteArray): Int {
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
            if (id == "data") return offset + 8
            offset += 8 + size + (size and 1)
        }
        return -1
    }

    private fun friendlyError(code: Int, body: String): String {
        val detail = runCatching {
            JSONObject(body).getJSONObject("error").getString("message")
        }.getOrDefault(body.take(200))
        return when (code) {
            400 -> "Pedido de voz inválido: $detail"
            401, 403 -> "Chave sem acesso ao Gemini TTS."
            429 -> "Cota de voz do Gemini esgotada por agora."
            in 500..599 -> "Gemini TTS instável agora."
            else -> "Erro do Gemini TTS ($code): $detail"
        }
    }

    companion object {
        private const val TAG = "GeminiTtsPlayer"
        private const val INTERACTIONS_URL =
            "https://generativelanguage.googleapis.com/v1beta/interactions"
        private const val MODEL = "gemini-3.8-flash-lite-tts"
        private const val SAMPLE_RATE = 24_000
        private const val WAV_HEADER_BYTES = 44
        private const val MAX_CACHE_FILES = 40
        private const val MAX_CACHEABLE_CHARS = 200
    }
}
