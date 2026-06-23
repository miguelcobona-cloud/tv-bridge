package com.tvbridge.sender.ui

enum class AppScreen {
    Setup,
    TvList,
    Streaming,
}

enum class ConnectionStatus {
    Idle,
    Connecting,
    Connected,
    Streaming,
    Disconnected,
    Error,
}

data class SenderUiState(
    val screen: AppScreen = AppScreen.Setup,
    val serverIp: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.Idle,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val tvs: List<TvItemUi> = emptyList(),
    val activeTvName: String? = null,
    val shareSystemAudio: Boolean = true,
)

data class ProjectionRequest(
    val tv: TvItemUi,
    val shareSystemAudio: Boolean,
)

data class TvItemUi(
    val id: String,
    val name: String,
)
