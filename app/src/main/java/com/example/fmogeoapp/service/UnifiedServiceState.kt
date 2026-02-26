package com.example.fmogeoapp.service

import com.example.fmogeoapp.network.ConnectionState

/**
 * 统一服务状态（包含所有需要的状态信息）
 *
 * 这个类将所有服务状态统一到一个 StateFlow 中，供 ViewModel 订阅
 */
sealed class UnifiedServiceState {
    /**
     * 空闲状态
     */
    data object Idle : UnifiedServiceState()

    /**
     * 运行中
     */
    data object Running : UnifiedServiceState()

    /**
     * 同步成功
     */
    data class Success(
        val timestamp: Long = System.currentTimeMillis(),
        val coordinate: com.example.fmogeoapp.network.Coordinate? = null
    ) : UnifiedServiceState()

    /**
     * 同步失败
     */
    data class Error(
        val timestamp: Long = System.currentTimeMillis(),
        val message: String = ""
    ) : UnifiedServiceState()

    /**
     * WebSocket 连接状态
     */
    data class ConnectionState(
        val state: WebSocketConnectionState
    ) : UnifiedServiceState()

    /**
     * 发现状态
     */
    data class DiscoveryState(
        val state: DiscoveryService.DiscoveryState
    ) : UnifiedServiceState()
}

/**
 * WebSocket 连接状态
 *
 * 注意：这与 network.ConnectionState 类似，但专门用于 UnifiedServiceState
 * 目的是保持状态统一，避免依赖不同包的类
 */
sealed class WebSocketConnectionState {
    data object Disconnected : WebSocketConnectionState()
    data object Connecting : WebSocketConnectionState()
    data object Connected : WebSocketConnectionState()
    data class Error(val message: String) : WebSocketConnectionState()

    /**
     * 判断是否已连接
     */
    fun isConnected(): Boolean = this is Connected
}

/**
 * 将 ConnectionState 转换为 WebSocketConnectionState
 */
fun ConnectionState.toWebSocketConnectionState(): WebSocketConnectionState = when (this) {
    is com.example.fmogeoapp.network.ConnectionState.Disconnected -> WebSocketConnectionState.Disconnected
    is com.example.fmogeoapp.network.ConnectionState.Connecting -> WebSocketConnectionState.Connecting
    is com.example.fmogeoapp.network.ConnectionState.Connected -> WebSocketConnectionState.Connected
    is com.example.fmogeoapp.network.ConnectionState.Error -> WebSocketConnectionState.Error(this.message)
}

/**
 * 将 WebSocketConnectionState 转换为 ConnectionState
 */
fun WebSocketConnectionState.toConnectionState(): ConnectionState = when (this) {
    is WebSocketConnectionState.Disconnected -> com.example.fmogeoapp.network.ConnectionState.Disconnected
    is WebSocketConnectionState.Connecting -> com.example.fmogeoapp.network.ConnectionState.Connecting
    is WebSocketConnectionState.Connected -> com.example.fmogeoapp.network.ConnectionState.Connected
    is WebSocketConnectionState.Error -> com.example.fmogeoapp.network.ConnectionState.Error(this.message)
}
