package com.example.jarvisteste.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.jarvisteste.accessibility.WhatsAppCallBridge
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.text.Normalizer

private const val SPOTIFY_PACKAGE = "com.spotify.music"
private const val WHATSAPP_PACKAGE = "com.whatsapp"
private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

/**
 * Ações que o Jarvis executa no aparelho, expostas ao Gemini como ferramentas:
 * abrir aplicativos, pesquisar na web, ligar em viva-voz, controlar a mídia e
 * criar alarmes/timers.
 *
 * Cada ferramenta devolve uma frase curta que volta ao modelo como resultado da
 * ação, para ele confirmar ao usuário o que aconteceu (ou explicar a falha).
 */
object JarvisActions {

    fun tools(context: Context): List<ToolSpec> = listOf(
        openApp(context),
        openSpotify(context),
        openWhatsappChat(context),
        webSearch(context),
        weather(context),
        phoneCall(context),
        mediaControl(context),
        setAlarm(context),
        setTimer(context)
    )

    // -------------------------------------------------------------- abrir app

    private fun openApp(context: Context): ToolSpec = ToolSpec(
        name = "abrir_app",
        description = "Abre um aplicativo instalado no celular. Use quando o usuário pedir " +
            "para abrir, entrar ou iniciar um app (ex.: Spotify, WhatsApp, YouTube, Instagram).",
        parameters = schema(
            "nome" to text("Nome do aplicativo como o usuário falou, ex.: \"Spotify\"."),
            required = listOf("nome")
        ),
        run = { args ->
            val nome = args.optString("nome").trim()
            val entry = AppIndex(context).find(nome)
            if (entry == null) {
                "Não encontrei nenhum aplicativo chamado \"$nome\"."
            } else {
                val launch = context.packageManager.getLaunchIntentForPackage(entry.packageName)
                if (launch == null) {
                    "Encontrei ${entry.label}, mas ele não tem uma tela inicial para abrir."
                } else {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(launch)
                        "Abri o ${entry.label}."
                    } catch (error: Exception) {
                        "Encontrei ${entry.label}, mas não consegui abrir. Permita que o " +
                            "Jarvis apareça sobre outros apps nos ajustes."
                    }
                }
            }
        }
    )

    // -------------------------------------------------- deep link do Spotify

    private fun openSpotify(context: Context): ToolSpec = ToolSpec(
        name = "abrir_playlist_spotify",
        description = "Abre uma playlist, álbum, artista ou faixa no Spotify, ou faz uma busca " +
            "no Spotify quando o usuário só falou o nome. Use quando pedirem para tocar, " +
            "ouvir ou colocar uma playlist/música no Spotify. Aceita o nome ou um link/ID do " +
            "Spotify. Com link/ID direto, a reprodução é iniciada; com apenas um nome, abre " +
            "a busca para o usuário escolher. Não use para pausar ou passar faixa " +
            "(use controlar_midia) nem para abrir o app sem conteúdo específico " +
            "(use abrir_app).",
        parameters = schema(
            "consulta" to text(
                "Nome da playlist, álbum, artista ou faixa; ou link do Spotify " +
                    "(open.spotify.com/... ou spotify:...)."
            ),
            "tipo" to choice(
                "Tipo de conteúdo. Use \"playlist\" quando o usuário pedir uma playlist.",
                listOf("playlist", "album", "artista", "faixa", "busca")
            ),
            required = listOf("consulta")
        ),
        run = { args ->
            val consulta = args.optString("consulta").trim()
            val tipo = args.optString("tipo").trim().lowercase()
            when {
                consulta.isEmpty() -> "Não entendi o que abrir no Spotify."
                !isInstalled(context, SPOTIFY_PACKAGE) ->
                    "O Spotify não está instalado neste aparelho."

                else -> {
                    val encoded = URLEncoder.encode(consulta, Charsets.UTF_8.name())
                    val target = spotifyTarget(consulta)
                    // A busca por playlist abre direto na aba "Playlists" do app.
                    val searchPath = if (tipo == "playlist") {
                        "https://open.spotify.com/search/$encoded/playlists"
                    } else {
                        "https://open.spotify.com/search/$encoded"
                    }
                    val uri = target?.uri ?: "spotify:search:" +
                        URLEncoder.encode(consulta.replace('/', ' '), Charsets.UTF_8.name())
                            .replace("+", "%20")
                    val primary = target?.uri ?: searchPath
                    val secondary = target?.webUrl ?: uri
                    val opened = launch(context, primary, SPOTIFY_PACKAGE) ||
                        launch(context, secondary, SPOTIFY_PACKAGE) ||
                        launch(context, secondary)

                    when {
                        !opened -> "Encontrei o Spotify, mas não consegui abrir o conteúdo."
                        target != null -> {
                            // Com a sessão parada, a deep link abre a playlist pausada e
                            // o comando de mídia (Nível 1) inicia a reprodução. Se já
                            // houver música tocando, enviar PLAY a pausaria no Spotify.
                            delay(1_800)
                            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                            val alreadyPlaying = audio?.isMusicActive == true
                            if (!alreadyPlaying) sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)
                            if (alreadyPlaying) {
                                "Abri o conteúdo no Spotify, que já estava tocando."
                            } else {
                                "Abri o conteúdo no Spotify e iniciei a reprodução."
                            }
                        }
                        else -> "Abri a busca por \"$consulta\" no Spotify. " +
                            "Toque na playlist que quiser ouvir."
                    }
                }
            }
        }
    )

    // ---------------------------------------------- deep link do WhatsApp

    private fun openWhatsappChat(context: Context): ToolSpec = ToolSpec(
        name = "abrir_conversa_whatsapp",
        description = "Abre a conversa do WhatsApp com um contato da agenda ou com um número " +
            "informado, ou liga para ele pelo WhatsApp em viva-voz. Sempre pede confirmação " +
            "do contato antes de agir. Use quando o usuário pedir para conversar, mandar " +
            "mensagem, falar, chamar ou ligar para alguém no WhatsApp ou Zap. Para ligação " +
            "telefônica normal (não WhatsApp), use ligar_telefone.",
        parameters = schema(
            "contato" to text("Nome do contato ou número de telefone, como o usuário falou."),
            "acao" to choice(
                "O que fazer com o contato. Use \"ligar\" se o usuário pediu chamada, " +
                    "ligação ou viva-voz; use \"abrir\" para apenas abrir a conversa.",
                listOf("abrir", "ligar")
            ),
            required = listOf("contato")
        ),
        run = { args ->
            val contato = args.optString("contato").trim()
            val acao = args.optString("acao").trim().lowercase()
            val action = if (acao == "ligar") ContactAction.CALL_WHATSAPP else ContactAction.OPEN_CHAT
            val candidates = resolveContacts(context, contato)
            when {
                candidates.isNotEmpty() -> askContactConfirmation(action, candidates)
                !hasPermission(context, Manifest.permission.READ_CONTACTS) ->
                    "Não tenho acesso à sua agenda. Permita contatos ou fale o número com DDD."
                else -> "Não encontrei nenhum contato chamado \"$contato\" na agenda."
            }
        }
    )

    private enum class ContactAction { OPEN_CHAT, CALL_WHATSAPP }

    private data class ContactMatch(val name: String, val number: String)
    private data class PendingContact(val action: ContactAction, val candidates: List<ContactMatch>)

    @Volatile
    private var pendingContact: PendingContact? = null

    /** A janela de fala após confirmar pode ser mais longa. */
    fun hasPendingContact(): Boolean = pendingContact != null

    private fun askContactConfirmation(action: ContactAction, candidates: List<ContactMatch>): String {
        pendingContact = PendingContact(action, candidates)
        val verb = if (action == ContactAction.CALL_WHATSAPP) "ligar para" else "abrir a conversa com"
        return if (candidates.size == 1) {
            val contact = candidates[0]
            "Encontrei ${contact.name}. Confirma $verb ${contact.name}? Diga sim ou não."
        } else {
            val options = candidates.mapIndexed { index, contact -> "${index + 1}) ${contact.name}" }
                .joinToString(", ")
            "Encontrei ${candidates.size} contatos: $options. Diga o número da opção ou não para cancelar."
        }
    }

    /**
     * Interpreta a resposta à confirmação antes de mandar o comando ao Gemini.
     * Devolve `null` quando não há confirmação pendente.
     */
    suspend fun confirmOrCancel(context: Context, utterance: String): String? {
        val pending = pendingContact ?: return null
        android.util.Log.i("JarvisActions", "confirmOrCancel: '$utterance' action=${pending.action}")
        val text = normalizeWords(utterance)
        if (text.isEmpty()) return null

        if (Regex("(^| )(nao|cancela|cancelar|esquece|desiste|nada)( |$)").containsMatchIn(text)) {
            pendingContact = null
            return "Cancelado."
        }

        val digit = text.firstOrNull { it.isDigit() }?.digitToInt()
        val affirmative = text == "sim" || text.startsWith("sim ") ||
            text.contains("confirm") || text.contains("pode") || text.contains("isso") ||
            text == "ok" || text.contains("certeza")
        val choice = when {
            digit != null -> pending.candidates.getOrNull(digit - 1)
            pending.candidates.size == 1 && affirmative -> pending.candidates.first()
            else -> pending.candidates.firstOrNull { contact ->
                val name = normalizeWords(contact.name)
                name.isNotEmpty() && (
                    text.contains(name) ||
                        name.split(' ').any { it.length >= 4 && text.contains(it) }
                    )
            }
        }

        if (choice == null) {
            return "Não entendi. Diga sim, o número da opção, ou não para cancelar."
        }
        pendingContact = null
        return when (pending.action) {
            ContactAction.OPEN_CHAT -> openWhatsapp(context, choice.number, choice.name)
            ContactAction.CALL_WHATSAPP -> startWhatsappCall(context, choice)
        }
    }

    private suspend fun startWhatsappCall(context: Context, contact: ContactMatch): String {
        val opened = openWhatsapp(context, contact.number, contact.name)
        if (!opened.startsWith("Abri")) return opened
        return if (WhatsAppCallBridge.enabled) {
            WhatsAppCallBridge.requestCall(contact.name)
            "Abri a conversa com ${contact.name} e vou tentar ligar no viva-voz pelo WhatsApp."
        } else {
            "Abri a conversa com ${contact.name}. Para eu ligar sozinho, ative o Acessibilidade " +
                "do Jarvis em Ajustes; senão toque no ícone de chamada."
        }
    }

    private fun normalizeWords(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Até 5 contatos, sem repetir números. */
    private fun resolveContacts(context: Context, query: String): List<ContactMatch> {
        if (looksLikeNumber(query)) return listOf(ContactMatch(query, query))
        if (!hasPermission(context, Manifest.permission.READ_CONTACTS)) return emptyList()

        val uri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
            Uri.encode(query)
        )
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val found = mutableListOf<ContactMatch>()
                while (cursor.moveToNext() && found.size < 5) {
                    val number = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: query
                    val digits = number.filter { it.isDigit() }
                    if (digits.length >= 8 && found.none { it.number.filter(Char::isDigit) == digits }) {
                        found.add(ContactMatch(name, number))
                    }
                }
                found
            }
        }.getOrNull().orEmpty()
    }

    private fun openWhatsapp(context: Context, number: String, displayName: String): String {
        val digits = normalizeBrazilianNumber(number)
        if (digits.length < 10) {
            return "O número de \"$displayName\" parece inválido para o WhatsApp."
        }

        val preferred = when {
            isInstalled(context, WHATSAPP_PACKAGE) -> WHATSAPP_PACKAGE
            isInstalled(context, WHATSAPP_BUSINESS_PACKAGE) -> WHATSAPP_BUSINESS_PACKAGE
            else -> null
        }
        val appUri = "whatsapp://send?phone=$digits"
        val webUri = "https://wa.me/$digits"

        val opened = if (preferred != null) {
            launch(context, appUri, preferred) || launch(context, webUri, preferred)
        } else {
            false
        }
        val fallback = if (opened) true else launch(context, webUri)

        return if (fallback) {
            "Abri a conversa com $displayName no WhatsApp."
        } else {
            "O WhatsApp não está instalado ou não consegui abrir a conversa."
        }
    }

    // ---------------------------------------------------------- pesquisar web

    private fun webSearch(context: Context): ToolSpec = ToolSpec(
        name = "pesquisar_web",
        description = "Abre o navegador com uma busca. Use SOMENTE quando o usuário pedir " +
            "explicitamente para abrir o navegador, abrir um site ou pesquisar na internet. " +
            "NUNCA use esta ferramenta para responder perguntas: responda você mesmo, " +
            "usando consultar_clima para clima.",
        parameters = schema(
            "consulta" to text("O que pesquisar na internet."),
            required = listOf("consulta")
        ),
        run = { args ->
            val consulta = args.optString("consulta").trim()
            val url = "https://www.google.com/search?q=" +
                URLEncoder.encode(consulta, Charsets.UTF_8.name())
            openInBrowser(context, url, "a busca por \"$consulta\"")
        }
    )

    // ------------------------------------------------------------------ clima

    private fun weather(context: Context): ToolSpec = ToolSpec(
        name = "consultar_clima",
        description = "Consulta a temperatura, as condições atuais e a previsão do tempo em " +
            "uma cidade. Use SEMPRE que o usuário perguntar sobre clima, tempo, temperatura, " +
            "chuva, calor, frio ou previsão. Nunca use pesquisar_web para clima.",
        parameters = schema(
            "cidade" to text(
                "Cidade informada pelo usuário. Deixe vazio para usar a localização do aparelho."
            ),
            "dias" to integer("Quantos dias de previsão (1 a 5). Padrão: 2."),
            required = listOf("cidade")
        ),
        run = { args ->
            WeatherService.forecast(
                context,
                args.optString("cidade").ifBlank { null },
                if (args.has("dias")) args.optInt("dias", 2) else 2
            )
        }
    )

    // -------------------------------------------------------------- ligação

    private fun phoneCall(context: Context): ToolSpec = ToolSpec(
        name = "ligar_telefone",
        description = "Faz uma ligação telefônica normal (NÃO é ligação de WhatsApp) e ativa " +
            "o viva-voz. Aceita um número ou o nome de um contato salvo na agenda.",
        parameters = schema(
            "contato" to text("Nome do contato ou número de telefone, como o usuário falou."),
            required = listOf("contato")
        ),
        run = { args ->
            val contato = args.optString("contato").trim()
            val resolved = resolveNumber(context, contato)
            when {
                resolved != null -> placeCall(context, resolved.first, resolved.second)

                looksLikeNumber(contato) -> placeCall(context, contato, contato)

                !hasPermission(context, Manifest.permission.READ_CONTACTS) ->
                    "Não encontrei \"$contato\" e não tenho acesso à sua agenda. Permita o " +
                        "acesso a contatos no app, ou diga o número com DDD."

                else -> "Não encontrei nenhum contato chamado \"$contato\". " +
                    "Confira o nome ou fale o número com DDD."
            }
        }
    )

    /** Devolve (número, nome de exibição) ou `null` se não achar na agenda. */
    private fun resolveNumber(context: Context, query: String): Pair<String, String>? {
        if (looksLikeNumber(query)) return query to query
        if (!hasPermission(context, Manifest.permission.READ_CONTACTS)) return null

        val uri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
            Uri.encode(query)
        )
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val number = cursor.getString(0) ?: return@use null
                    val name = cursor.getString(1) ?: query
                    number to name
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private suspend fun placeCall(context: Context, number: String, displayName: String): String {
        if (!hasPermission(context, Manifest.permission.CALL_PHONE)) {
            return "Preciso da permissão de telefone para ligar. Abra o Jarvis e permita."
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            // O viva-voz só pode ser ativado depois que a chamada existe de fato.
            delay(2000)
            enableSpeaker(context)
            "Ligando para $displayName em viva-voz."
        } catch (error: SecurityException) {
            "Não tenho permissão para fazer ligações."
        } catch (error: Exception) {
            "Não consegui iniciar a ligação."
        }
    }

    /** Liga o alto-falante da chamada em curso. */
    private fun enableSpeaker(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speaker = audio.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) audio.setCommunicationDevice(speaker)
            } else {
                @Suppress("DEPRECATION")
                audio.isSpeakerphoneOn = true
            }
        }
    }

    // ---------------------------------------------------------------- mídia

    private fun mediaControl(context: Context): ToolSpec = ToolSpec(
        name = "controlar_midia",
        description = "Controla a música ou o vídeo que estiver tocando agora: play, pause, " +
            "alternar (play/pause), próxima faixa, faixa anterior ou parar.",
        parameters = schema(
            "acao" to choice(
                "O que fazer com a reprodução atual.",
                listOf("play", "pause", "alternar", "proxima", "anterior", "parar")
            ),
            required = listOf("acao")
        ),
        run = { args ->
            val acao = args.optString("acao").trim().lowercase()
            val keyCode = when (acao) {
                "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
                "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                "proxima", "próxima", "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "anterior", "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                "parar", "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
                else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audio == null) {
                "Não consegui acessar o áudio do aparelho."
            } else {
                sendMediaKey(context, keyCode)
                when (acao) {
                    "play" -> "Reproduzindo."
                    "pause" -> "Pausei a reprodução."
                    "proxima", "próxima", "next" -> "Passei para a próxima faixa."
                    "anterior", "previous" -> "Voltei para a faixa anterior."
                    "parar", "stop" -> "Parei a reprodução."
                    else -> "Alternei a reprodução."
                }
            }
        }
    )

    // --------------------------------------------------------- alarme / timer

    private fun setAlarm(context: Context): ToolSpec = ToolSpec(
        name = "definir_alarme",
        description = "Cria um alarme no relógio do aparelho.",
        parameters = schema(
            "hora" to text("Horário no formato 24 horas HH:MM, ex.: \"07:30\" ou \"22:00\"."),
            "rotulo" to text("Rótulo curto do alarme, opcional."),
            required = listOf("hora")
        ),
        run = { args ->
            val hora = args.optString("hora").trim()
            val parsed = parseTime(hora)
            if (parsed == null) {
                "Não entendi o horário \"$hora\"."
            } else {
                val (h, m) = parsed
                val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, h)
                    .putExtra(AlarmClock.EXTRA_MINUTES, m)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                args.optString("rotulo").takeIf { it.isNotBlank() }
                    ?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                if (canHandle(context, intent)) {
                    context.startActivity(intent)
                    "Alarme para %02d:%02d definido.".format(h, m)
                } else {
                    "O aparelho não tem um app de relógio compatível."
                }
            }
        }
    )

    private fun setTimer(context: Context): ToolSpec = ToolSpec(
        name = "definir_timer",
        description = "Cria um timer (contagem regressiva) no relógio do aparelho.",
        parameters = schema(
            "segundos" to integer("Duração total do timer em segundos."),
            "rotulo" to text("Rótulo curto do timer, opcional."),
            required = listOf("segundos")
        ),
        run = { args ->
            val segundos = args.optInt("segundos", 0)
            if (segundos <= 0) {
                "Preciso de uma duração válida para o timer."
            } else {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, segundos)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                args.optString("rotulo").takeIf { it.isNotBlank() }
                    ?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                if (canHandle(context, intent)) {
                    context.startActivity(intent)
                    "Timer de $segundos segundos iniciado."
                } else {
                    "O aparelho não tem um app de relógio compatível."
                }
            }
        }
    )

    // ------------------------------------------------------------- utilidades

    private data class SpotifyTarget(val uri: String, val webUrl: String)

    /** Reconhece links/IDs do Spotify; um nome comum não é confundido com ID. */
    private fun spotifyTarget(value: String): SpotifyTarget? {
        val link = Regex(
            """(?:https?://)?(?:open\.)?spotify\.com/(playlist|album|artist|track)/([\w]+)""",
            RegexOption.IGNORE_CASE
        ).find(value)
            ?: Regex("""spotify:(playlist|album|artist|track):([\w]+)""", RegexOption.IGNORE_CASE)
                .find(value)
        if (link != null) {
            val type = link.groupValues[1].lowercase()
            val id = link.groupValues[2]
            return SpotifyTarget("spotify:$type:$id", "https://open.spotify.com/$type/$id")
        }
        val bare = value.filter { !it.isWhitespace() }
        if (bare.length == 22 && bare.all { it.isLetterOrDigit() }) {
            return SpotifyTarget("spotify:playlist:$bare", "https://open.spotify.com/playlist/$bare")
        }
        return null
    }

    /** Abre um URI, opcionalmente em um app específico. */
    private fun launch(context: Context, uri: String, packageName: String? = null): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (packageName != null) intent.setPackage(packageName)
        context.startActivity(intent)
        true
    } catch (error: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0) != null
    } catch (error: Exception) {
        false
    }

    /** Converte números brasileiros em E.164 (55 + DDD + número). */
    private fun normalizeBrazilianNumber(value: String): String {
        var digits = value.filter { it.isDigit() }
        if (digits.startsWith("55") && digits.length >= 12) return digits
        if (digits.startsWith("0")) digits = digits.drop(1)
        return if (digits.length == 10 || digits.length == 11) "55$digits" else digits
    }

    /** Envia uma tecla de mídia à sessão ativa (música, vídeo, etc.). */
    private fun sendMediaKey(context: Context, keyCode: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    private fun openInBrowser(context: Context, url: String, label: String): String = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        "Abri $label no navegador."
    } catch (error: Exception) {
        "Não consegui abrir o navegador."
    }

    private fun canHandle(context: Context, intent: Intent): Boolean =
        context.packageManager.resolveActivity(intent, 0) != null

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun looksLikeNumber(value: String): Boolean {
        val digits = value.count { it.isDigit() }
        return digits >= 3 && value.all { it.isDigit() || it in "+-() ." }
    }

    /** Aceita "07:30", "7h30", "0730", "7" — sempre em 24 horas. */
    private fun parseTime(value: String): Pair<Int, Int>? {
        val cleaned = value.replace('h', ':').replace('H', ':')
        val parts = cleaned.split(Regex("[:.,\\s]+")).filter { it.isNotBlank() }
        val digits = cleaned.replace(Regex("[^0-9]"), "")

        val hour: Int?
        val minute: Int
        if (parts.size >= 2) {
            hour = parts[0].filter { it.isDigit() }.toIntOrNull()
            minute = parts[1].filter { it.isDigit() }.toIntOrNull() ?: 0
        } else if (digits.length in 3..4) {
            val padded = digits.padStart(4, '0')
            hour = padded.substring(0, 2).toIntOrNull()
            minute = padded.substring(2, 4).toIntOrNull() ?: 0
        } else {
            hour = digits.toIntOrNull()
            minute = 0
        }

        if (hour == null || hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }
}
