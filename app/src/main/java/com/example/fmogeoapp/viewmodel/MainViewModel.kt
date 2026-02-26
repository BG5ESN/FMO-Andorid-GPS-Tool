package com.example.fmogeoapp.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fmogeoapp.data.SettingsDataStore
import com.example.fmogeoapp.data.model.AppSettings
import com.example.fmogeoapp.network.ConnectionState
import com.example.fmogeoapp.network.Coordinate
import com.example.fmogeoapp.service.LocationService
import com.example.fmogeoapp.service.SyncForegroundService
import com.example.fmogeoapp.service.SyncServiceBinder
import com.example.fmogeoapp.service.toConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主界面 ViewModel
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsDataStore = SettingsDataStore(application)
    private val locationService = LocationService(application)

    // 服务 Binder
    private var syncService: SyncServiceBinder? = null
    private var isServiceBound = false

    // 服务连接
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SyncForegroundService.LocalBinder
            syncService = binder
            isServiceBound = true
            Log.d(TAG, "前台服务已连接")

            // 观察统一状态流
            viewModelScope.launch {
                syncService!!.stateFlow.collect { unifiedState ->
                    updateUiFromUnifiedState(unifiedState)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            syncService = null
            isServiceBound = false
            Log.d(TAG, "前台服务已断开")
        }
    }

    /**
     * UI 状态枚举（用于UI展示）
     */
    enum class SyncState {
        Idle,           // 未启动
        Running,        // 同步中
        Success,        // 成功（最近一次）
        Error,          // 失败
        Timeout         // 超时
    }

    /**
     * 发现状态（用于UI展示）
     */
    enum class DiscoveryState {
        Idle,           // 未启动
        Discovering,    // 发现中
        Success,        // 成功
        Error           // 失败
    }

    /**
     * UI 状态
     */
    data class UiState(
        val syncState: SyncState = SyncState.Idle,
        val syncIntervalMinutes: Int = AppSettings.DEFAULT_SYNC_INTERVAL,
        val host: String = "",
        val discoveryEnabled: Boolean = false,
        val discoveryState: DiscoveryState = DiscoveryState.Idle,
        val discoveryMessage: String = "",
        val discoveredHost: String = "",
        val statusMessage: String = "",
        val isLocationServiceEnabled: Boolean = false,
        val currentCoordinate: Coordinate? = null,
        val connectionState: ConnectionState = ConnectionState.Disconnected,
        val lastSyncTime: Long = 0L,
        val preciseLocationMode: Boolean = false,
        val hasFineLocationPermission: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var currentSettings: AppSettings = AppSettings()

    init {
        loadSettings()
        bindToSyncService()
    }

    /**
     * 绑定到同步服务
     */
    private fun bindToSyncService() {
        val intent = Intent(getApplication(), SyncForegroundService::class.java)
        try {
            getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "已绑定到同步服务")
        } catch (e: Exception) {
            Log.e(TAG, "绑定同步服务失败", e)
        }
    }

    /**
     * 解绑同步服务
     */
    private fun unbindFromSyncService() {
        if (isServiceBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
                isServiceBound = false
            } catch (e: Exception) {
                Log.e(TAG, "解绑同步服务失败", e)
            }
        }
    }

    /**
     * 将统一服务状态映射到 UI 状态
     */
    private fun updateUiFromUnifiedState(unifiedState: com.example.fmogeoapp.service.UnifiedServiceState) {
        when (unifiedState) {
            is com.example.fmogeoapp.service.UnifiedServiceState.Idle -> {
                _uiState.value = _uiState.value.copy(
                    syncState = SyncState.Idle,
                    statusMessage = "未启动同步"
                )
            }
            is com.example.fmogeoapp.service.UnifiedServiceState.Running -> {
                _uiState.value = _uiState.value.copy(
                    syncState = SyncState.Running,
                    statusMessage = "同步中..."
                )
            }
            is com.example.fmogeoapp.service.UnifiedServiceState.Success -> {
                _uiState.value = _uiState.value.copy(
                    syncState = SyncState.Success,
                    statusMessage = "同步成功",
                    currentCoordinate = unifiedState.coordinate,
                    lastSyncTime = unifiedState.timestamp
                )
            }
            is com.example.fmogeoapp.service.UnifiedServiceState.Error -> {
                _uiState.value = _uiState.value.copy(
                    syncState = SyncState.Error,
                    statusMessage = unifiedState.message
                )
            }
            is com.example.fmogeoapp.service.UnifiedServiceState.ConnectionState -> {
                val connectionState = unifiedState.state.toConnectionState()
                _uiState.value = _uiState.value.copy(connectionState = connectionState)
            }
            is com.example.fmogeoapp.service.UnifiedServiceState.DiscoveryState -> {
                val discoveryState = when (unifiedState.state) {
                    is com.example.fmogeoapp.service.DiscoveryService.DiscoveryState.Idle -> DiscoveryState.Idle
                    is com.example.fmogeoapp.service.DiscoveryService.DiscoveryState.Discovering -> DiscoveryState.Discovering
                    is com.example.fmogeoapp.service.DiscoveryService.DiscoveryState.Success -> DiscoveryState.Success
                    is com.example.fmogeoapp.service.DiscoveryService.DiscoveryState.Error -> DiscoveryState.Error
                }
                val discoveryMessage = when (unifiedState.state) {
                    is com.example.fmogeoapp.service.DiscoveryService.DiscoveryState.Discovering -> "正在发现设备..."
                    is com.example.fmogeoapp.service.DiscoveryService.DiscoveryState.Success -> "发现设备: ${unifiedState.state.host}"
                    is com.example.fmogeoapp.service.DiscoveryService.DiscoveryState.Error -> unifiedState.state.message
                    else -> ""
                }
                _uiState.value = _uiState.value.copy(
                    discoveryState = discoveryState,
                    discoveryMessage = discoveryMessage,
                    discoveredHost = (unifiedState.state as? com.example.fmogeoapp.service.DiscoveryService.DiscoveryState.Success)?.host ?: ""
                )
            }
        }
    }

    /**
     * 加载设置
     */
    private fun loadSettings() {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                currentSettings = settings
                _uiState.value = _uiState.value.copy(
                    syncIntervalMinutes = settings.syncIntervalMinutes,
                    host = settings.host,
                    discoveryEnabled = settings.discoveryEnabled,
                    preciseLocationMode = settings.preciseLocationMode
                )
            }
        }
    }

    /**
     * 测试定位并发送坐标（不启动前台服务）
     */
    fun testLocationAndSend() {
        if (!currentSettings.isValidHost()) {
            updateStatus("请先设置 FMO Host")
            _uiState.value = _uiState.value.copy(syncState = SyncState.Error)
            return
        }

        if (!_uiState.value.isLocationServiceEnabled) {
            updateStatus("定位服务未开启")
            _uiState.value = _uiState.value.copy(syncState = SyncState.Error)
            return
        }

        syncService?.testLocationAndSend(currentSettings.host, currentSettings.preciseLocationMode)
    }

    /**
     * 启动同步（通过前台服务）
     */
    fun startSync() {
        if (!currentSettings.isValidHost()) {
            updateStatus("请先设置 FMO Host")
            _uiState.value = _uiState.value.copy(syncState = SyncState.Error)
            return
        }

        val intent = Intent(getApplication(), SyncForegroundService::class.java).apply {
            action = SyncForegroundService.ACTION_START_SYNC
            putExtra(SyncForegroundService.EXTRA_HOST, currentSettings.host)
            putExtra(SyncForegroundService.EXTRA_SYNC_INTERVAL, currentSettings.syncIntervalMinutes)
            putExtra(SyncForegroundService.EXTRA_PRECISE_LOCATION_MODE, currentSettings.preciseLocationMode)
        }

        getApplication<Application>().startForegroundService(intent)

        _uiState.value = _uiState.value.copy(
            syncState = SyncState.Running,
            statusMessage = "已开始同步定位"
        )

        Log.d(TAG, "已启动同步服务")
    }

    /**
     * 停止同步（通过前台服务）
     */
    fun stopSync() {
        val intent = Intent(getApplication(), SyncForegroundService::class.java).apply {
            action = SyncForegroundService.ACTION_STOP_SYNC
        }

        getApplication<Application>().startService(intent)

        _uiState.value = _uiState.value.copy(
            syncState = SyncState.Idle,
            statusMessage = "已停止同步",
            connectionState = ConnectionState.Disconnected
        )

        Log.d(TAG, "已停止同步服务")
    }

    /**
     * 更新 Host
     */
    fun updateHost(host: String) {
        viewModelScope.launch {
            settingsDataStore.saveHost(host)
        }
    }

    /**
     * 更新发现开关
     */
    fun updateDiscoveryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveDiscoveryEnabled(enabled)
        }
    }

    /**
     * 开始发现 FMO 设备
     */
    fun startDiscovery() {
        if (_uiState.value.discoveryState == DiscoveryState.Discovering) {
            Log.w(TAG, "发现任务正在进行中")
            return
        }

        // 通过 Binder 调用 Service 的发现方法
        syncService?.startDiscovery()
    }

    /**
     * 取消发现
     */
    fun cancelDiscovery() {
        // 通过 Binder 调用 Service 的取消发现方法
        syncService?.cancelDiscovery()
    }

    /**
     * 应用发现的 Host 到输入框并保存
     */
    fun applyDiscoveredHost() {
        val discoveredHost = _uiState.value.discoveredHost
        if (discoveredHost.isNotEmpty()) {
            updateHost(discoveredHost)
            _uiState.value = _uiState.value.copy(
                discoveryState = DiscoveryState.Idle,
                discoveredHost = ""
            )
            Log.d(TAG, "已应用发现的 Host: $discoveredHost")
        }
    }

    /**
     * 更新同步频率
     */
    fun updateSyncInterval(minutes: Int) {
        val clampedMinutes = minutes.coerceIn(
            AppSettings.MIN_SYNC_INTERVAL,
            AppSettings.MAX_SYNC_INTERVAL
        )
        viewModelScope.launch {
            settingsDataStore.saveSyncIntervalMinutes(clampedMinutes)
        }
    }

    fun updatePreciseLocationMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.savePreciseLocationMode(enabled)
        }
    }

    fun setPreciseLocationModeAndSave(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(preciseLocationMode = enabled)
        updatePreciseLocationMode(enabled)
    }

    fun updateFineLocationPermissionState(hasPermission: Boolean) {
        _uiState.value = _uiState.value.copy(hasFineLocationPermission = hasPermission)
    }

    /**
     * 更新状态消息
     */
    private fun updateStatus(message: String) {
        _uiState.value = _uiState.value.copy(statusMessage = message)
    }

    /**
     * 检查定位服务是否启用
     */
    fun checkLocationServiceEnabled() {
        val isEnabled = locationService.isLocationEnabled()
        _uiState.value = _uiState.value.copy(isLocationServiceEnabled = isEnabled)
        if (!isEnabled) {
            updateStatus("定位服务未开启，请在设置中开启")
        }
    }

    override fun onCleared() {
        super.onCleared()
        unbindFromSyncService()
    }

    /**
     * ViewModel Factory
     */
    companion object {
        private const val TAG = "MainViewModel"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val application = modelClass.getDeclaredField("application").apply {
                    isAccessible = true
                }.get(null) as Application
                return MainViewModel(application) as T
            }
        }
    }
}
