package com.tvbridge.receiver.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvbridge.receiver.R
import com.tvbridge.receiver.ui.theme.TvBridgeError
import com.tvbridge.receiver.util.QrCodeGenerator

/**
 * Pantalla de espera mientras la TV está registrada en el servidor de señalización.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WaitingScreen(
    serverIp: String,
    tvName: String,
    tvId: String?,
    statusMessage: String,
    errorMessage: String?,
    onEditConfiguration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editFocus = remember { FocusRequester() }
    val frontendUrl = remember(serverIp) { QrCodeGenerator.buildPhoneConnectUrl(serverIp) }
    val qrBitmap = remember(frontendUrl) {
        runCatching { QrCodeGenerator.encodeToImageBitmap(frontendUrl) }.getOrNull()
    }

    LaunchedEffect(Unit) {
        editFocus.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 72.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.widthIn(max = 1200.dp),
            horizontalArrangement = Arrangement.spacedBy(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .background(Color.White)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = stringResource(R.string.waiting_qr_content_description),
                        modifier = Modifier.size(320.dp),
                        filterQuality = FilterQuality.None,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.waiting_title),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.waiting_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (serverIp.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.waiting_qr_instruction, serverIp),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Start,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = tvName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                if (tvId != null) {
                    Text(
                        text = "ID: $tvId",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvBridgeError,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onEditConfiguration,
                    modifier = Modifier.focusRequester(editFocus),
                ) {
                    Text(text = stringResource(R.string.edit_config_button))
                }
            }
        }
    }
}
