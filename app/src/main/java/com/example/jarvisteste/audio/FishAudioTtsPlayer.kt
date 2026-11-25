package com.example.jarvisteste.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.jarvisteste.data.sanitizeForSpeech
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Fala usando uma voz do Fish Audio — inclusive uma voz clonada do próprio
 * usuário.
 *
 * A API devolve MP3. O arquivo é baixado, tocado com [MediaPlayer] e fica em
 * cache quando a frase é curta, para o "Pois não?" e respostas repetidas
 * saírem na hora. Se a chave/voz não estiver configurada ou a API falhar, o
 * [VoiceManager] cai para o Gemini ou para a voz do aparelho.
 */
class FishAudioTtsPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheDir = File(appContext.cacheDir, "fish_audio").apply { mkdirs() }

    @Volatile private var generation = 0
    @Volatile private var job: Job? = null
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var shutDown = false
    private val playerLock = Any()

    fun speak(
        text: String,
        voiceId: String,
        model: String,
        apiKey: String,
        onDone: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val clean = sanitizeForSpeech(text)
        if (clean.isBlank()) {
            mainHandler.post { onDone() }
            return
        }
        if (apiKey.isBlank() || voiceId.isBlank()) {
            mainHandler.post {
                onFailure(IllegalStateException("Configure a API key e a voz do Fish Audio."))
            }
            return
        }

        stop()
        val myGeneration = generation
        Log.d(TAG, "Sintetizando: voz=$voiceId modelo=$model (${clean.length} caracteres)")

        val work = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            var failure: Throwable? = null
            try {
                val cached = cacheFile(clean, voiceId, model)
                if (cacheable(clean) && cached.exists() && cached.length() > MIN_MP3_BYTES) {
                    Log.d(TAG, "Usando áudio em cache (${cached.length()} bytes)")
                    playFile(cached, myGeneration)
                } else {
                    val bytes = download(clean, voiceId, model, apiKey, myGeneration)
                    if (myGeneration != generation) return@launch
                    if (cacheable(clean)) saveCache(cached, bytes)
                    val temp = File(cacheDir, "play-${System.nanoTime()}.mp3")
                    try {
                        temp.writeBytes(bytes)
                        playFile(temp, myGeneration)
                    } finally {
                        runCatching { temp.delete() }
                    }
                }
            } catch (cancelled: CancellationException) {
                return@launch
            } catch (error: Throwable) {
                failure = error
                Log.w(TAG, "Falha na voz do Fish Audio", error)
            } finally {
                if (myGeneration == generation) VoiceLevel.finish()
                if (job === self) job = null
            }

            if (myGeneration != generation) return@launch
            if (failure == null) {
                mainHandler.post { onDone() }
            } else {
                mainHandler.post { onFailure(failure ?: IllegalStateException("Falha na voz")) }
            }
        }
        job = work
        work.start()
    }

    /**
     * Deixa uma frase curta no cache sem tocar nada. Usado no "Pois não?" do
     * despertar para a primeira resposta sair mais rápido.
     */
    fun prewarm(text: String, voiceId: String, model: String, apiKey: String) {
        if (shutDown || apiKey.isBlank() || voiceId.isBlank()) return
        val clean = sanitizeForSpeech(text)
        if (clean.isBlank() || !cacheable(clean)) return
        val target = cacheFile(clean, voiceId, model)
        if (target.exists() && target.length() > MIN_MP3_BYTES) return
        if (job?.isActive == true) return

        val myGeneration = generation
        val work = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            try {
                val bytes = download(clean, voiceId, model, apiKey, myGeneration)
                if (myGeneration == generation) saveCache(target, bytes)
            } catch (cancelled: CancellationException) {
                return@launch
            } catch (error: Throwable) {
                Log.d(TAG, "Pré-aquecimento do Fish Audio falhou", error)
            } finally {
                if (job === self) job = null
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

        val current = player
        player = null
        if (current != null) {
            mainHandler.post {
                synchronized(playerLock) {
                    runCatching { current.stop() }
                    runCatching { current.reset() }
                    runCatching { current.release() }
                }
            }
        }
        VoiceLevel.finish()
    }

    fun shutdown() {
        if (shutDown) return
        shutDown = true
        stop()
        scope.cancel()
    }

    // --------------------------------------------------------------- download

    private fun download(
        text: String,
        voiceId: String,
        model: String,
        apiKey: String,
        myGeneration: Int
    ): ByteArray {
        val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "audio/mpeg")
            setRequestProperty("model", model)
        }
        connection = conn
        try {
            val body = JSONObject()
                .put("text", text)
                .put("reference_id", voiceId)
                .put("format", "mp3")
                .put("language", "pt")
                .put("normalize", true)
                .put("latency", "balanced")
                .put("mp3_bitrate", 128)
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = conn.errorStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                throw IllegalStateException(friendlyError(code, detail))
            }

            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            conn.inputStream.use { input ->
                while (myGeneration == generation) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_AUDIO_BYTES) {
                        throw IllegalStateException("O áudio passou do tamanho máximo.")
                    }
                }
            }
            val bytes = out.toByteArray()
            if (bytes.size < MIN_MP3_BYTES) {
                throw IllegalStateException("O Fish Audio não retornou áudio.")
            }
            return bytes
        } finally {
            if (connection === conn) connection = null
            runCatching { conn.disconnect() }
        }
    }

    // ------------------------------------------------------------------ áudio

    private suspend fun playFile(file: File, myGeneration: Int) {
        val done = CompletableDeferred<Unit>()
        mainHandler.post {
            if (myGeneration != generation) {
                done.complete(Unit)
                return@post
            }

            val mp = try {
                MediaPlayer()
            } catch (error: Throwable) {
                done.completeExceptionally(error)
                return@post
            }
            player = mp
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setDataSource(file.absolutePath)
                mp.setOnPreparedListener { prepared ->
                    if (myGeneration != generation) {
                        release(prepared)
                        done.complete(Unit)
                    } else {
                        VoiceLevel.beginSystem()
                        runCatching { prepared.start() }.onFailure { error ->
                            release(prepared)
                            done.completeExceptionally(error)
                        }
                    }
                }
                mp.setOnCompletionListener { finished ->
                    release(finished)
                    done.complete(Unit)
                }
                mp.setOnErrorListener { broken, what, extra ->
                    release(broken)
                    done.completeExceptionally(
                        IllegalStateException("O player não conseguiu tocar o áudio ($what/$extra).")
                    )
                    true
                }
                mp.prepareAsync()
            } catch (error: Throwable) {
                release(mp)
                done.completeExceptionally(error)
            }
        }
        done.await()
    }

    private fun release(mp: MediaPlayer) {
        synchronized(playerLock) {
            if (player === mp) player = null
            runCatching { mp.stop() }
            runCatching { mp.reset() }
            runCatching { mp.release() }
        }
    }

    // ------------------------------------------------------------------- cache

    private fun cacheFile(text: String, voiceId: String, model: String): File {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$model|$voiceId|$text".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$digest.mp3")
    }

    private fun cacheable(text: String) = text.length <= MAX_CACHEABLE_CHARS

    private fun saveCache(target: File, bytes: ByteArray) {
        runCatching {
            val temp = File(target.parentFile, target.name + "." + System.nanoTime() + ".tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                target.writeBytes(bytes)
                temp.delete()
            }
            pruneCache()
        }
    }

    private fun pruneCache() {
        val files = cacheDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_CACHE_FILES).forEach { runCatching { it.delete() } }
    }

    private fun friendlyError(code: Int, body: String): String {
        val detail = runCatching {
            JSONObject(body).optString("message")
        }.getOrNull().orEmpty().ifBlank { body.take(160) }
        return when (code) {
            400 -> "Voz ou requisição inválida: $detail"
            401, 403 -> "API key do Fish Audio inválida ou sem permissão."
            402 -> "Sem crédito no Fish Audio para esse modelo. Use o modelo grátis."
            429 -> "Limite do Fish Audio atingido. Tente de novo em instantes."
            in 500..599 -> "Fish Audio indisponível agora."
            else -> "Erro do Fish Audio ($code): $detail"
        }
    }

    companion object {
        private const val TAG = "FishAudioTtsPlayer"
        private const val API_URL = "https://api.fish.audio/v1/tts"
        private const val MIN_MP3_BYTES = 512
        private const val MAX_AUDIO_BYTES = 12 * 1024 * 1024
        private const val MAX_CACHE_FILES = 40
        private const val MAX_CACHEABLE_CHARS = 300
    }
}
