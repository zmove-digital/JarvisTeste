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
import kotlinx.coroutines.delay
import java.net.URLEncoder

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
        webSearch(context),
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

    // ---------------------------------------------------------- pesquisar web

    private fun webSearch(context: Context): ToolSpec = ToolSpec(
        name = "pesquisar_web",
        description = "Abre o navegador com uma busca na internet. Use quando o usuário pedir " +
            "para pesquisar, procurar, ou ver algo na web. Não use para perguntas que você " +
            "mesmo sabe responder.",
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
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
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
