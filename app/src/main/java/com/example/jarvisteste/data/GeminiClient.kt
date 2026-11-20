package com.example.jarvisteste.data

import com.example.jarvisteste.actions.ToolSpec
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
    val tryNextModel: Boolean = false,
    val raw: String = ""
) : Exception(message)

/**
 * Cliente mínimo e direto da API do Gemini (sem backend).
 * A chave vem do próprio usuário e é enviada apenas para o Google.
 *
 * Além de conversar, o cliente sabe executar *function calling*: o modelo pode
 * pedir uma das [ToolSpec] passadas em [ask]; o app roda a ação no aparelho e
 * devolve o resultado, e só então o modelo fala a resposta final.
 */
class GeminiClient(private val apiKey: String) {

    companion object {
        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MAX_TOOL_ROUNDS = 4
        private val MODELS = listOf(
            "gemini-3.5-flash-lite",
            "gemini-2.5-flash-lite"
        )
        private val TOOL_ERROR = Regex("tool|function|declaration|schema", RegexOption.IGNORE_CASE)
        private const val SYSTEM_INSTRUCTION =
            "Você é Jarvis, um assistente pessoal inteligente, educado, natural, " +
                "prestativo e levemente bem-humorado. Fale sempre em português brasileiro. " +
                "Como suas respostas serão faladas em voz alta, seja conciso: no máximo " +
                "3 frases curtas, sem listas, sem markdown e sem emojis, a menos que o " +
                "usuário peça detalhes.\n" +
                "REGRA PRINCIPAL: pergunta é para ser RESPONDIDA com texto. Fatos, " +
                "conhecimento geral, temas, notícias, dúvidas e informações devem virar " +
                "resposta falada. NUNCA chame uma ferramenta apenas porque a pergunta é sobre " +
                "algo atual. Você tem acesso a uma busca na internet para obter informações " +
                "recentes: use-a para fundamentar a resposta, sem pedir ao usuário que abra " +
                "um navegador.\n" +
                "Use consultar_clima para QUALQUER pergunta sobre clima, tempo, temperatura, " +
                "chuva ou previsão, e responda com os dados obtidos.\n" +
                "Use pesquisar_web SOMENTE se o usuário pedir explicitamente para abrir o " +
                "navegador, abrir um site ou pesquisar na internet. Nunca para responder uma " +
                "pergunta.\n" +
                "Ações no aparelho: use abrir_playlist_spotify para tocar playlists; " +
                "abrir_conversa_whatsapp para conversar ou ligar no zap (com acao \"ligar\" " +
                "quando pedirem chamada ou viva-voz); ligar_telefone para chamada telefônica " +
                "normal; controlar_midia para play/pause; definir_alarme e definir_timer. " +
                "abrir_conversa_whatsapp devolve uma pergunta de confirmação: repita ao " +
                "usuário exatamente o que ela disse e pare; não chame outra ferramenta até " +
                "ele responder. Se uma ferramenta falhar, explique em poucas palavras. " +
                "Nunca invente um resultado de ação."
    }

    @Volatile
    private var googleSearchAvailable = true

