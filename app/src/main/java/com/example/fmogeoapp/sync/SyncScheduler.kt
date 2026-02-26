package com.example.fmogeoapp.sync

import android.util.Log
import com.example.fmogeoapp.network.Coordinate
import com.example.fmogeoapp.network.FmoWebSocketService
import com.example.fmogeoapp.network.WebSocketResult
import com.example.fmogeoapp.network.ConnectionState
import com.example.fmogeoapp.service.LocationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * 同步调度器状态
 */
sealed class SyncSchedulerState {
    data object Idle : SyncSchedulerState()
    data object Running : SyncSchedulerState()
    data class LastSuccess(
        val timestamp: Long = System.currentTimeMillis(),
        val coordinate: Coordinate? = null
    ) : SyncSchedulerState()
    data class LastError(
        val timestamp: Long = System.currentTimeMillis(),
        val errorMessage: String,
        val coordinate: Coordinate? = null
    ) : SyncSchedulerState()
}

/**
 * 同步配置
 */
data class SyncConfig(
    val intervalMinutes: Int = 7,
    val enabled: Boolean = true,
    val host: String = "",
    val preciseLocationMode: Boolean = false
)

/**
 * 同步结果
 */
sealed class SyncResult {
    data class Success(val coordinate: Coordinate) : SyncResult()
    data class Error(val message: String, val coordinate: Coordinate? = null) : SyncResult()
    data class Timeout(val message: String = "同步超时") : SyncResult()
}

/**
 * 同步调度器
 *
 * 负责周期性地获取位置并发送到 FMO 服务器
 *
 * @param locationService 定位服务
 * @param webSocketService WebSocket 服务
 * @param config 同步配置
 *
 * 协程作用域设计说明：
 * - 使用独立的 coroutineScope (private val scope = CoroutineScope(...))
 * - 生命周期由 Scheduler 控制，外部无法直接控制
 * - 提供 start() / stop() 方法管理协程生命周期
 * - 内部使用 Job 保存当前同步任务引用，确保 stop() 能取消
 * - 使用 Mutex 保护状态变更，防止并发问题
 */
