package com.tvbridge.receiver.ui

import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Evita el salvapantallas de Android TV mientras hay sesión activa o video entrante.
 * Combina FLAG_KEEP_SCREEN_ON con WakeLock mientras [active] es true.
 */
@Composable
fun KeepScreenOn(active: Boolean) {
    val context = LocalContext.current

    DisposableEffect(active) {
        if (!active) {
            return@DisposableEffect onDispose { }
        }

        val activity = context as? ComponentActivity
        val window = activity?.window
        val powerManager = context.getSystemService(PowerManager::class.java)
        @Suppress("DEPRECATION")
        val wakeLockFlag = PowerManager.SCREEN_BRIGHT_WAKE_LOCK
        val wakeLock = powerManager?.newWakeLock(
            wakeLockFlag,
            "tvbridge:streaming",
        )

        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.decorView?.keepScreenOn = true
        wakeLock?.acquire()

        onDispose {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
            window?.decorView?.keepScreenOn = false
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
