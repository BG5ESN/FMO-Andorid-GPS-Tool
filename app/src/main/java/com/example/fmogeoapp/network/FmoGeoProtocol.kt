package com.example.fmogeoapp.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * FMO GEO 协议数据模型
 *
 * 注意：协议子类型使用历史拼写 "Cordinate"（与设备侧保持一致）
 */

/**
 * 消息类型枚举
 */
enum class MessageType {
    @SerializedName("config")
    CONFIG
}

/**
 * 消息子类型枚举
 */
enum class MessageSubType {
    @SerializedName("setCordinate")
    SET_CORDINATE,

    @SerializedName("getCordinate")
    GET_CORDINATE,

    @SerializedName("setCordinateResponse")
    SET_CORDINATE_RESPONSE,

    @SerializedName("getCordinateResponse")
    GET_CORDINATE_RESPONSE;

    /**
     * 获取序列化后的字符串值（使用 @SerializedName）
     */
    val serializedName: String
        get() = when (this) {
            SET_CORDINATE -> "setCordinate"
            GET_CORDINATE -> "getCordinate"
            SET_CORDINATE_RESPONSE -> "setCordinateResponse"
            GET_CORDINATE_RESPONSE -> "getCordinateResponse"
        }
}

/**
 * WebSocket 消息通用外壳
 */
data class MessageEnvelope(
    val type: String = "config",
    val subType: String,
    val data: Any? = null,
    val code: Int = 0
)

/**
 * setCordinate 请求数据
 */
data class SetCordinateRequestData(
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        fun isValid(latitude: Double, longitude: Double): Boolean {
            return latitude in -90.0..90.0 && longitude in -180.0..180.0
        }
    }
}

/**
 * setCordinate 响应数据
 */
data class SetCordinateResponseData(
    val result: Int  // 0 = 成功, -1 = 失败
) {
    fun isSuccess(): Boolean = result == 0
}

/**
 * getCordinate 响应数据
 */
data class GetCordinateResponseData(
    val latitude: Double,
    val longitude: Double
)

/**
 * WebSocket 操作结果
 */
sealed class WebSocketResult<out T> {
    data class Success<T>(val data: T) : WebSocketResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : WebSocketResult<Nothing>()
    object Timeout : WebSocketResult<Nothing>()
}

/**
 * FMO WebSocket 服务类
 */
class FmoWebSocketService {
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var isConnecting = false
    private var currentHost: String = ""

    private val okHttpClient = OkHttpClient.Builder()
        .build()

    // 待处理的响应回调
    private val pendingResponses = mutableMapOf<String, (String) -> Unit>()
    private var responseHandler: ((String) -> Unit)? = null

    /**
     * 连接状态
     */
    var connectionState: ConnectionState = ConnectionState.Disconnected
        private set

    /**
     * 连接状态监听器
     */
    private var connectionStateListener: ((ConnectionState) -> Unit)? = null

    fun setConnectionStateListener(listener: (ConnectionState) -> Unit) {
        connectionStateListener = listener
    }

    private fun notifyConnectionState(state: ConnectionState) {
        connectionState = state
        connectionStateListener?.invoke(state)
    }

    /**
     * 连接到 FMO WebSocket 服务器
     */
    fun connect(host: String) {
        if (host.isBlank()) {
            notifyConnectionState(ConnectionState.Error("Host 不能为空"))
            return
        }

        if (isConnecting) {
            return
        }

        // 如果已连接且 Host 相同，不重新连接
        if (connectionState is ConnectionState.Connected && currentHost == host) {
            return
        }

        // 关闭现有连接
        disconnect()

        currentHost = host
        isConnecting = true
        notifyConnectionState(ConnectionState.Connecting)

        val url = buildWebSocketUrl(host)
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isConnecting = false
                notifyConnectionState(ConnectionState.Connected)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                // 处理响应
                try {
                    android.util.Log.d("FmoWebSocketService", "收到消息: $text")
                    val envelope = gson.fromJson(text, MessageEnvelope::class.java)
                    val subType = envelope.subType
                    android.util.Log.d("FmoWebSocketService", "消息类型: $subType, 是否有处理器: ${responseHandler != null}")

                    // 检查是否有待处理的响应处理器
                    when (subType) {
                        MessageSubType.SET_CORDINATE_RESPONSE.serializedName -> {
                            responseHandler?.invoke(text)
                            responseHandler = null
                        }
                        MessageSubType.GET_CORDINATE_RESPONSE.serializedName -> {
                            responseHandler?.invoke(text)
                            responseHandler = null
                        }
                        else -> {
                            android.util.Log.w("FmoWebSocketService", "未处理的子类型: $subType")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FmoWebSocketService", "解析消息失败", e)
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // 不处理二进制消息
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                isConnecting = false
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                isConnecting = false
                notifyConnectionState(ConnectionState.Disconnected)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                isConnecting = false
                notifyConnectionState(ConnectionState.Error(t.message ?: "连接失败"))
            }
        })
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        webSocket?.close(1000, "客户端主动断开")
        webSocket = null
        isConnecting = false
        responseHandler = null
        notifyConnectionState(ConnectionState.Disconnected)
    }

