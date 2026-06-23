package com.tvbridge.sender.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tvbridge.sender.R

@Composable
fun SetupScreen(
    serverIp: String,
    errorMessage: String?,
    onServerIpChange: (String) -> Unit,
    onConnect: () -> Unit,
    onScanQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = serverIp,
            onValueChange = onServerIpChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.server_ip_label)) },
            placeholder = { Text(stringResource(R.string.server_ip_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.connect_button))
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onScanQr,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.scan_qr_button))
        }
    }
}
