package com.example.fmogeoapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.fmogeoapp.data.model.AppSettings
import com.example.fmogeoapp.network.Coordinate
import com.example.fmogeoapp.network.FmoWebSocketService
import com.example.fmogeoapp.network.WebSocketResult
import com.example.fmogeoapp.network.ConnectionState
import com.example.fmogeoapp.notification.NotificationHelper
import com.example.fmogeoapp.sync.SyncConfig
import com.example.fmogeoapp.sync.SyncScheduler
import com.example.fmogeoapp.sync.SyncSchedulerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 同步前台服务
 *
 * 负责在后台持续执行定位同步任务，保持常驻通知
 *
 * 生命周期与异常恢复策略：
 * 1. 服务启动时立即显示前台通知并启动同步调度器
 * 2. 服务停止时停止调度器并取消前台通知
 * 3. 网络异常时 SyncScheduler 会自动重试，服务继续运行
 * 4. 系统杀死服务后，如果用户希望继续同步，需要重新启动
 * 5. 服务被系统杀死不会导致数据丢失，但需要用户重新启动
 */
class SyncForegroundService : Service() {

    companion object {
        private const val TAG = "SyncForegroundService"

        /**
         * Action: 启动同步
         */
        const val ACTION_START_SYNC = "com.example.fmogeoapp.ACTION_START_SYNC"

        /**
         * Action: 停止同步
         */
        const val ACTION_STOP_SYNC = "com.example.fmogeoapp.ACTION_STOP_SYNC"

        /**
         * Extra: Host
         */
        const val EXTRA_HOST = "extra_host"

        /**
         * Extra: 同步间隔（分钟）
         */
        const val EXTRA_SYNC_INTERVAL = "extra_sync_interval"

        const val EXTRA_PRECISE_LOCATION_MODE = "extra_precise_location_mode"

        // SharedPreferences 配置
        private const val PREFS_NAME = "sync_service_prefs"
        private const val KEY_SHOULD_KEEP_RUNNING = "should_keep_running"
        private const val KEY_HOST = "host"
        private const val KEY_SYNC_INTERVAL = "sync_interval"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_LAST_LATITUDE = "last_latitude"
        private const val KEY_LAST_LONGITUDE = "last_longitude"
        private const val KEY_STATUS_MESSAGE = "status_message"
    }

    // Binder 用于客户端绑定
    private val binder = LocalBinder()

    // 定位服务
    private lateinit var locationService: LocationService

    // WebSocket 服务
    private val webSocketService = FmoWebSocketService()

    // 同步调度器
    private var syncScheduler: SyncScheduler? = null

    // 通知助手
    private lateinit var notificationHelper: NotificationHelper

    // SharedPreferences 用于持久化配置
    private lateinit var sharedPrefs: SharedPreferences

    // 发现服务
    private lateinit var discoveryService: DiscoveryService

    // 当前配置
    private var currentConfig: SyncConfig? = null

    // 服务是否应该运行（用于 START_STICKY 重启时恢复状态）
    private var shouldKeepRunning = false

    // 协程作用域（用于 testLocationAndSend 等异步操作）
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 统一状态流（用于暴露给 ViewModel）
    private val _unifiedState = MutableStateFlow<UnifiedServiceState>(UnifiedServiceState.Idle)
    val unifiedState: StateFlow<UnifiedServiceState> = _unifiedState.asStateFlow()

    // 当前 Host
    private var currentHost: String = ""

    // 当前同步间隔
    private var currentSyncInterval: Int = AppSettings.DEFAULT_SYNC_INTERVAL

    // 上次同步时间
    private var lastSyncTime: Long = 0

    // 当前状态消息
    private var statusMessage: String = ""

    // 当前坐标
    private var currentCoordinate: Coordinate? = null

    // WebSocket 连接状态缓存
    private var wsConnectionState: WebSocketConnectionState = WebSocketConnectionState.Disconnected

    // 发现状态缓存
    private var discoveryStateCache: DiscoveryService.DiscoveryState = DiscoveryService.DiscoveryState.Idle

