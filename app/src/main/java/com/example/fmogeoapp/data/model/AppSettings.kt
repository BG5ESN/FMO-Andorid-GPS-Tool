package com.example.fmogeoapp.data.model

/**
 * 应用设置数据类
 */
data class AppSettings(
    val host: String = "",
    val discoveryEnabled: Boolean = true,
    val syncIntervalMinutes: Int = 5,
    val preciseLocationMode: Boolean = false
) {
    companion object {
        const val MIN_SYNC_INTERVAL = 1
        const val MAX_SYNC_INTERVAL = 30
        const val DEFAULT_SYNC_INTERVAL = 5
    }

    /**
     * 验证同步频率是否在有效范围内
     */
    fun isValidSyncInterval(): Boolean {
        return syncIntervalMinutes in MIN_SYNC_INTERVAL..MAX_SYNC_INTERVAL
    }

    /**
     * 验证 Host 是否有效（非空）
     */
    fun isValidHost(): Boolean {
        return host.isNotBlank()
    }
}
