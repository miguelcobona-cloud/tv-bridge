package com.tvbridge.receiver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvbridge.receiver.R
import com.tvbridge.receiver.ui.theme.TvBridgeBackground
import org.webrtc.SurfaceViewRenderer

/**
 * Pantalla de reproducción a pantalla completa con [SurfaceViewRenderer] de WebRTC.
 * El video P2P se renderiza en cuanto se negocia la conexión con el emisor web.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaybackScreen(
    statusMessage: String,
    onRendererBind: (SurfaceViewRenderer) -> Unit,
    onRendererUnbind: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            renderer?.let(onRendererUnbind)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvBridgeBackground),
    ) {
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).also { view ->
                    renderer = view
                    onRendererBind(view)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                if (renderer !== view) {
                    renderer?.let(onRendererUnbind)
                    renderer = view
                    onRendererBind(view)
                }
            },
        )

        Text(
            text = statusMessage.ifBlank { stringResource(R.string.streaming_title) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp),
        )
    }
}