    /**
     * 本地 Binder 类
     */
    inner class LocalBinder : Binder(), SyncServiceBinder {
        override val stateFlow: StateFlow<UnifiedServiceState>
            get() = this@SyncForegroundService.unifiedState

        override val isRunning: Boolean
            get() = syncScheduler?.isRunning == true

        override val currentCoordinate: Coordinate?
            get() = this@SyncForegroundService.currentCoordinate

        override val lastSyncTime: Long
            get() = this@SyncForegroundService.lastSyncTime

        override val connectionState: ConnectionState
            get() = wsConnectionState.toConnectionState()

        override val discoveryState: DiscoveryService.DiscoveryState
            get() = this@SyncForegroundService.discoveryStateCache

        override fun startSync(host: String, intervalMinutes: Int) {
            this@SyncForegroundService.startSync(host, intervalMinutes)
        }

        override fun startSync(host: String, intervalMinutes: Int, preciseLocationMode: Boolean) {
            this@SyncForegroundService.startSync(host, intervalMinutes, preciseLocationMode)
        }

        override fun stopSync() {
            this@SyncForegroundService.stopSync()
        }

        override fun testLocationAndSend(host: String) {
            this@SyncForegroundService.testLocationAndSend(host)
        }

        override fun testLocationAndSend(host: String, preciseMode: Boolean) {
            this@SyncForegroundService.testLocationAndSend(host, preciseMode)
        }

        override fun startDiscovery() {
            this@SyncForegroundService.startDiscovery()
        }

        override fun cancelDiscovery() {
            this@SyncForegroundService.cancelDiscovery()
        }

        // 兼容性方法（保留给可能还在使用的地方）
        fun getService(): SyncForegroundService = this@SyncForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        // 初始化服务组件
        locationService = LocationService(this)
        notificationHelper = NotificationHelper(this)
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        discoveryService = DiscoveryService(this)

        // 创建通知渠道
        notificationHelper.createNotificationChannel()

        // 加载持久化配置
        loadPersistedConfig()

        // 恢复服务状态（确保 ViewModel 绑定时能获取正确状态）
        restoreServiceState()

        // 设置 WebSocket 连接状态监听
        setupWebSocketListener()
    }

    /**
     * 恢复服务状态（用于服务重启后恢复 UI 状态）
     */
    private fun restoreServiceState() {
        if (shouldKeepRunning && syncScheduler != null && syncScheduler!!.isRunning) {
            // 服务正在运行，根据是否有成功记录显示对应状态
            if (lastSyncTime > 0 && currentCoordinate != null) {
                _unifiedState.value = UnifiedServiceState.Success(
                    timestamp = lastSyncTime,
                    coordinate = currentCoordinate
                )
                Log.d(TAG, "恢复服务状态: Success (lastSyncTime=$lastSyncTime, coordinate=$currentCoordinate)")
            } else {
                _unifiedState.value = UnifiedServiceState.Running
                Log.d(TAG, "恢复服务状态: Running")
            }
        } else {
            _unifiedState.value = UnifiedServiceState.Idle
            Log.d(TAG, "恢复服务状态: Idle")
        }
    }

