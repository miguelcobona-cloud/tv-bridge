package com.tvbridge.receiver.ui

/**
 * Estado de la aplicación TV-Bridge Receiver.
 */
sealed interface AppScreen {
    data object Setup : AppScreen
    data object Waiting : AppScreen
    data object Streaming : AppScreen
}

enum class ConnectionStatus {
    Idle,
    Connecting,
    Connected,
    Streaming,
    Error,
    Disconnected,
}

data class ReceiverUiState(
    val screen: AppScreen = AppScreen.Setup,
    val serverIp: String = "",
    val tvName: String = "",
    val tvId: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Idle,
    val statusMessage: String = "",
    val errorMessage: String? = null,
)
