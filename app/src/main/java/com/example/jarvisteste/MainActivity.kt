package com.example.jarvisteste

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.jarvisteste.audio.TtsManager
import com.example.jarvisteste.data.SettingsStore
import com.example.jarvisteste.ui.HomeScreen
import com.example.jarvisteste.ui.OnboardingScreen
import com.example.jarvisteste.ui.SettingsScreen
import com.example.jarvisteste.ui.theme.JarvisTesteTheme

class MainActivity : ComponentActivity() {

    private lateinit var settings: SettingsStore
    private lateinit var tts: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settings = SettingsStore(this)
        tts = TtsManager(this).also { it.init() }

        setContent {
            JarvisTesteTheme {
                var screen by rememberSaveable {
                    mutableStateOf(
                        if (settings.onboarded) Screen.HOME.name else Screen.ONBOARDING.name
                    )
                }

                when (Screen.valueOf(screen)) {
                    Screen.ONBOARDING -> OnboardingScreen(
                        settings = settings,
                        tts = tts,
                        onFinished = { screen = Screen.HOME.name }
                    )

                    Screen.HOME -> HomeScreen(
                        settings = settings,
                        tts = tts,
                        onOpenSettings = {
                            tts.stop()
                            screen = Screen.SETTINGS.name
                        }
                    )

                    Screen.SETTINGS -> {
                        BackHandler {
                            tts.stop()
                            screen = Screen.HOME.name
                        }
                        SettingsScreen(
                            settings = settings,
                            tts = tts,
                            onBack = {
                                tts.stop()
                                screen = Screen.HOME.name
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }

    private enum class Screen { ONBOARDING, HOME, SETTINGS }
}
