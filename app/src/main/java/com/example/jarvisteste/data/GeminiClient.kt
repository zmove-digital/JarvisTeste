package com.example.jarvisteste.data

import android.util.Log
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
    val raw: String = "",
    val retryAfterSeconds: Int? = null
) : Exception(message)

/**
 * Cliente da API do Gemini (sem backend).
 *
 * Usa a **Interactions API** (`/v1beta/interactions`): é a interface recomendada
 * pelo Google e a que aceita as chaves novas no formato `AQ.` — o antigo
 * `generateContent` responde 429 para essas chaves. A chave é do próprio
 * usuário e vai apenas para o Google.
 *
 * Além de conversar, o cliente sabe executar *function calling*: o modelo pode
 * pedir uma das [ToolSpec] passadas em [ask]; o app roda a ação no aparelho e
 * devolve o resultado, e só então o modelo fala a resposta final.
 *
 * A conversa é mantida no próprio aparelho (`store = false` no servidor), então
 * o histórico curto de [ConversationMemory] é reenviado a cada pergunta.
 */
class GeminiClient(private val apiKey: String) {

    companion object {
        private const val TAG = "GeminiClient"
        private const val MODELS_URL =
            "https://generativelanguage.googleapis.com/v1beta/models"
        private const val INTERACTIONS_URL =
            "https://generativelanguage.googleapis.com/v1beta/interactions"
        private const val MAX_TOOL_ROUNDS = 4
        private val MODELS = listOf(
            // O alias acompanha o modelo lite atual e é o mais econômico.
            "gemini-flash-lite-latest",
            "gemini-3.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-3.6-flash"
        )
        private val TOOL_ERROR = Regex(
            "tool|function|declaration|schema|parameters",
            RegexOption.IGNORE_CASE
        )
        private val SEARCH_ERROR = Regex(
            "google.?search|grounding|search",
            RegexOption.IGNORE_CASE
        )

        /**
         * Quando o Google responde 429, o limite costuma ser de curto prazo
         * (por minuto). Guardamos um instante de bloqueio para não disparar
         * várias requisições inúteis na sequência.
         */
        @Volatile
        private var quotaBlockedUntil = 0L
        @Volatile
        private var consecutiveQuotaHits = 0
        private const val DEFAULT_QUOTA_WAIT_SECONDS = 30

        private fun quotaMessage(seconds: Int? = null): String {
            val wait = seconds ?: DEFAULT_QUOTA_WAIT_SECONDS
            return "Limite de uso do Gemini atingido. Tente de novo em $wait segundos. " +
                "Se continuar, a cota gratuita do dia acabou."
        }

        /** Depois de duas negativas seguidas, a cota diária acabou mesmo. */
        private fun dailyQuotaMessage(): String =
            "A cota gratuita do Gemini acabou por hoje. Ela reinicia em algumas horas. " +
                "Ative o faturamento no Google AI Studio para usar sem parar."

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

        if (System.currentTimeMillis() < quotaBlockedUntil) {
            val remaining = ((quotaBlockedUntil - System.currentTimeMillis()) / 1000L)
                .toInt()
                .coerceAtLeast(1)
            val message = if (consecutiveQuotaHits >= 2) {
                dailyQuotaMessage()
            } else {
                quotaMessage(remaining)
            }
            return@withContext Result.failure(
                GeminiException(429, message, retryAfterSeconds = remaining)
            )
        }

        for (model in MODELS) {
            // Tenta com busca do Google + ações, depois só ações, depois só texto.
            // Assim uma declaração de ferramenta rejeitada nunca deixa o usuário sem
            // resposta.
            val variants = mutableListOf<Pair<List<ToolSpec>, Boolean>>()
            if (tools.isNotEmpty()) {
                if (googleSearchAvailable) variants.add(tools to true)
                variants.add(tools to false)
            }
            variants.add(emptyList<ToolSpec>() to false)

            var error: Throwable? = null
            for ((activeTools, grounding) in variants) {
                val attempt = runConversation(model, message, history, activeTools, grounding)
                if (attempt.isSuccess) {
                    consecutiveQuotaHits = 0
                    return@withContext attempt
                }

                error = attempt.exceptionOrNull()
                val failure = error as? GeminiException
                val toolRejected = failure?.httpCode == 400 &&
                    TOOL_ERROR.containsMatchIn(failure.raw)
                if (toolRejected) {
                    if (grounding && SEARCH_ERROR.containsMatchIn(failure?.raw.orEmpty())) {
                        googleSearchAvailable = false
                    }
                    continue
                }
                break
            }

            lastFailure = error
            val failure = error as? GeminiException
            if (failure?.httpCode == 429) {
                // Não adianta tentar os outros modelos: a cota é do projeto.
                consecutiveQuotaHits++
                val wait = (failure.retryAfterSeconds ?: DEFAULT_QUOTA_WAIT_SECONDS)
                    .coerceIn(5, 600)
                quotaBlockedUntil = System.currentTimeMillis() + wait * 1000L
                break
            }
            if (failure?.tryNextModel != true) break
        }

        Result.failure(lastFailure ?: GeminiException(null, "Falha ao consultar o Gemini."))
    }