    /**
     * 设置 WebSocket 连接状态监听
     */
    private fun setupWebSocketListener() {
        webSocketService.setConnectionStateListener { state ->
            wsConnectionState = state.toWebSocketConnectionState()
            _unifiedState.value = UnifiedServiceState.ConnectionState(wsConnectionState)
            Log.d(TAG, "WebSocket 状态变更: $wsConnectionState")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action = ${intent?.action}, shouldKeepRunning=$shouldKeepRunning")

        when (intent?.action) {
            ACTION_START_SYNC -> {
                val host = intent.getStringExtra(EXTRA_HOST) ?: ""
                val syncInterval = intent.getIntExtra(EXTRA_SYNC_INTERVAL, AppSettings.DEFAULT_SYNC_INTERVAL)
                val preciseLocationMode = intent.getBooleanExtra(EXTRA_PRECISE_LOCATION_MODE, false)
                shouldKeepRunning = true
                savePersistedConfig(host, syncInterval, shouldKeepRunning)
                startSync(host, syncInterval, preciseLocationMode)
            }
            ACTION_STOP_SYNC -> {
                shouldKeepRunning = false
                // 持久化配置（标记为停止）
                savePersistedConfig("", AppSettings.DEFAULT_SYNC_INTERVAL, shouldKeepRunning)
                stopSync()
            }
            // START_STICKY 重启时，如果应该运行则恢复同步
            null -> {
                if (shouldKeepRunning && currentHost.isNotEmpty()) {
                    Log.d(TAG, "START_STICKY 重启，恢复同步: host=$currentHost, interval=$currentSyncInterval")
                    startSync(currentHost, currentSyncInterval)
                } else {
                    Log.d(TAG, "START_STICKY 重启，但无需恢复同步: shouldKeepRunning=$shouldKeepRunning, host=$currentHost")
                    // 确保状态为 Idle
                    _unifiedState.value = UnifiedServiceState.Idle
                }
            }
        }

        // 返回 START_STICKY 以便系统杀死后自动重启
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        return true // 允许 onRebind
    }

    override fun onRebind(intent: Intent?) {
        Log.d(TAG, "onRebind")
        super.onRebind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: shouldKeepRunning=$shouldKeepRunning")

        // 注意：服务可能因为 START_STICKY 重启，此时不应该完全清理状态
        // SharedPreferences 中的配置已保存，onCreate 时会自动加载

        // 如果服务是因为显式停止（shouldKeepRunning=false）而销毁，则需要清理资源
        if (!shouldKeepRunning) {
            webSocketService.disconnect()
            syncScheduler?.cleanup()
            // 清除持久化的状态
            clearPersistedState()
        } else {
            // 如果服务应该保持运行（START_STICKY 重启场景），只停止调度器的定时器
            // 保留其他状态，以便服务重启时恢复
            syncScheduler?.stop()
        }

        // 注意：不调用 stopForeground()，系统会自动处理通知
        // 注意：不调用 stopSelf()，这是 onDestroy 被调用的原因

        Log.d(TAG, "服务销毁完成")
    }

    /**
     * 清除持久化的状态
     */
    private fun clearPersistedState() {
        sharedPrefs.edit().apply {
            remove(KEY_LAST_SYNC_TIME)
            remove(KEY_LAST_LATITUDE)
            remove(KEY_LAST_LONGITUDE)
            remove(KEY_STATUS_MESSAGE)
            apply()
        }
        Log.d(TAG, "清除持久化的状态")
    }

    /**
     * 启动同步
     * @param host FMO Host
     * @param syncIntervalMinutes 同步间隔（分钟）
     */
    private fun startSync(host: String, syncIntervalMinutes: Int) {
        startSync(host, syncIntervalMinutes, false)
    }

    private fun startSync(host: String, syncIntervalMinutes: Int, preciseLocationMode: Boolean) {
        Log.d(TAG, "startSync: host=$host, interval=$syncIntervalMinutes, isRunning=${syncScheduler?.isRunning}")

        if (host.isBlank()) {
            Log.e(TAG, "Host 不能为空，无法启动同步")
            return
        }

        // 如果调度器已经在运行，检查配置是否相同
        if (syncScheduler?.isRunning == true) {
            if (currentHost == host && currentSyncInterval == syncIntervalMinutes) {
                Log.d(TAG, "同步服务已在运行，配置相同，跳过启动")
                // 确保状态正确：如果有成功同步记录，显示成功状态
                if (lastSyncTime > 0 && currentCoordinate != null) {
                    _unifiedState.value = UnifiedServiceState.Success(
                        timestamp = lastSyncTime,
                        coordinate = currentCoordinate
                    )
                } else {
                    _unifiedState.value = UnifiedServiceState.Running
                    statusMessage = "同步中..."
                }
                // 更新通知
                updateNotification()
                saveSyncState()  // 保存状态
                return
            }
            Log.d(TAG, "配置变更，重启同步服务")
        }

        currentHost = host
        currentSyncInterval = syncIntervalMinutes

        // 创建配置
        currentConfig = SyncConfig(
            intervalMinutes = syncIntervalMinutes,
            enabled = true,
            host = host,
            preciseLocationMode = preciseLocationMode
        )

        // 停止并清理旧的调度器
        syncScheduler?.stop()
        syncScheduler?.cleanup()

        // 创建新的同步调度器
        syncScheduler = SyncScheduler(locationService, webSocketService, currentConfig!!)

        // 监听调度器状态变化
        syncScheduler?.addStateListener { state ->
            handleSyncStateChange(state)
        }

        // 启动调度器
        syncScheduler?.start()

        // 启动前台服务并显示通知
        _unifiedState.value = UnifiedServiceState.Running
        startForeground(notificationHelper.getNotificationId(), notificationHelper.buildSyncNotification(
            UnifiedServiceState.Running,
            statusMessage.ifEmpty { "同步中..." },
            currentHost,
            lastSyncTime
        ))

        Log.d(TAG, "同步服务已启动")
    }

    /**
     * 停止同步
     */
    private fun stopSync() {
        Log.d(TAG, "stopSync")

        // 停止调度器
        syncScheduler?.stop()
        syncScheduler?.cleanup()
        syncScheduler = null

        // 断开 WebSocket
        webSocketService.disconnect()

        // 取消前台服务
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationHelper.cancelSyncNotification()

        // 停止服务
        stopSelf()

        Log.d(TAG, "同步服务已停止")
    }

    /**
     * 测试定位并发送（通过 Binder 暴露）
     */
    private fun testLocationAndSend(host: String) {
        testLocationAndSend(host, false)
    }

    private fun testLocationAndSend(host: String, preciseMode: Boolean) {
        serviceScope.launch {
            try {
                _unifiedState.value = UnifiedServiceState.Running
                updateNotification()

                val location = locationService.getCurrentLocation(
                    timeout = 10000L,
                    preciseMode = preciseMode
                )
                if (location == null) {
                    _unifiedState.value = UnifiedServiceState.Error(
                        message = "定位失败"
                    )
                    return@launch
                }

                val coordinate = Coordinate(location.latitude, location.longitude)
                currentCoordinate = coordinate

                // 确保 WebSocket 已连接
                if (webSocketService.connectionState !is ConnectionState.Connected) {
                    webSocketService.connect(host)
                    delay(1000)
                }

                // 发送坐标
                val result = webSocketService.setCordinate(
                    latitude = coordinate.latitude,
                    longitude = coordinate.longitude,
                    timeoutMs = 5000L
                )

                when (result) {
                    is WebSocketResult.Success -> {
                        if (result.data.isSuccess()) {
                            _unifiedState.value = UnifiedServiceState.Success(
                                coordinate = coordinate
                            )
                            statusMessage = "坐标发送成功"
                        } else {
                            _unifiedState.value = UnifiedServiceState.Error(
                                message = "服务器处理失败"
                            )
                            statusMessage = "服务器处理失败"
                        }
                    }
                    is WebSocketResult.Error -> {
                        _unifiedState.value = UnifiedServiceState.Error(
                            message = result.message
                        )
                        statusMessage = result.message
                    }
                    is WebSocketResult.Timeout -> {
                        _unifiedState.value = UnifiedServiceState.Error(
                            message = "发送超时"
                        )
                        statusMessage = "发送超时"
                    }
                }

                updateNotification()
            } catch (e: Exception) {
                _unifiedState.value = UnifiedServiceState.Error(
                    message = e.message ?: "未知错误"
                )
                statusMessage = e.message ?: "未知错误"
                updateNotification()
            }
        }
    }

    /**
     * 开始发现设备
     */
    private fun startDiscovery() {
        serviceScope.launch {
            discoveryService.discover { state ->
                discoveryStateCache = state
                _unifiedState.value = UnifiedServiceState.DiscoveryState(state)
            }
        }
    }

    /**
     * 取消发现
     */
    private fun cancelDiscovery() {
        discoveryService.cancel()
        discoveryStateCache = DiscoveryService.DiscoveryState.Idle
    }

    /**
     * 处理同步状态变化
     */
    private fun handleSyncStateChange(state: SyncSchedulerState) {
        Log.d(TAG, "同步状态变更: $state")

        when (state) {
            is SyncSchedulerState.Idle -> {
                statusMessage = "未启动同步"
                _unifiedState.value = UnifiedServiceState.Idle
            }
            is SyncSchedulerState.Running -> {
                statusMessage = "同步中..."
                _unifiedState.value = UnifiedServiceState.Running
            }
            is SyncSchedulerState.LastSuccess -> {
                lastSyncTime = state.timestamp
                currentCoordinate = state.coordinate
                statusMessage = "同步成功 (${state.coordinate?.toFormattedString() ?: ""})"
                _unifiedState.value = UnifiedServiceState.Success(
                    timestamp = state.timestamp,
                    coordinate = state.coordinate
                )
            }
            is SyncSchedulerState.LastError -> {
                lastSyncTime = state.timestamp
                currentCoordinate = state.coordinate
                statusMessage = state.errorMessage
                _unifiedState.value = UnifiedServiceState.Error(
                    timestamp = state.timestamp,
                    message = state.errorMessage
                )
            }
        }

        // 更新通知
        updateNotification()

        // 保存状态到持久化存储
        saveSyncState()
    }

    /**
     * 更新通知
     */
    private fun updateNotification() {
        notificationHelper.updateSyncNotification(
            _unifiedState.value,
            statusMessage,
            currentHost,
            lastSyncTime
        )
    }

    /**
     * 检查是否正在运行
     */
    fun isRunning(): Boolean {
        return syncScheduler?.isRunning == true
    }

    /**
     * 获取当前状态消息
     */
    fun getCurrentStatusMessage(): String = statusMessage

    /**
     * 获取当前坐标
     */
    fun getCurrentCoordinate(): Coordinate? = currentCoordinate

    /**
     * 获取上次同步时间
     */
    fun getLastSyncTime(): Long = lastSyncTime

    /**
     * 获取 WebSocket 服务（用于绑定连接状态监听）
     */
    fun getWebSocketService(): FmoWebSocketService = webSocketService

    /**
     * 加载持久化配置
     */
    private fun loadPersistedConfig() {
        shouldKeepRunning = sharedPrefs.getBoolean(KEY_SHOULD_KEEP_RUNNING, false)
        currentHost = sharedPrefs.getString(KEY_HOST, "") ?: ""
        currentSyncInterval = sharedPrefs.getInt(KEY_SYNC_INTERVAL, AppSettings.DEFAULT_SYNC_INTERVAL)

        // 恢复同步状态
        lastSyncTime = sharedPrefs.getLong(KEY_LAST_SYNC_TIME, 0)
        val lat = sharedPrefs.getFloat(KEY_LAST_LATITUDE, Float.NaN)
        val lon = sharedPrefs.getFloat(KEY_LAST_LONGITUDE, Float.NaN)
        if (!lat.isNaN() && !lon.isNaN()) {
            currentCoordinate = Coordinate(lat.toDouble(), lon.toDouble())
        }
        statusMessage = sharedPrefs.getString(KEY_STATUS_MESSAGE, "") ?: ""

        Log.d(TAG, "加载持久化配置: shouldKeepRunning=$shouldKeepRunning, host=$currentHost, interval=$currentSyncInterval, lastSyncTime=$lastSyncTime")

        // 如果配置有效，恢复 currentConfig
        if (shouldKeepRunning && currentHost.isNotEmpty()) {
            currentConfig = SyncConfig(
                intervalMinutes = currentSyncInterval,
                enabled = true,
                host = currentHost
            )
        }
    }

    /**
     * 保存持久化配置
     */
    private fun savePersistedConfig(host: String, syncInterval: Int, keepRunning: Boolean) {
        sharedPrefs.edit().apply {
            putBoolean(KEY_SHOULD_KEEP_RUNNING, keepRunning)
            putString(KEY_HOST, host)
            putInt(KEY_SYNC_INTERVAL, syncInterval)
            putLong(KEY_LAST_SYNC_TIME, lastSyncTime)
            putFloat(KEY_LAST_LATITUDE, currentCoordinate?.latitude?.toFloat() ?: Float.NaN)
            putFloat(KEY_LAST_LONGITUDE, currentCoordinate?.longitude?.toFloat() ?: Float.NaN)
            putString(KEY_STATUS_MESSAGE, statusMessage)
            apply()
        }
        Log.d(TAG, "保存持久化配置: keepRunning=$keepRunning, host=$host, interval=$syncInterval")
    }

    /**
     * 保存同步状态
     */
    private fun saveSyncState() {
        sharedPrefs.edit().apply {
            putLong(KEY_LAST_SYNC_TIME, lastSyncTime)
            putFloat(KEY_LAST_LATITUDE, currentCoordinate?.latitude?.toFloat() ?: Float.NaN)
            putFloat(KEY_LAST_LONGITUDE, currentCoordinate?.longitude?.toFloat() ?: Float.NaN)
            putString(KEY_STATUS_MESSAGE, statusMessage)
            apply()
        }
    }
}
