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
import com.example.jarvisteste.audio.VoiceManager
import com.example.jarvisteste.data.SettingsStore
import com.example.jarvisteste.ui.HomeScreen
import com.example.jarvisteste.ui.OnboardingScreen
import com.example.jarvisteste.ui.SettingsScreen
import com.example.jarvisteste.ui.theme.JarvisTesteTheme

class MainActivity : ComponentActivity() {

    private lateinit var settings: SettingsStore
    private lateinit var voice: VoiceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settings = SettingsStore(this)
        voice = VoiceManager(this) { settings.apiKey }

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
                        voice = voice,
                        onFinished = { screen = Screen.HOME.name }
                    )

                    Screen.HOME -> HomeScreen(
                        settings = settings,
                        voice = voice,
                        onOpenSettings = {
                            voice.stop()
                            screen = Screen.SETTINGS.name
                        }
                    )

                    Screen.SETTINGS -> {
                        BackHandler {
                            voice.stop()
                            screen = Screen.HOME.name
                        }
                        SettingsScreen(
                            settings = settings,
                            voice = voice,
                            onBack = {
                                voice.stop()
                                screen = Screen.HOME.name
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        voice.shutdown()
        super.onDestroy()
    }

    private enum class Screen { ONBOARDING, HOME, SETTINGS }
}
