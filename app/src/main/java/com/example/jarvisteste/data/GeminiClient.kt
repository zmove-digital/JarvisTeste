package com.example.jarvisteste.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

data class GeminiReply(val text: String, val model: String)

class GeminiException(
    val httpCode: Int?,
    message: String,
    val tryNextModel: Boolean = false
) : Exception(message)

/**
 * Cliente mínimo e direto da API do Gemini (sem backend).
 * A chave vem do próprio usuário e é enviada apenas para o Google.
 */
class GeminiClient(private val apiKey: String) {

    companion object {
        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models"
        private val MODELS = listOf(
            "gemini-3.5-flash-lite",
            "gemini-2.5-flash-lite"
        )
        private const val SYSTEM_INSTRUCTION =
            "Você é Jarvis, um assistente pessoal inteligente, educado, natural, " +
                "prestativo e levemente bem-humorado. Fale sempre em português brasileiro. " +
                "Como suas respostas serão faladas em voz alta, seja conciso: no máximo " +
                "3 frases curtas, sem listas, sem markdown e sem emojis, a menos que o " +
                "usuário peça detalhes."
    }

    suspend fun ask(message: String): Result<GeminiReply> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                GeminiException(null, "Chave da API não configurada. Abra os ajustes.")
            )
        }

        val history = ConversationMemory.snapshot()
        var lastFailure: Throwable? = null

        for (model in MODELS) {
            val attempt = request(
                model = model,
                message = message,
                history = history,
                maxOutputTokens = 800
            )
            if (attempt.isSuccess) {
                val reply = attempt.getOrThrow()
                ConversationMemory.add(message, reply.text)
                return@withContext Result.success(reply)
            }

            val error = attempt.exceptionOrNull() ?: continue
            lastFailure = error
            val canFallback = (error as? GeminiException)?.tryNextModel == true
            if (!canFallback) break
        }

        Result.failure(lastFailure ?: GeminiException(null, "Falha ao consultar o Gemini."))
    }

    /** Faz uma chamada curta só para confirmar que a chave funciona. */
    suspend fun validate(): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                GeminiException(null, "Informe a chave da API.")
            )
        }

        var lastFailure: Throwable? = null
        for (model in MODELS) {
            val attempt = request(
                model = model,
                message = "Responda apenas com a palavra OK.",
                history = emptyList(),
                maxOutputTokens = 16
            )
            if (attempt.isSuccess) return@withContext Result.success(model)

            val error = attempt.exceptionOrNull() ?: continue
            lastFailure = error
            if ((error as? GeminiException)?.tryNextModel != true) break
        }

        Result.failure(lastFailure ?: GeminiException(null, "Chave inválida."))
    }

    private fun request(
        model: String,
        message: String,
        history: List<Pair<String, String>>,
        maxOutputTokens: Int
    ): Result<GeminiReply> {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("$BASE_URL/$model:generateContent")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }

            val payload = buildRequestBody(message, history, maxOutputTokens)
            connection.outputStream.use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code in 200..299) {
                val text = extractText(JSONObject(body))
                if (text.isBlank()) {
                    Result.failure(
                        GeminiException(code, "O Gemini respondeu sem texto. Tente novamente.")
                    )
                } else {
                    Result.success(GeminiReply(text.trim(), model))
                }
            } else {
                val errorMessage = runCatching {
                    JSONObject(body).getJSONObject("error").getString("message")
                }.getOrDefault("HTTP $code")
                Result.failure(GeminiException(code, friendlyMessage(code, errorMessage), shouldTryNext(code, errorMessage)))
            }
        } catch (timeout: SocketTimeoutException) {
            Result.failure(GeminiException(null, "O Gemini demorou demais para responder. Tente novamente."))
        } catch (dns: UnknownHostException) {
            Result.failure(GeminiException(null, "Sem conexão com a internet."))
        } catch (error: Exception) {
            Result.failure(GeminiException(null, "Falha de rede: ${error.message ?: "erro desconhecido"}"))
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildRequestBody(
        message: String,
        history: List<Pair<String, String>>,
        maxOutputTokens: Int
    ): String {
        val contents = JSONArray()
        history.forEach { (user, model) ->
            contents.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", user)))
            )
            contents.put(
                JSONObject()
                    .put("role", "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", model)))
            )
        }
        contents.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", message)))
        )

        return JSONObject()
            .put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_INSTRUCTION)))
            )
            .put("contents", contents)
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.8)
                    .put("topP", 0.95)
                    .put("maxOutputTokens", maxOutputTokens)
            )
            .toString()
    }

    private fun extractText(response: JSONObject): String {
        val candidates = response.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: return ""
        val builder = StringBuilder()
        for (index in 0 until parts.length()) {
            val text = parts.optJSONObject(index)?.optString("text").orEmpty()
            if (text.isNotBlank()) {
                if (builder.isNotEmpty()) builder.append(' ')
                builder.append(text)
            }
        }
        return builder.toString()
    }

    private fun shouldTryNext(code: Int, message: String): Boolean {
        if (code == 404) return true
        val lower = message.lowercase()
        return code == 400 && (
            lower.contains("not found") ||
                lower.contains("not supported") ||
                lower.contains("unknown model")
            )
    }

    private fun friendlyMessage(code: Int, raw: String): String = when {
        code == 400 && raw.contains("API key", ignoreCase = true) ->
            "Chave da API inválida. Confira e tente novamente."
        code == 401 || code == 403 ->
            "Chave não autorizada. Verifique se ela está ativa no Google AI Studio."
        code == 429 ->
            "Limite gratuito atingido por agora. Aguarde um pouco e tente de novo."
        code == 404 ->
            "Modelo indisponível para esta chave."
        code in 500..599 ->
            "O Gemini está instável agora. Tente novamente em instantes."
        else ->
            "Erro do Gemini ($code): $raw"
    }
}

/** Histórico curto mantido em memória — dá contexto à conversa. */
object ConversationMemory {

    private const val MAX_TURNS = 10
    private val turns = ArrayDeque<Pair<String, String>>()

    @Synchronized
    fun snapshot(): List<Pair<String, String>> = turns.toList()

    @Synchronized
    fun add(userMessage: String, assistantMessage: String) {
        turns.addLast(userMessage to assistantMessage)
        while (turns.size > MAX_TURNS) turns.removeFirst()
    }

    @Synchronized
    fun clear() = turns.clear()
}

/** Remove marcações que não devem ser lidas em voz alta. */
fun sanitizeForSpeech(text: String): String = text
    .replace(Regex("```[\\s\\S]*?```"), " ")
    .replace(Regex("`([^`]*)`"), "$1")
    .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
    .replace(Regex("[*_#>~|]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