    /** Faz uma chamada curta só para confirmar que a chave funciona. */
    suspend fun validate(): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(GeminiException(null, "Informe a chave da API."))
        }

        val input = JSONArray().put(
            userInput("Responda apenas com a palavra OK.")
        )
        var lastFailure: Throwable? = null
        for (model in MODELS) {
            val attempt = postInteraction(model, input, emptyList(), 64, grounding = false)
            if (attempt.isSuccess) return@withContext Result.success(model)

            val error = attempt.exceptionOrNull() ?: continue
            lastFailure = error
            if ((error as? GeminiException)?.tryNextModel != true) break
        }

        Result.failure(lastFailure ?: GeminiException(null, "Chave inválida."))
    }

    /**
     * Lista os modelos que esta chave pode usar para conversar. Serve de
     * diagnóstico quando o Google aposenta um modelo ou muda a cota.
     */
    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("$MODELS_URL?pageSize=200")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("x-goog-api-key", apiKey)
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            Log.d(TAG, "GET models -> HTTP $code")

            if (code !in 200..299) {
                val errorMessage = runCatching {
                    JSONObject(body).getJSONObject("error").getString("message")
                }.getOrDefault("HTTP $code")
                return@withContext Result.failure(
                    GeminiException(code, friendlyMessage(code, errorMessage), raw = errorMessage)
                )
            }

            val models = JSONObject(body).optJSONArray("models") ?: JSONArray()
            val names = mutableListOf<String>()
            for (index in 0 until models.length()) {
                val item = models.optJSONObject(index) ?: continue
                val name = item.optString("name").removePrefix("models/")
                val methods = item.optJSONArray("supportedGenerationMethods")
                val methodList = buildList {
                    if (methods != null) {
                        for (i in 0 until methods.length()) add(methods.optString(i))
                    }
                }
                val canChat = methodList.isEmpty() || methodList.contains("generateContent")
                if (canChat) names.add(name)
                Log.d(TAG, "Modelo $name -> $methodList")
            }
            Result.success(names)
        } catch (error: Exception) {
            Result.failure(GeminiException(null, "Falha ao listar modelos: ${error.message}"))
        } finally {
            connection?.disconnect()
        }
    }

    /** Teste rápido da Interactions API com o modelo informado. */
    suspend fun probeInteractions(model: String): Result<String> = withContext(Dispatchers.IO) {
        val input = JSONArray().put(userInput("Oi"))
        postInteraction(model, input, emptyList(), 64, grounding = false).map { model }
    }

    /** Teste rápido de um modelo: uma resposta curta, só para ver se há cota. */
    suspend fun probe(model: String): Result<String> = probeInteractions(model)

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
        val input = JSONArray()
        history.forEach { (user, assistant) ->
            input.put(userInput(user))
            input.put(modelOutput(assistant))
        }
        input.put(userInput(message))

        val pediuNavegador = Regex(
            "navegador|browser|site|url|na internet",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(message)
        var round = 0

        while (true) {
            val response = postInteraction(model, input, tools, 800, grounding)
                .getOrElse { return Result.failure(it) }

            val calls = functionCalls(response)
            if (calls.isNotEmpty() && tools.isNotEmpty() && round < MAX_TOOL_ROUNDS) {
                round++
                // A interação é sem estado no servidor: devolvemos os passos do
                // modelo exatamente como vieram antes dos resultados.
                appendSteps(input, steps(response))

                calls.forEach { call ->
                    val name = call.optString("name")
                    val args = call.optJSONObject("arguments") ?: JSONObject()
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

                    input.put(
                        functionResult(
                            name = name,
                            callId = call.optString("id"),
                            observation = observation
                        )
                    )
                }
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

    private fun postInteraction(
        model: String,
        input: JSONArray,
        tools: List<ToolSpec>,
        maxOutputTokens: Int,
        grounding: Boolean
    ): Result<JSONObject> {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(INTERACTIONS_URL)
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }

            val payload = buildRequestBody(model, input, tools, maxOutputTokens, grounding)
            connection.outputStream.use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            Log.d(TAG, "POST interactions/$model -> HTTP $code (${body.length} bytes)")

            if (code in 200..299) {
                val json = JSONObject(body)
                val status = json.optString("status")
                if (status == "failed") {
                    val message = json.optJSONArray("errors")
                        ?.optJSONObject(0)
                        ?.optString("message")
                        .orEmpty()
                        .ifBlank { "interação falhou" }
                    Log.w(TAG, "Interação $model falhou: $message")
                    Result.failure(
                        GeminiException(
                            null,
                            "Não consegui falar com o Gemini: $message",
                            raw = message
                        )
                    )
                } else {
                    Result.success(json)
                }
            } else {
                val errorMessage = runCatching {
                    JSONObject(body).getJSONObject("error").getString("message")
                }.getOrDefault("HTTP $code")
                val retryAfter = connection?.getHeaderField("Retry-After")
                    ?.trim()
                    ?.toIntOrNull()
                Log.w(TAG, "Gemini $model falhou: $errorMessage")
                Log.w(TAG, "Corpo do erro: ${body.take(900)}")
                logQuotaHeaders(connection, code)
                Result.failure(
                    GeminiException(
                        httpCode = code,
                        message = friendlyMessage(code, errorMessage, retryAfter),
                        tryNextModel = shouldTryNext(code, errorMessage),
                        raw = errorMessage,
                        retryAfterSeconds = retryAfter
                    )
                )
            }
        } catch (timeout: SocketTimeoutException) {
            Result.failure(
                GeminiException(null, "O Gemini demorou demais para responder. Tente novamente.")
            )
        } catch (dns: UnknownHostException) {
            Result.failure(GeminiException(null, "Sem conexão com a internet."))
        } catch (error: Exception) {
            Result.failure(
                GeminiException(null, "Falha de rede: ${error.message ?: "erro desconhecido"}")
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun logQuotaHeaders(connection: HttpURLConnection?, code: Int) {
        if (connection == null) return
        val interesting = connection.headerFields
            .filterKeys { key ->
                key != null && (
                    key.contains("ratelimit", ignoreCase = true) ||
                        key.contains("retry", ignoreCase = true) ||
                        key.contains("quota", ignoreCase = true)
                    )
            }
        Log.d(TAG, "HTTP $code headers: $interesting")
    }

    private fun buildRequestBody(
        model: String,
        input: JSONArray,
        tools: List<ToolSpec>,
        maxOutputTokens: Int,
        grounding: Boolean
    ): String {
        val body = JSONObject()
            .put("model", model)
            .put("input", input)
            .put("system_instruction", SYSTEM_INSTRUCTION)
            // A conversa vive só no aparelho; o Google não guarda o histórico.
            .put("store", false)
            .put(
                "generation_config",
                JSONObject().put("max_output_tokens", maxOutputTokens)
            )

        val toolsArray = JSONArray()
        if (grounding) toolsArray.put(JSONObject().put("type", "google_search"))
        tools.forEach { toolsArray.put(toolDeclaration(it)) }
        if (toolsArray.length() > 0) body.put("tools", toolsArray)
        return body.toString()
    }

    private fun toolDeclaration(spec: ToolSpec): JSONObject = JSONObject()
        .put("type", "function")
        .put("name", spec.name)
        .put("description", spec.description)
        .put("parameters", lowercaseTypes(spec.parameters))

    /**
     * O schema das ferramentas usa os tipos em maiúsculas (`OBJECT`, `STRING`),
     * herdados do `generateContent`. A Interactions API espera JSON Schema em
     * minúsculas, então normalizamos antes de enviar.
     */
    private fun lowercaseTypes(value: Any?): Any? = when (value) {
        is JSONObject -> JSONObject().also { out ->
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = value.opt(key)
                out.put(key, if (key == "type" && item is String) item.lowercase() else lowercaseTypes(item))
            }
        }
        is JSONArray -> JSONArray().also { out ->
            for (index in 0 until value.length()) out.put(lowercaseTypes(value.opt(index)))
        }
        else -> value
    }

    private fun userInput(text: String): JSONObject = JSONObject()
        .put("type", "user_input")
        .put(
            "content",
            JSONArray().put(JSONObject().put("type", "text").put("text", text))
        )

    private fun modelOutput(text: String): JSONObject = JSONObject()
        .put("type", "model_output")
        .put(
            "content",
            JSONArray().put(JSONObject().put("type", "text").put("text", text))
        )

    private fun functionResult(name: String, callId: String, observation: String): JSONObject =
        JSONObject()
            .put("type", "function_result")
            .put("name", name)
            .put("call_id", callId)
            .put(
                "result",
                JSONArray().put(JSONObject().put("type", "text").put("text", observation))
            )

    private fun steps(response: JSONObject): JSONArray =
        response.optJSONArray("steps") ?: JSONArray()

    private fun appendSteps(target: JSONArray, source: JSONArray) {
        for (index in 0 until source.length()) {
            val step = source.optJSONObject(index) ?: continue
            target.put(step)
        }
    }

    private fun functionCalls(response: JSONObject): List<JSONObject> {
        val calls = mutableListOf<JSONObject>()
        val steps = steps(response)
        for (index in 0 until steps.length()) {
            val step = steps.optJSONObject(index) ?: continue
            if (step.optString("type") == "function_call") calls.add(step)
        }
        return calls
    }

    private fun extractText(response: JSONObject): String {
        val builder = StringBuilder()
        val steps = steps(response)
        for (index in 0 until steps.length()) {
            val step = steps.optJSONObject(index) ?: continue
            if (step.optString("type") != "model_output") continue
            val content = step.optJSONArray("content") ?: continue
            for (item in 0 until content.length()) {
                val block = content.optJSONObject(item) ?: continue
                if (block.optString("type") != "text") continue
                val text = block.optString("text")
                if (text.isNotBlank()) {
                    if (builder.isNotEmpty()) builder.append(' ')
                    builder.append(text)
                }
            }
        }
        return builder.toString()
    }

    private fun shouldTryNext(code: Int, message: String): Boolean {
        // Cota (429) não é tratada aqui: é do projeto, então o ask() para de tentar.
        if (code == 404 || code == 503) return true
        val lower = message.lowercase()
        return code == 400 && (
            lower.contains("not found") ||
                lower.contains("not supported") ||
                lower.contains("unknown model") ||
                lower.contains("model is not available")
            )
    }

    private fun friendlyMessage(
        code: Int,
        raw: String,
        retryAfter: Int? = null
    ): String = when {
        code == 400 && raw.contains("API key", ignoreCase = true) ->
            "Chave da API inválida. Confira e tente novamente."
        code == 401 || code == 403 ->
            "Chave não autorizada. Verifique se ela está ativa no Google AI Studio."
        code == 429 || raw.contains("quota", ignoreCase = true) ->
            if (consecutiveQuotaHits >= 1) dailyQuotaMessage() else quotaMessage(retryAfter)
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