class SyncScheduler(
    private val locationService: LocationService,
    private val webSocketService: FmoWebSocketService,
    private val config: SyncConfig
) {
    private val TAG = "SyncScheduler"

    // 独立的协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 周期调度 Job 引用
    private var scheduleJob: Job? = null

    // 单次同步执行标志（防止重复执行）
    private var isSyncing = false

    // 同步状态互斥锁
    private val stateMutex = Mutex()

    // 内部状态（使用@Volatile保证可见性）
    @Volatile
    private var _internalState: SyncSchedulerState = SyncSchedulerState.Idle

    // 公开的状态访问器
    var internalState: SyncSchedulerState
        get() = _internalState
        set(value) {
            _internalState = value
            // 状态变更后通知监听器
            stateListeners.forEach { it(value) }
        }

    // 状态监听器列表
    private val stateListeners = mutableListOf<(SyncSchedulerState) -> Unit>()

    /**
     * 获取当前同步状态
     */
    val state: SyncSchedulerState
        get() = _internalState

    /**
     * 添加状态监听器
     */
    fun addStateListener(listener: (SyncSchedulerState) -> Unit) {
        stateListeners.add(listener)
    }

    /**
     * 移除状态监听器
     */
    fun removeStateListener(listener: (SyncSchedulerState) -> Unit) {
        stateListeners.remove(listener)
    }

    /**
     * 检查是否正在运行
     */
    val isRunning: Boolean
        get() = scheduleJob?.isActive == true

    /**
     * 获取当前配置的Host
     */
    val currentHost: String
        get() = config.host

    /**
     * 启动同步
     */
    fun start() {
        // 保存周期循环的 Job 引用，确保 stop() 能正确取消
        scheduleJob = scope.launch {
            Log.d(TAG, "启动同步调度，周期：${config.intervalMinutes}分钟")

            // 立即执行一次
            if (config.enabled) {
                performSync()
            }

            // 进入周期循环
            while (config.enabled && scheduleJob?.isActive == true) {
                try {
                    val delayMs = config.intervalMinutes * 60_000L
                    Log.d(TAG, "等待 ${config.intervalMinutes} 分钟后执行下一次同步")
                    delay(delayMs)

                    // 执行同步
                    performSync()
                } catch (e: Exception) {
                    Log.e(TAG, "同步调度异常", e)
                    // 网络异常时进入错误状态
                    updateState(
                        SyncSchedulerState.LastError(
                            timestamp = System.currentTimeMillis(),
                            errorMessage = e.message ?: "未知错误"
                        )
                    )
                }
            }

            Log.d(TAG, "同步调度已停止")
        }
    }

    /**
     * 停止同步
     */
    fun stop() {
        Log.d(TAG, "停止同步调度")

        // 取消周期调度
        scheduleJob?.cancel()
        scheduleJob = null

        // 重置为空闲状态
        updateState(SyncSchedulerState.Idle)
    }

    /**
     * 确保WebSocket已连接，必要时重连
     * @param maxAttempts 最大重试次数
     * @return 是否连接成功
     */
    private suspend fun ensureConnected(maxAttempts: Int = 3): Boolean {
        val state = webSocketService.connectionState
        if (state is ConnectionState.Connected) {
            return true
        }

        Log.w(TAG, "WebSocket 未连接，当前状态: $state")

        // 尝试重连
        repeat(maxAttempts) { attempt ->
            if (webSocketService.connectionState is ConnectionState.Connected) {
                Log.d(TAG, "WebSocket 已连接")
                return true
            }

            Log.d(TAG, "尝试连接 ${attempt + 1}/$maxAttempts 到 $currentHost")
            webSocketService.connect(currentHost)

            // 等待连接结果
            var connected = false
            try {
                withTimeout(5000L) {
                    delay(100) // 短暂等待让连接状态更新

                    var waited = 0
                    val startTime = System.currentTimeMillis()

                    while (webSocketService.connectionState !is ConnectionState.Connected && waited < 4500) {
                        delay(100)
                        waited = (System.currentTimeMillis() - startTime).toInt()
                    }

                    if (webSocketService.connectionState is ConnectionState.Connected) {
                        Log.d(TAG, "WebSocket 连接成功")
                        connected = true
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.w(TAG, "连接超时: $e")
            }

            // 如果连接成功则返回
            if (connected) {
                return true
            }

            // 如果连接后仍未就绪，继续下一次尝试
            delay(500)
        }

        Log.e(TAG, "WebSocket 连接失败，达到最大重试次数")
        return false
    }

    /**
     * 执行一次同步操作
     */
    private fun performSync() {
        // 确保只有一个同步任务在运行
        if (isSyncing) {
            Log.d(TAG, "已有同步任务在运行，跳过")
            return
        }

        // 创建新的同步任务
        scope.launch {
            isSyncing = true
            try {
                updateState(SyncSchedulerState.Running)

                Log.d(TAG, "开始执行同步")

                val location = locationService.getCurrentLocation(
                    timeout = 10000L,
                    preciseMode = config.preciseLocationMode
                )
                if (location == null) {
                    throw Exception("定位失败，请检查定位服务")
                }

                val coordinate = Coordinate(location.latitude, location.longitude)
                Log.d(TAG, "获取到位置: ${coordinate.toFormattedString()}")

                // 2. 确保WebSocket已连接
                if (!ensureConnected(maxAttempts = 2)) {
                    throw Exception("无法连接到 FMO 服务器: $currentHost")
                }

                // 3. 发送坐标到服务器
                val result = webSocketService.setCordinate(
                    latitude = coordinate.latitude,
                    longitude = coordinate.longitude,
                    timeoutMs = 10000L
                )

                // 4. 处理结果
                when (result) {
                    is WebSocketResult.Success -> {
                        if (result.data.isSuccess()) {
                            Log.d(TAG, "同步成功")
                            updateState(
                                SyncSchedulerState.LastSuccess(
                                    timestamp = System.currentTimeMillis(),
                                    coordinate = coordinate
                                )
                            )
                        } else {
                            throw Exception("服务器处理失败: result=${result.data.result}")
                        }
                    }
                    is WebSocketResult.Error -> {
                        throw Exception(result.message)
                    }
                    is WebSocketResult.Timeout -> {
                        throw Exception("同步超时")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步失败: ${e.message}", e)
                updateState(
                    SyncSchedulerState.LastError(
                        timestamp = System.currentTimeMillis(),
                        errorMessage = e.message ?: "同步失败",
                        coordinate = getLastCoordinate()
                    )
                )
            } finally {
                isSyncing = false
            }
        }
    }

    /**
     * 更新状态
     */
    private fun updateState(newState: SyncSchedulerState) {
        internalState = newState
    }

    /**
     * 获取最后一次成功的坐标
     */
    private fun getLastCoordinate(): Coordinate? {
        val currentState = internalState
        return when (currentState) {
            is SyncSchedulerState.LastSuccess -> currentState.coordinate
            is SyncSchedulerState.LastError -> currentState.coordinate
            else -> null
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        stop()
        scope.cancel()
        stateListeners.clear()
        Log.d(TAG, "SyncScheduler 已清理")
    }
}
