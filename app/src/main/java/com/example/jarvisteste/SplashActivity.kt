package com.example.jarvisteste

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.jarvisteste.ui.SplashScreen
import com.example.jarvisteste.ui.theme.JarvisTesteTheme

/** Primeira tela: mostra o ícone e os anéis, depois abre o app. */
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            JarvisTesteTheme {
                SplashScreen(
                    onDone = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}
