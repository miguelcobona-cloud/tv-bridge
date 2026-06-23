package com.tvbridge.sender.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvbridge.sender.SenderViewModel
import com.tvbridge.sender.ui.screens.SetupScreen
import com.tvbridge.sender.ui.screens.StreamingScreen
import com.tvbridge.sender.ui.screens.TvListScreen
import com.tvbridge.sender.ui.theme.TvBridgeSenderTheme

@Composable
fun SenderApp(
    viewModel: SenderViewModel = viewModel(),
    onScanQr: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TvBridgeSenderTheme {
        when (uiState.screen) {
            AppScreen.Setup -> {
                SetupScreen(
                    serverIp = uiState.serverIp,
                    errorMessage = uiState.errorMessage,
                    onServerIpChange = viewModel::onServerIpChange,
                    onConnect = viewModel::connect,
                    onScanQr = onScanQr,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AppScreen.TvList -> {
                TvListScreen(
                    statusMessage = uiState.statusMessage,
                    errorMessage = uiState.errorMessage,
                    tvs = uiState.tvs,
                    shareSystemAudio = uiState.shareSystemAudio,
                    onShareSystemAudioChange = viewModel::onShareSystemAudioChange,
                    onTvSelected = viewModel::onTvSelected,
                    onChangeServer = viewModel::openSetup,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AppScreen.Streaming -> {
                StreamingScreen(
                    tvName = uiState.activeTvName.orEmpty(),
                    statusMessage = uiState.statusMessage,
                    onStop = viewModel::stopStreaming,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
