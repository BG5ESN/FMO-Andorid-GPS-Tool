package com.example.fmogeoapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.fmogeoapp.network.ConnectionState
import com.example.fmogeoapp.viewmodel.MainViewModel

/**
 * 主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    locationPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkLocationServiceEnabled()
    }

    // 检查通知权限状态（每次进入主页面时都检查）
    LaunchedEffect(notificationPermissionGranted, locationPermissionGranted) {
        // 当权限状态变化时，重新检查通知权限
    }

    // 显示权限提示
    if (!locationPermissionGranted) {
        LocationPermissionMessage()
        return
    }

    // 显示通知权限提示（Android 13+）
    if (!notificationPermissionGranted) {
        NotificationPermissionMessage()
        return
    }

    Scaffold(
        topBar = {
            MainTopBar(
                onSettingsClick = onNavigateToSettings
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // 状态显示
            StatusCard(
                syncState = uiState.syncState,
                statusMessage = uiState.statusMessage
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 当前坐标显示
            CurrentCoordinateCard(
                coordinate = uiState.currentCoordinate
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 连接状态显示
            ConnectionStateCard(
                connectionState = uiState.connectionState
            )

            Spacer(modifier = Modifier.height(16.dp))

            ButtonRow(
                syncState = uiState.syncState,
                isLocationServiceEnabled = uiState.isLocationServiceEnabled,
                hasHost = uiState.host.isNotBlank(),
                onStartSync = { viewModel.startSync() },
                onStopSync = { viewModel.stopSync() },
                onTestLocation = { viewModel.testLocationAndSend() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 当前设置信息
            CurrentSettingsInfo(
                host = uiState.host,
                syncIntervalMinutes = uiState.syncIntervalMinutes,
                connectionState = uiState.connectionState
            )
        }
    }
}

/**
 * 顶部栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(onSettingsClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(text = "FMO 定位同步")
        },
        colors = TopAppBarDefaults.topAppBarColors(),
        actions = {
            TextButton(onClick = onSettingsClick) {
                Text(text = "设置")
            }
        }
    )
}

/**
 * 状态卡片
 */
@Composable
private fun StatusCard(
    syncState: MainViewModel.SyncState,
    statusMessage: String
) {
    val (text, color, backgroundColor) = when (syncState) {
        MainViewModel.SyncState.Idle -> Triple(
            "未启动同步",
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant
        )
        MainViewModel.SyncState.Running -> Triple(
            if (statusMessage.isNotEmpty()) statusMessage else "同步中...",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )
        MainViewModel.SyncState.Success -> Triple(
            if (statusMessage.isNotEmpty()) statusMessage else "同步成功",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.tertiaryContainer
        )
        MainViewModel.SyncState.Error -> Triple(
            if (statusMessage.isNotEmpty()) statusMessage else "同步失败",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer
        )
        MainViewModel.SyncState.Timeout -> Triple(
            if (statusMessage.isNotEmpty()) statusMessage else "同步超时",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                color = color
            )
        }
    }
}

/**
 * 当前坐标卡片
 */
@Composable
private fun CurrentCoordinateCard(
    coordinate: com.example.fmogeoapp.network.Coordinate?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "当前坐标",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (coordinate != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = coordinate.toFormattedString(),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Text(
                    text = "暂无坐标数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 连接状态卡片
 */
@Composable
private fun ConnectionStateCard(
    connectionState: ConnectionState
) {
    val (text, color) = when (connectionState) {
        is ConnectionState.Connected -> Pair(
            "已连接",
            MaterialTheme.colorScheme.tertiary
        )
        is ConnectionState.Connecting -> Pair(
            "连接中...",
            MaterialTheme.colorScheme.primary
        )
        is ConnectionState.Disconnected -> Pair(
            "未连接",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        is ConnectionState.Error -> Pair(
            "连接失败",
            MaterialTheme.colorScheme.error
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = color
            )
            Text(
                text = "连接状态: $text",
                style = MaterialTheme.typography.bodyLarge,
                color = color
            )
        }
    }
}

/**
 * 按钮组
 */
@Composable
private fun ButtonRow(
    syncState: MainViewModel.SyncState,
    isLocationServiceEnabled: Boolean,
    hasHost: Boolean,
    onStartSync: () -> Unit,
    onStopSync: () -> Unit,
    onTestLocation: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (syncState == MainViewModel.SyncState.Idle ||
            syncState == MainViewModel.SyncState.Error ||
            syncState == MainViewModel.SyncState.Timeout
        ) {
            Button(
                onClick = onStartSync,
                enabled = isLocationServiceEnabled && hasHost,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(text = "启动定位")
            }
            OutlinedButton(
                onClick = onTestLocation,
                enabled = isLocationServiceEnabled && hasHost,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "测试定位")
            }
        } else {
            // Running 或 Success 状态都显示"停止定位"按钮
            Button(
                onClick = onStopSync,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(text = "停止定位")
            }
            // Success 状态时也显示"测试定位"按钮
            if (syncState == MainViewModel.SyncState.Success) {
                OutlinedButton(
                    onClick = onTestLocation,
                    enabled = isLocationServiceEnabled && hasHost,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "测试定位")
                }
            }
        }
    }
}

/**
 * 当前设置信息
 */
@Composable
private fun CurrentSettingsInfo(
    host: String,
    syncIntervalMinutes: Int,
    connectionState: ConnectionState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "当前设置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Host: ${if (host.isEmpty()) "未设置" else host}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "同步频率: $syncIntervalMinutes 分钟",
                style = MaterialTheme.typography.bodyMedium
            )
            when (connectionState) {
                is ConnectionState.Connected -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "状态: 已经连接到FMO",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                is ConnectionState.Error -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "状态: ${connectionState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }
        }
    }
}

/**
 * 定位权限提示消息
 */
@Composable
private fun LocationPermissionMessage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "需要定位权限",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "请授予应用定位权限以使用定位功能",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * 通知权限提示消息（Android 13+）
 */
@Composable
private fun NotificationPermissionMessage() {
    val context = LocalContext.current

    val openAppSettings = {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "需要通知权限",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "请授予应用通知权限以在后台运行时显示状态",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = openAppSettings) {
            Text(text = "进入应用设置")
        }
    }
}
