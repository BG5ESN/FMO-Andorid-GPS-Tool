package com.example.fmogeoapp.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.fmogeoapp.MainActivity
import com.example.fmogeoapp.R
import com.example.fmogeoapp.service.UnifiedServiceState
import com.example.fmogeoapp.viewmodel.MainViewModel

/**
 * 通知助手类
 * 负责创建通知渠道、构建和更新通知
 */
class NotificationHelper(private val context: Context) {

    companion object {
        /**
         * 通知渠道 ID
         */
        private const val CHANNEL_ID_SYNC = "fmo_geo_sync_channel"

        /**
         * 通知 ID
         */
        private const val NOTIFICATION_ID_SYNC = 1001

        /**
         * 通知渠道名称
         */
        private const val CHANNEL_NAME_SYNC = "定位同步服务"

        /**
         * 通知渠道描述
         */
        private const val CHANNEL_DESC_SYNC = "显示定位同步状态的后台服务通知"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * 创建通知渠道（仅在 Android 8.0+ 需要）
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SYNC,
                CHANNEL_NAME_SYNC,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESC_SYNC
                setShowBadge(false)
                setSound(null, null) // 禁用声音
                enableVibration(false) // 禁用震动
                enableLights(false) // 禁用灯光
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 构建同步服务通知
     * @param unifiedState 统一服务状态
     * @param statusMessage 状态消息
     * @param host FMO Host
     * @param lastSyncTime 上次同步时间戳
     * @return Notification 对象
     */
    fun buildSyncNotification(
        unifiedState: UnifiedServiceState,
        statusMessage: String = "",
        host: String = "",
        lastSyncTime: Long = 0L
    ): Notification {
        // 创建点击通知跳转到主界面的 Intent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 根据状态获取通知内容
        val (title, contentText) = when (unifiedState) {
            is UnifiedServiceState.Idle -> Pair(
                "定位同步已停止",
                "点击重新启动同步"
            )
            is UnifiedServiceState.Running -> Pair(
                "定位同步进行中",
                "正在获取并发送位置数据..."
            )
            is UnifiedServiceState.Success -> {
                val timeStr = if (lastSyncTime > 0) {
                    android.text.format.DateFormat.format("HH:mm:ss", lastSyncTime).toString()
                } else {
                    ""
                }
                Pair(
                    "同步成功",
                    if (timeStr.isNotEmpty()) "上次同步: $timeStr - $statusMessage" else statusMessage
                )
            }
            is UnifiedServiceState.Error -> Pair(
                "同步失败",
                unifiedState.message.takeIf { it.isNotEmpty() } ?: "连接或发送失败，请检查网络"
            )
            else -> Pair(
                "定位同步",
                statusMessage
            )
        }

        // 构建通知
        return NotificationCompat.Builder(context, CHANNEL_ID_SYNC)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(unifiedState !is UnifiedServiceState.Idle) // 持续通知
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false) // 不自动取消
            .build()
    }

    /**
     * 构建同步服务通知
     * @param syncState 同步状态
     * @return Notification 对象
     */
    fun buildSyncNotification(
        syncState: MainViewModel.SyncState,
        statusMessage: String = "",
        host: String = "",
        lastSyncTime: Long = 0L
    ): Notification {
        // 创建点击通知跳转到主界面的 Intent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 根据状态获取通知内容
        val (title, contentText) = when (syncState) {
            MainViewModel.SyncState.Idle -> Pair(
                "定位同步已停止",
                "点击重新启动同步"
            )
            MainViewModel.SyncState.Running -> Pair(
                "定位同步进行中",
                "正在获取并发送位置数据..."
            )
            MainViewModel.SyncState.Success -> {
                val timeStr = if (lastSyncTime > 0) {
                    android.text.format.DateFormat.format("HH:mm:ss", lastSyncTime).toString()
                } else {
                    ""
                }
                Pair(
                    "同步成功",
                    if (timeStr.isNotEmpty()) "上次同步: $timeStr - $statusMessage" else statusMessage
                )
            }
            MainViewModel.SyncState.Error -> Pair(
                "同步失败",
                statusMessage.takeIf { it.isNotEmpty() } ?: "连接或发送失败，请检查网络"
            )
            MainViewModel.SyncState.Timeout -> Pair(
                "同步超时",
                statusMessage.takeIf { it.isNotEmpty() } ?: "请求超时，将在下次周期重试"
            )
        }

        // 构建通知
        return NotificationCompat.Builder(context, CHANNEL_ID_SYNC)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(syncState != MainViewModel.SyncState.Idle) // 持续通知
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false) // 不自动取消
            .build()
    }

    /**
     * 更新同步服务通知
     * @param unifiedState 统一服务状态
     * @param statusMessage 状态消息
     * @param host FMO Host
     * @param lastSyncTime 上次同步时间戳
     */
    fun updateSyncNotification(
        unifiedState: UnifiedServiceState,
        statusMessage: String = "",
        host: String = "",
        lastSyncTime: Long = 0L
    ) {
        val notification = buildSyncNotification(unifiedState, statusMessage, host, lastSyncTime)
        notificationManager.notify(NOTIFICATION_ID_SYNC, notification)
    }

    /**
     * 更新同步服务通知
     * @param syncState 同步状态
     * @param statusMessage 状态消息
     * @param host FMO Host
     * @param lastSyncTime 上次同步时间戳
     */
    fun updateSyncNotification(
        syncState: MainViewModel.SyncState,
        statusMessage: String = "",
        host: String = "",
        lastSyncTime: Long = 0L
    ) {
        val notification = buildSyncNotification(syncState, statusMessage, host, lastSyncTime)
        notificationManager.notify(NOTIFICATION_ID_SYNC, notification)
    }

    /**
     * 取消同步服务通知
     */
    fun cancelSyncNotification() {
        notificationManager.cancel(NOTIFICATION_ID_SYNC)
    }

    /**
     * 获取通知渠道 ID
     */
    fun getChannelId(): String = CHANNEL_ID_SYNC

    /**
     * 获取通知 ID
     */
    fun getNotificationId(): Int = NOTIFICATION_ID_SYNC
}
