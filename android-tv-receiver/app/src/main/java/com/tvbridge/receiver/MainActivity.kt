package com.tvbridge.receiver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tvbridge.receiver.ui.TvBridgeApp

/**
 * Actividad principal de TV-Bridge Receiver para Android TV.
 * Lanza la interfaz Compose for TV y gestiona el ciclo de vida del receptor WebRTC.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TvBridgeApp()
        }
    }
}
