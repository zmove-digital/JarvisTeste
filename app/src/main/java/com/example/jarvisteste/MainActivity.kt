
package com.example.jarvisteste

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.jarvisteste.ui.theme.JarvisTesteTheme
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        textToSpeech = TextToSpeech(this) { result ->

            if (result == TextToSpeech.SUCCESS) {

                val languageResult = textToSpeech?.setLanguage(
                    Locale("pt", "BR")
                )

                ttsReady =
                    languageResult != TextToSpeech.LANG_MISSING_DATA &&
                            languageResult != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }

        setContent {
            JarvisTesteTheme {
                JarvisHomeScreen(

                    onSpeak = { message ->
                        if (ttsReady) {
                            textToSpeech?.speak(
                                message,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "jarvis_response"
                            )
                        }
                    },

                    onAsk = { message, onResult ->

                        Thread {

                            var connection: HttpURLConnection? = null

                            try {

                                val url = URL(
                                    "http://192.168.100.54:8000/conversar"
                                )

                                connection =
                                    url.openConnection() as HttpURLConnection

                                connection.requestMethod = "POST"
                                connection.connectTimeout = 15000
                                connection.readTimeout = 60000
                                connection.doOutput = true

                                connection.setRequestProperty(
                                    "Content-Type",
                                    "application/json; charset=UTF-8"
                                )

                                val body = JSONObject()
                                    .put("mensagem", message)
                                    .toString()

                                connection.outputStream.use { output ->
                                    output.write(
                                        body.toByteArray(Charsets.UTF_8)
                                    )
                                }

                                val code = connection.responseCode

                                val stream = if (code in 200..299) {
                                    connection.inputStream
                                } else {
                                    connection.errorStream
                                }

                                val responseText = stream
                                    ?.bufferedReader(Charsets.UTF_8)
                                    ?.use { it.readText() }
                                    ?: throw Exception("Resposta vazia do servidor.")

                                if (code !in 200..299) {
                                    throw Exception(
                                        "Erro HTTP $code: $responseText"
                                    )
                                }

                                val answer = JSONObject(responseText)
                                    .getString("resposta")

                                if (answer.isBlank()) {
                                    throw Exception(
                                        "Resposta vazia do Gemini."
                                    )
                                }

                                Handler(Looper.getMainLooper()).post {
                                    onResult(Result.success(answer))
                                }

                            } catch (e: Exception) {

                                Handler(Looper.getMainLooper()).post {
                                    onResult(Result.failure(e))
                                }

                            } finally {
                                connection?.disconnect()
                            }

                        }.start()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }
}

@Composable
fun JarvisHomeScreen(
    onSpeak: (String) -> Unit = {},

    onAsk: (String, (Result<String>) -> Unit) -> Unit =
        { _, callback ->
            callback(
                Result.failure(
                    Exception("Servidor indisponível")
                )
            )
        }
) {

    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    var status by remember {
        mutableStateOf("Aguardando seu comando...")
    }

    var recognizedText by remember {
        mutableStateOf("")
    }

    var isListening by remember {
        mutableStateOf(false)
    }

    val recognizer = remember(context, isPreview) {

        if (
            isPreview ||
            !SpeechRecognizer.isRecognitionAvailable(context)
        ) {
            null
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    val speechIntent = remember {

        Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "pt-BR"
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "pt-BR"
            )

            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
            )

            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                1
            )
        }
    }

    DisposableEffect(recognizer) {

        if (recognizer != null) {

            recognizer.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        status = "Estou ouvindo..."
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {
                        status = "Pode falar, estou ouvindo."
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        status = "Processando sua fala..."
                        isListening = false
                    }

                    override fun onError(error: Int) {

                        isListening = false

                        status = when (error) {

                            SpeechRecognizer.ERROR_AUDIO ->
                                "Erro no áudio."

                            SpeechRecognizer.ERROR_CLIENT ->
                                "Erro no reconhecimento."

                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Permissão do microfone não concedida."

                            SpeechRecognizer.ERROR_NETWORK ->
                                "Erro de conexão."

                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Tempo de conexão esgotado."

                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "Não consegui entender. Tente novamente."

                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                "Reconhecimento ocupado. Tente novamente."

                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "Não detectei sua voz."

                            else ->
                                "Erro ao reconhecer a voz: $error"
                        }
                    }

                    override fun onResults(results: Bundle?) {

                        isListening = false

                        val matches = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val message = matches?.firstOrNull()?.trim()

                        if (!message.isNullOrEmpty()) {

                            recognizedText = message
                            status = "Consultando o Gemini..."

                            onAsk(message) { result ->

                                result.onSuccess { answer ->

                                    status = answer
                                    onSpeak(answer)
                                }

                                result.onFailure { error ->

                                    status =
                                        "Erro ao consultar Jarvis: " +
                                                (error.message ?: "Falha desconhecida")
                                }
                            }

                        } else {

                            status =
                                "Não consegui entender. Tente novamente."
                        }
                    }

                    override fun onPartialResults(
                        partialResults: Bundle?
                    ) {

                        val matches = partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        if (!matches.isNullOrEmpty()) {
                            recognizedText = matches[0]
                        }
                    }

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {}
                }
            )
        }

        onDispose {
            recognizer?.destroy()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->

        if (granted) {

            if (recognizer != null) {

                try {
                    recognizedText = ""
                    status = "Iniciando microfone..."
                    recognizer.startListening(speechIntent)

                } catch (e: Exception) {

                    isListening = false
                    status = "Não foi possível iniciar o microfone."
                }

            } else {

                status =
                    "Reconhecimento de voz indisponível neste aparelho."
            }

        } else {

            status = "Permissão do microfone negada."
        }
    }

    fun startMicrophone() {

        if (recognizer == null) {
            status = "Reconhecimento de voz indisponível."
            return
        }

        val permission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )

        if (permission == PackageManager.PERMISSION_GRANTED) {

            try {
                recognizedText = ""
                status = "Iniciando microfone..."
                recognizer.startListening(speechIntent)

            } catch (e: Exception) {

                isListening = false
                status = "Erro ao iniciar o microfone."
            }

        } else {

            permissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    val background = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF101C2B),
            Color(0xFF0A111C),
            Color(0xFF080D15)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 24.dp,
                    vertical = 32.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // CABECALHO

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "J.A.R.V.I.S.",
                    color = Color(0xFFB8E7FF),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF153B32)
                ) {

                    Text(
                        text = "  ONLINE  ",
                        modifier = Modifier.padding(vertical = 7.dp),
                        color = Color(0xFF75E6AE),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(65.dp))

            // IDENTIDADE VISUAL

            Box(
                modifier = Modifier
                    .size(190.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF245B82),
                                Color(0xFF152D47),
                                Color(0xFF101C2B)
                            )
                        ),
                        shape = CircleShape
                    )
                    .border(
                        1.dp,
                        Color(0xFF438BB8),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {

                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .background(
                            Color(0xFF183D5B),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            Color(0xFF66B9E8),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = "J",
                        color = Color(0xFFB8E7FF),
                        fontSize = 76.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }

            Spacer(modifier = Modifier.height(38.dp))

            Text(
                text = "Olá, estou aqui.",
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Como posso ajudar?",
                color = Color(0xFFA8B9CC),
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(38.dp))

            // AREA DE CONVERSACAO

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF172536),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color(0xFF263D53)
                )
            ) {

                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Text(
                        text = "ASSISTENTE",
                        color = Color(0xFF70BDEB),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = status,
                        color = Color.White,
                        fontSize = 16.sp
                    )

                    if (recognizedText.isNotEmpty()) {

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "VOCÊ DISSE:",
                            color = Color(0xFF70BDEB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = recognizedText,
                            color = Color.White,
                            fontSize = 17.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(42.dp))

            // MICROFONE FUNCIONAL

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(
                        if (isListening) {
                            Color(0xFFB83232)
                        } else {
                            Color(0xFF17699A)
                        },
                        CircleShape
                    )
                    .border(
                        1.dp,
                        Color(0xFF61B9EA),
                        CircleShape
                    )
                    .clickable {

                        if (isListening) {

                            recognizer?.stopListening()
                            isListening = false
                            status = "Escuta interrompida."

                        } else {

                            startMicrophone()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = if (isListening) "..." else "MIC",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(15.dp))

            Text(
                text = "CONTROLE DE VOZ",
                color = Color(0xFFB8D9EF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isListening) {
                    "JARVIS está ouvindo você."
                } else {
                    "Toque na bolinha para falar com JARVIS."
                },
                color = Color(0xFF8194A9),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(45.dp))

            HorizontalDivider(
                color = Color(0xFF263747)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SEU ASSISTENTE. SUAS REGRAS.",
                color = Color(0xFF718DA5),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(15.dp))

            Text(
                text = "JARVIS  •  PERSONALIZÁVEL",
                color = Color(0xFF8BBFE0),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun JarvisPreview() {

    JarvisTesteTheme {
        JarvisHomeScreen()
    }
}