    suspend fun ask(
        message: String,
        tools: List<ToolSpec> = emptyList()
    ): Result<GeminiReply> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                GeminiException(null, "Chave da API não configurada. Abra os ajustes.")
            )
        }

        val history = ConversationMemory.snapshot()
        var lastFailure: Throwable? = null

        for (model in MODELS) {
            // Tenta com busca do Google + ações, depois só ações, depois só texto.
            val variants = mutableListOf<Pair<List<ToolSpec>, Boolean>>()
            if (googleSearchAvailable) variants.add(tools to true)
            variants.add(tools to false)

            var error: Throwable? = null
            for ((activeTools, grounding) in variants) {
                val attempt = runConversation(model, message, history, activeTools, grounding)
                if (attempt.isSuccess) return@withContext attempt
                error = attempt.exceptionOrNull()
                val toolRejected = error is GeminiException && error.httpCode == 400 &&
                    TOOL_ERROR.containsMatchIn(error.raw)
                if (toolRejected && grounding) {
                    googleSearchAvailable = false
                    continue
                }
                if (!toolRejected) break
            }

            lastFailure = error
            if ((error as? GeminiException)?.tryNextModel != true) break
        }

        Result.failure(lastFailure ?: GeminiException(null, "Falha ao consultar o Gemini."))
    }

    /** Faz uma chamada curta só para confirmar que a chave funciona. */
    suspend fun validate(): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(GeminiException(null, "Informe a chave da API."))
        }

        val contents = buildContents(emptyList(), "Responda apenas com a palavra OK.")
        var lastFailure: Throwable? = null
        for (model in MODELS) {
            val attempt = post(model, contents, emptyList(), 16)
            if (attempt.isSuccess) return@withContext Result.success(model)

            val error = attempt.exceptionOrNull() ?: continue
            lastFailure = error
            if ((error as? GeminiException)?.tryNextModel != true) break
        }

        Result.failure(lastFailure ?: GeminiException(null, "Chave inválida."))
    }

    /**
     * Conversa com o modelo em uma ou mais rodadas, executando ferramentas até
     * ele responder com texto.
     */
    private suspend fun runConversation(
        model: String,
        message: String,
        history: List<Pair<String, String>>,
        tools: List<ToolSpec>,
        grounding: Boolean = false
    ): Result<GeminiReply> {
        val contents = buildContents(history, message)
        val pediuNavegador = Regex(
            "navegador|browser|site|url|na internet",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(message)
        var round = 0

        while (true) {
            val response = post(model, contents, tools, 800, grounding)
                .getOrElse { return Result.failure(it) }

            val call = firstFunctionCall(response)
            if (call != null && tools.isNotEmpty() && round < MAX_TOOL_ROUNDS) {
                round++
                val name = call.optString("name")
                val args = call.optJSONObject("args") ?: JSONObject()
                val spec = tools.firstOrNull { it.name == name }
                val observation = if (name == "pesquisar_web" && !pediuNavegador) {
                    // Trava determinística: pergunta comum não pode virar aba de browser.
                    "O usuário NÃO pediu para abrir o navegador. Não abra o browser. " +
                        "Responda à pergunta em texto usando a busca interna do Gemini."
                } else if (spec == null) {
                    "Ferramenta desconhecida: $name"
                } else {
                    runCatching { spec.run(args) }
                        .getOrElse { "Não consegui concluir a ação (${it.message ?: "erro"})." }
                }

                modelContent(response)?.let { contents.put(it) }
                contents.put(functionResponse(name, observation))
                continue
            }

            val text = extractText(response)
            if (text.isBlank()) {
                return Result.failure(
                    GeminiException(null, "O Gemini respondeu sem texto. Tente novamente.")
                )
            }
            ConversationMemory.add(message, text)
            return Result.success(GeminiReply(text.trim(), model))
        }
    }

    private fun post(
        model: String,
        contents: JSONArray,
        tools: List<ToolSpec>,
        maxOutputTokens: Int,
        grounding: Boolean = false
    ): Result<JSONObject> {
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

            val payload = buildRequestBody(contents, tools, maxOutputTokens, grounding)
            connection.outputStream.use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code in 200..299) {
                Result.success(JSONObject(body))
            } else {
                val errorMessage = runCatching {
                    JSONObject(body).getJSONObject("error").getString("message")
                }.getOrDefault("HTTP $code")
                Result.failure(
                    GeminiException(
                        httpCode = code,
                        message = friendlyMessage(code, errorMessage),
                        tryNextModel = shouldTryNext(code, errorMessage),
                        raw = errorMessage
                    )
                )
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

    private fun buildContents(history: List<Pair<String, String>>, message: String): JSONArray {
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
        return contents
    }

    private fun buildRequestBody(
        contents: JSONArray,
        tools: List<ToolSpec>,
        maxOutputTokens: Int,
        grounding: Boolean
    ): String {
        val body = JSONObject()
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

        val toolsArray = JSONArray()
        if (grounding) {
            toolsArray.put(JSONObject().put("google_search", JSONObject()))
        }
        if (tools.isNotEmpty()) {
            val declarations = JSONArray()
            tools.forEach { declarations.put(it.declaration()) }
            toolsArray.put(JSONObject().put("functionDeclarations", declarations))
        }
        if (toolsArray.length() > 0) body.put("tools", toolsArray)
        return body.toString()
    }

    private fun modelContent(response: JSONObject): JSONObject? =
        response.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")

    private fun firstFunctionCall(response: JSONObject): JSONObject? {
        val parts = modelContent(response)?.optJSONArray("parts") ?: return null
        for (index in 0 until parts.length()) {
            val call = parts.optJSONObject(index)?.optJSONObject("functionCall")
            if (call != null) return call
        }
        return null
    }

    private fun functionResponse(name: String, observation: String): JSONObject = JSONObject()
        .put("role", "user")
        .put(
            "parts",
            JSONArray().put(
                JSONObject().put(
                    "functionResponse",
                    JSONObject()
                        .put("name", name)
                        .put("response", JSONObject().put("resultado", observation))
                )
            )
        )

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
    .replace(Regex("【[^】]*】"), " ")
    .replace(Regex("\\[\\d+(?:,\\s*\\d+)*]"), " ")
    .replace(Regex("[*_#>~|]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
