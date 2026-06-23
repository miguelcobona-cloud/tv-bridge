package com.tvbridge.receiver.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvbridge.receiver.R
import com.tvbridge.receiver.ui.theme.TvBridgeError
import com.tvbridge.receiver.ui.components.DpadTextField

/**
 * Pantalla de configuración inicial: IP del servidor y nombre de la TV.
 * El foco D-Pad se encadena IP → nombre → botón conecta.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SetupScreen(
    serverIp: String,
    tvName: String,
    errorMessage: String?,
    onServerIpChange: (String) -> Unit,
    onTvNameChange: (String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ipFocus = remember { FocusRequester() }
    val nameFocus = remember { FocusRequester() }
    val connectFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        ipFocus.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 72.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )

            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.server_ip_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                DpadTextField(
                    value = serverIp,
                    onValueChange = onServerIpChange,
                    placeholder = stringResource(R.string.server_ip_hint),
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                    onImeAction = { nameFocus.requestFocus() },
                    modifier = Modifier
                        .focusRequester(ipFocus)
                        .focusProperties { next = nameFocus },
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.tv_name_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                DpadTextField(
                    value = tvName,
                    onValueChange = onTvNameChange,
                    placeholder = stringResource(R.string.tv_name_hint),
                    imeAction = ImeAction.Done,
                    onImeAction = { connectFocus.requestFocus() },
                    modifier = Modifier
                        .focusRequester(nameFocus)
                        .focusProperties {
                            previous = ipFocus
                            next = connectFocus
                        },
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvBridgeError,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(connectFocus)
                    .focusProperties { previous = nameFocus },
            ) {
                Text(text = stringResource(R.string.connect_button))
            }
        }
    }
}