    /**
     * 发送 setCordinate 请求
     */
    suspend fun setCordinate(
        latitude: Double,
        longitude: Double,
        timeoutMs: Long = 5000L
    ): WebSocketResult<SetCordinateResponseData> {
        // 验证坐标范围
        if (!SetCordinateRequestData.isValid(latitude, longitude)) {
            return WebSocketResult.Error("坐标范围无效")
        }

        // 检查连接状态
        if (connectionState !is ConnectionState.Connected) {
            return WebSocketResult.Error("未连接到服务器")
        }

        val ws = webSocket ?: return WebSocketResult.Error("WebSocket 未初始化")

        // 构造请求消息
        val requestData = SetCordinateRequestData(latitude, longitude)
        val message = MessageEnvelope(
            type = "config",
            subType = MessageSubType.SET_CORDINATE.serializedName,
            data = requestData
        )

        val json = gson.toJson(message)
        android.util.Log.d("FmoWebSocketService", "发送消息: $json")

        return suspendCancellableCoroutine { continuation ->
            // 设置响应处理器
            responseHandler = { responseText ->
                try {
                    android.util.Log.d("FmoWebSocketService", "收到响应: $responseText")
                    val envelope = gson.fromJson(responseText, MessageEnvelope::class.java)
                    val jsonData = gson.toJson(envelope.data)
                    val responseData = gson.fromJson(jsonData, SetCordinateResponseData::class.java)
                    continuation.resume(WebSocketResult.Success(responseData))
                } catch (e: Exception) {
                    continuation.resume(WebSocketResult.Error("解析响应失败", e))
                }
            }

            // 发送消息
            val sent = ws.send(json)
            if (!sent) {
                responseHandler = null
                continuation.resume(WebSocketResult.Error("发送失败"))
                return@suspendCancellableCoroutine
            }

            android.util.Log.d("FmoWebSocketService", "消息发送${if (sent) "成功" else "失败"}")

            // 启动超时定时器
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (continuation.isActive) {
                    android.util.Log.w("FmoWebSocketService", "响应超时")
                    responseHandler = null
                    continuation.resume(WebSocketResult.Timeout)
                }
            }, timeoutMs)
        }
    }

    /**
     * 发送 getCordinate 请求
     */
    suspend fun getCordinate(timeoutMs: Long = 5000L): WebSocketResult<GetCordinateResponseData> {
        // 检查连接状态
        if (connectionState !is ConnectionState.Connected) {
            return WebSocketResult.Error("未连接到服务器")
        }

        val ws = webSocket ?: return WebSocketResult.Error("WebSocket 未初始化")

        // 构造请求消息
        val message = MessageEnvelope(
            type = "config",
            subType = MessageSubType.GET_CORDINATE.serializedName,
            data = null
        )

        val json = gson.toJson(message)

        return suspendCancellableCoroutine { continuation ->
            // 设置响应处理器
            responseHandler = { responseText ->
                try {
                    val envelope = gson.fromJson(responseText, MessageEnvelope::class.java)
                    val jsonData = gson.toJson(envelope.data)
                    val responseData = gson.fromJson(jsonData, GetCordinateResponseData::class.java)
                    continuation.resume(WebSocketResult.Success(responseData))
                } catch (e: Exception) {
                    continuation.resume(WebSocketResult.Error("解析响应失败", e))
                }
            }

            // 发送消息
            val sent = ws.send(json)
            if (!sent) {
                responseHandler = null
                continuation.resume(WebSocketResult.Error("发送失败"))
                return@suspendCancellableCoroutine
            }

            // 启动超时定时器
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (continuation.isActive) {
                    responseHandler = null
                    continuation.resume(WebSocketResult.Timeout)
                }
            }, timeoutMs)
        }
    }

    /**
     * 构建 WebSocket URL
     */
    private fun buildWebSocketUrl(host: String): String {
        return if (host.startsWith("ws://") || host.startsWith("wss://")) {
            host + "/ws"
        } else {
            "ws://$host/ws"
        }
    }
}

/**
 * 连接状态
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * 坐标数据类
 */
data class Coordinate(
    val latitude: Double,
    val longitude: Double
) {
    fun toFormattedString(): String {
        return "纬度: ${"%.6f".format(latitude)}, 经度: ${"%.6f".format(longitude)}"
    }

    companion object {
        fun isValid(latitude: Double, longitude: Double): Boolean {
            return latitude in -90.0..90.0 && longitude in -180.0..180.0
        }
    }
}
