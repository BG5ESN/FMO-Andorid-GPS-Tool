package com.example.fmogeoapp.service

import com.example.fmogeoapp.network.Coordinate
import com.example.fmogeoapp.network.ConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * 同步服务 Binder 接口
 *
 * 定义前台服务与 ViewModel 之间的通信接口
 */
interface SyncServiceBinder {
    /**
     * 统一状态流（用于观察）
     * 包含：同步状态、WebSocket 连接状态、发现状态等
     */
    val stateFlow: StateFlow<UnifiedServiceState>

    /**
     * 是否正在运行
     */
    val isRunning: Boolean

    /**
     * 当前坐标
     */
    val currentCoordinate: Coordinate?

    /**
     * 上次同步时间
     */
    val lastSyncTime: Long

    /**
     * WebSocket 连接状态
     */
    val connectionState: ConnectionState

    /**
     * 发现状态
     */
    val discoveryState: DiscoveryService.DiscoveryState

    /**
     * 启动同步
     */
    fun startSync(host: String, intervalMinutes: Int)

    fun startSync(host: String, intervalMinutes: Int, preciseLocationMode: Boolean)

    /**
     * 停止同步
     */
    fun stopSync()

    /**
     * 测试定位并发送（不启动周期同步）
     */
    fun testLocationAndSend(host: String)

    fun testLocationAndSend(host: String, preciseMode: Boolean)

    /**
     * 开始发现设备
     */
    fun startDiscovery()

    /**
     * 取消发现
     */
    fun cancelDiscovery()
}
