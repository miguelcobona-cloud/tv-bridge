package com.tvbridge.receiver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.tvbridge.receiver.ReceiverViewModel
import com.tvbridge.receiver.ui.screens.PlaybackScreen
import com.tvbridge.receiver.ui.screens.SetupScreen
import com.tvbridge.receiver.ui.screens.WaitingScreen
import com.tvbridge.receiver.ui.theme.TvBridgeBackground
import com.tvbridge.receiver.ui.theme.TvBridgeTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvBridgeApp(
    viewModel: ReceiverViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Mantener pantalla encendida: esperando señal o reproduciendo video WebRTC
    val keepScreenOn = uiState.screen != AppScreen.Setup &&
        uiState.connectionStatus != ConnectionStatus.Disconnected &&
        uiState.connectionStatus != ConnectionStatus.Error

    KeepScreenOn(active = keepScreenOn)

    TvBridgeTheme {
        when (uiState.screen) {
            AppScreen.Setup -> {
                SetupScreen(
                    serverIp = uiState.serverIp,
                    tvName = uiState.tvName,
                    errorMessage = uiState.errorMessage,
                    onServerIpChange = viewModel::onServerIpChange,
                    onTvNameChange = viewModel::onTvNameChange,
                    onConnect = viewModel::saveAndConnect,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TvBridgeBackground),
                )
            }

            AppScreen.Waiting -> {
                WaitingScreen(
                    serverIp = uiState.serverIp,
                    tvName = uiState.tvName,
                    tvId = uiState.tvId,
                    statusMessage = uiState.statusMessage,
                    errorMessage = uiState.errorMessage,
                    onEditConfiguration = viewModel::openSetup,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TvBridgeBackground),
                )
            }

            AppScreen.Streaming -> {
                PlaybackScreen(
                    statusMessage = uiState.statusMessage,
                    onRendererBind = viewModel::bindVideoRenderer,
                    onRendererUnbind = viewModel::unbindVideoRenderer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
