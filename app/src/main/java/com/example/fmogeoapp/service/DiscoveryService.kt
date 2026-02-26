package com.example.fmogeoapp.service

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

/**
 * mDNS 发现服务
 * 用于发现局域网内的 FMO 设备 (fmo.local)
 */
class DiscoveryService(private val context: Context) {

    companion object {
        private const val TAG = "DiscoveryService"
        private const val SERVICE_TYPE = "_http._tcp.local."
        private const val SERVICE_NAME = "fmo"
        private const val DISCOVERY_TIMEOUT_MS = 10000L // 10秒超时
    }

    /**
     * 发现状态
     */
    sealed class DiscoveryState {
        data object Idle : DiscoveryState()
        data object Discovering : DiscoveryState()
        data class Success(val host: String) : DiscoveryState()
        data class Error(val message: String) : DiscoveryState()
    }

    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    private var jmdns: JmDNS? = null
    private var listener: ServiceListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * 开始发现 FMO 设备
     * @param timeoutMs 超时时间（毫秒）
     * @param onStateChange 状态变化回调（可选）
     */
    suspend fun discover(
        timeoutMs: Long = DISCOVERY_TIMEOUT_MS,
        onStateChange: ((DiscoveryState) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始发现 FMO 设备...")

        if (jmdns != null) {
            Log.w(TAG, "已有正在进行的发现任务")
            val errorState = DiscoveryState.Error("已有正在进行的发现任务")
            _discoveryState.value = errorState
            onStateChange?.invoke(errorState)
            return@withContext
        }

        val discoveringState = DiscoveryState.Discovering
        _discoveryState.value = discoveringState
        onStateChange?.invoke(discoveringState)

        try {
            // 获取 WiFi IP 地址
            val wifiAddress = getWifiAddress()
            if (wifiAddress == null) {
                val errorState = DiscoveryState.Error("无法获取 WiFi 地址，请确保已连接 WiFi")
                _discoveryState.value = errorState
                onStateChange?.invoke(errorState)
                Log.e(TAG, "无法获取 WiFi 地址")
                return@withContext
            }

            Log.d(TAG, "使用地址: ${wifiAddress.hostAddress}")

            // 获取 MulticastLock（非常重要！没有它永远发现不到）
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("jmdns-lock")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()
            Log.d(TAG, "已获取 MulticastLock")

            // 创建 JmDNS 实例（必须在 IO 线程执行）
            jmdns = JmDNS.create(wifiAddress)

            // 创建服务监听器
            listener = object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    Log.d(TAG, "服务添加: ${event.name}")
                    // 请求服务信息以获取 IP 地址
                    jmdns?.requestServiceInfo(event.type, event.name, 1)
                }

                override fun serviceRemoved(event: ServiceEvent) {
                    Log.d(TAG, "服务移除: ${event.name}")
                }

                override fun serviceResolved(event: ServiceEvent) {
                    val info = event.info
                    Log.d(TAG, "服务解析成功: ${info.name}")
                    Log.d(TAG, "服务详情: $info")

                    // 检查是否是我们要找的服务
                    if (info.name.lowercase().contains(SERVICE_NAME.lowercase())) {
                        val hostAddress = info.inet4Addresses.firstOrNull()?.hostAddress
                        if (hostAddress != null) {
                            Log.d(TAG, "发现 FMO 设备: $hostAddress")
                            val successState = DiscoveryState.Success(hostAddress)
                            _discoveryState.value = successState
                            onStateChange?.invoke(successState)
                        } else {
                            val errorState = DiscoveryState.Error("无法获取设备 IP 地址")
                            _discoveryState.value = errorState
                            onStateChange?.invoke(errorState)
                        }
                    }
                }
            }

            // 添加服务监听器
            jmdns?.addServiceListener(SERVICE_TYPE, listener!!)

            Log.d(TAG, "已添加服务监听器: $SERVICE_TYPE")

            // 等待发现完成或超时
            kotlinx.coroutines.delay(timeoutMs)

            // 检查是否还在发现状态
            if (_discoveryState.value is DiscoveryState.Discovering) {
                val errorState = DiscoveryState.Error("发现超时，未找到 FMO 设备")
                _discoveryState.value = errorState
                onStateChange?.invoke(errorState)
                Log.w(TAG, "发现超时")
            }

        } catch (e: Exception) {
            val errorMsg = "发现失败: ${e.message}"
            val errorState = DiscoveryState.Error(errorMsg)
            _discoveryState.value = errorState
            onStateChange?.invoke(errorState)
            Log.e(TAG, errorMsg, e)
        } finally {
            cleanup()
        }
    }

    /**
     * 取消发现
     */
    fun cancel() {
        Log.d(TAG, "取消发现")
        _discoveryState.value = DiscoveryState.Idle
        cleanup()
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        try {
            listener?.let {
                jmdns?.removeServiceListener(SERVICE_TYPE, it)
            }
            listener = null

            jmdns?.unregisterAllServices()
            jmdns?.close()
            jmdns = null

            // 释放 MulticastLock
            multicastLock?.release()
            multicastLock = null

            Log.d(TAG, "已清理 mDNS 资源")
        } catch (e: Exception) {
            Log.e(TAG, "清理 mDNS 资源时出错", e)
        }
    }

    /**
     * 获取 WiFi IP 地址
     */
    private fun getWifiAddress(): InetAddress? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo.networkId == -1) {
                Log.w(TAG, "未连接 WiFi")
                return null
            }

            val ipAddress = wifiInfo.ipAddress
            if (ipAddress == 0) {
                Log.w(TAG, "WiFi IP 地址无效")
                return null
            }

            val inetAddress = InetAddress.getByName(
                String.format(
                    "%d.%d.%d.%d",
                    ipAddress and 0xFF,
                    ipAddress shr 8 and 0xFF,
                    ipAddress shr 16 and 0xFF,
                    ipAddress shr 24 and 0xFF
                )
            )

            Log.d(TAG, "WiFi IP 地址: ${inetAddress.hostAddress}")
            return inetAddress

        } catch (e: Exception) {
            Log.e(TAG, "获取 WiFi 地址失败", e)
            return null
        }
    }
}
