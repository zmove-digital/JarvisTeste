package com.example.jarvisteste.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.jarvisteste.audio.VoiceFeatures
import com.example.jarvisteste.audio.VoiceprintRecorder
import com.example.jarvisteste.data.SettingsStore
import com.example.jarvisteste.data.VoiceprintStore
import com.example.jarvisteste.service.JarvisListeningService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cadastro local da voz do usuário. As amostras viram MFCC ([VoiceFeatures]),
 * ficam cifradas no Keystore ([VoiceprintStore]) e nunca saem do aparelho.
 */
@Composable
fun VoiceLockPanel(settings: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { VoiceprintStore(context) }

    var enrolled by remember { mutableStateOf(store.hasEnrollment) }
    var enabled by remember { mutableStateOf(settings.voiceLockEnabled) }
    var strictness by remember { mutableIntStateOf(settings.voiceLockStrictness) }
    var enrolling by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }
    var message by remember {
        mutableStateOf(
            if (store.hasEnrollment) {
                "Sua voz está cadastrada neste aparelho."
            } else {
                "Cadastre sua voz para o Jarvis atender só a você."
            }
        )
    }

    fun restartIfRunning() {
        if (JarvisListeningService.running.value) JarvisListeningService.restart(context)
    }

    suspend fun enrollVoice() {
        val restartAfter = JarvisListeningService.running.value
        if (restartAfter) JarvisListeningService.stop(context)
        enrolling = true
        step = 0
        val samples = mutableListOf<FloatArray>()
        val recorder = VoiceprintRecorder(context)
        try {
            for (index in 0 until SAMPLE_COUNT) {
                step = index + 1
                message = "Diga apenas \"${settings.wakeWord}\" agora..."
                delay(650)
                val pcm = withContext(Dispatchers.IO) { recorder.record(1_800L) }
                val features = pcm?.let {
                    withContext(Dispatchers.Default) { VoiceFeatures.extract(it) }
                }
                if (features == null) {
                    message = "Não consegui ouvir direito. Toque em cadastrar e tente de novo."
                    return
                }
                samples.add(features)
                message = "Amostra ${index + 1} de $SAMPLE_COUNT capturada."
                delay(450)
            }
            store.save(samples)
            settings.voiceLockEnabled = true
            enrolled = true
            enabled = true
            message = "Pronto. Agora só atendo à sua voz."
            Log.i(TAG, "Voz cadastrada com ${samples.size} amostras")
        } finally {
            enrolling = false
            step = 0
            if (restartAfter) JarvisListeningService.start(context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch { enrollVoice() }
        } else {
            message = "Permissão do microfone negada."
        }
    }

    fun requestEnrollment() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            scope.launch { enrollVoice() }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    HudPanel(label = "Voz autorizada", modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Somente minha voz",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = message,
                        color = if (enrolled) JarvisColors.Success else JarvisColors.TextMuted,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = enabled,
                    enabled = enrolled && !enrolling,
                    onCheckedChange = { value ->
                        enabled = value
                        settings.voiceLockEnabled = value
                        restartIfRunning()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = JarvisColors.DangerStrong,
                        checkedTrackColor = JarvisColors.DangerSurface,
                        uncheckedThumbColor = JarvisColors.TextSecondary,
                        uncheckedTrackColor = JarvisColors.SurfaceAlt,
                        uncheckedBorderColor = JarvisColors.Border,
                        disabledUncheckedThumbColor = JarvisColors.Border,
                        disabledUncheckedTrackColor = JarvisColors.SurfaceAlt
                    )
                )
            }

            if (enrolled) {
                Spacer(modifier = Modifier.height(16.dp))
                val rigor = when {
                    strictness < 35 -> "baixo"
                    strictness < 70 -> "médio"
                    else -> "alto"
                }
                Text(
                    text = "Rigor da verificação: $rigor",
                    color = JarvisColors.TextSecondary,
                    fontSize = 11.sp
                )
                Slider(
                    value = strictness / 100f,
                    onValueChange = { value ->
                        settings.voiceLockStrictness = (value * 100).toInt()
                        strictness = settings.voiceLockStrictness
                    },
                    valueRange = 0f..1f,
                    enabled = !enrolling,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Se ele não reconhecer sua voz, reduza o rigor.",
                    color = JarvisColors.TextMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            JarvisPrimaryButton(
                text = if (enrolling) {
                    "GRAVANDO $step DE $SAMPLE_COUNT..."
                } else {
                    "CADASTRAR MINHA VOZ"
                },
                enabled = !enrolling,
                onClick = { requestEnrollment() }
            )
            Spacer(modifier = Modifier.height(6.dp))
            JarvisGhostButton(
                text = "APAGAR CADASTRO",
                enabled = enrolled && !enrolling,
                onClick = {
                    store.clear()
                    settings.voiceLockEnabled = false
                    enrolled = false
                    enabled = false
                    message = "Cadastro apagado."
                    restartIfRunning()
                }
            )
        }
    }
}

private const val SAMPLE_COUNT = 5
private const val TAG = "VoiceLock"
