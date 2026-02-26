package com.example.fmogeoapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import com.example.fmogeoapp.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    hasFineLocationPermission: Boolean = false,
    onRequestFineLocationPermission: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }
    var showGpsPermissionDialog by remember { mutableStateOf(false) }

    // 进入页面时自动开始发现
    LaunchedEffect(Unit) {
        if (uiState.discoveryState == MainViewModel.DiscoveryState.Idle) {
            viewModel.startDiscovery()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            HostSettingCard(
                host = uiState.host,
                onHostChange = { viewModel.updateHost(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            DiscoverySettingCard(
                discoveryState = uiState.discoveryState,
                discoveryMessage = uiState.discoveryMessage,
                discoveredHost = uiState.discoveredHost,
                onStartDiscovery = { viewModel.startDiscovery() },
                onCancelDiscovery = { viewModel.cancelDiscovery() },
                onApplyDiscoveredHost = { viewModel.applyDiscoveredHost() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SyncIntervalCard(
                minutes = uiState.syncIntervalMinutes,
                onMinutesChange = { viewModel.updateSyncInterval(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            LocationModeCard(
                preciseLocationMode = uiState.preciseLocationMode,
                onModeChange = { enabled ->
                    if (enabled && !hasFineLocationPermission) {
                        showGpsPermissionDialog = true
                    } else {
                        viewModel.setPreciseLocationModeAndSave(enabled)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            AboutCard(
                onClick = { showAboutDialog = true }
            )
        }
    }

    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }

    if (showGpsPermissionDialog) {
        GpsPermissionDialog(
            onDismiss = {
                showGpsPermissionDialog = false
            },
            onConfirm = {
                showGpsPermissionDialog = false
                onRequestFineLocationPermission()
            }
        )
    }
}

@Composable
private fun HostSettingCard(
    host: String,
    onHostChange: (String) -> Unit
) {
    var text by remember { mutableStateOf(host) }
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    if (!isFocused && text != host) {
        text = host
    }

    LaunchedEffect(isFocused) {
        if (!isFocused && text != host) {
            onHostChange(text)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "FMO Host",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "输入 FMO 设备的 IP 地址或主机名",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                placeholder = { Text("例如: 192.168.1.100 或 fmo.local") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onHostChange(text) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "保存")
            }
        }
    }
}

@Composable
private fun DiscoverySettingCard(
    discoveryState: MainViewModel.DiscoveryState,
    discoveryMessage: String,
    discoveredHost: String,
    onStartDiscovery: () -> Unit,
    onCancelDiscovery: () -> Unit,
    onApplyDiscoveredHost: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "自动发现",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "自动发现局域网内的 fmo.local 设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 发现状态显示
            when (discoveryState) {
                MainViewModel.DiscoveryState.Idle -> {
                    FilledTonalButton(
                        onClick = onStartDiscovery,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "开始发现")
                    }
                }
                MainViewModel.DiscoveryState.Discovering -> {
                    Button(
                        onClick = onCancelDiscovery,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "取消发现")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "正在发现设备...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                MainViewModel.DiscoveryState.Success -> {
                    Text(
                        text = discoveryMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 应用和重新发现按钮并排显示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onApplyDiscoveredHost,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "应用")
                        }
                        FilledTonalButton(
                            onClick = onStartDiscovery,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "重新发现")
                        }
                    }
                }
                MainViewModel.DiscoveryState.Error -> {
                    Text(
                        text = discoveryMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = onStartDiscovery,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "重试")
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncIntervalCard(
    minutes: Int,
    onMinutesChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "同步频率",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "当前: $minutes 分钟",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Slider(
                value = minutes.toFloat(),
                onValueChange = { onMinutesChange(it.toInt()) },
                valueRange = 1f..30f,
                steps = 28,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "1分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "30分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AboutCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "关于",
                style = MaterialTheme.typography.titleMedium
            )
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "关于",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    val openWebsite = {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://bg5esn.com"))
        context.startActivity(intent)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("关于")
        },
        text = {
            Column {
                Text("这是一个用于FMO位置同步的小程序，可以最小化后持续运行。注意防止手机杀后台。")
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "更多信息可以访问",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "bg5esn.com",
                    modifier = Modifier.clickable(onClick = openWebsite),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "版本 1.03",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}

@Composable
private fun LocationModeCard(
    preciseLocationMode: Boolean,
    onModeChange: (Boolean) -> Unit
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
            Text(
                text = "位置模式",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "选择定位方式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = !preciseLocationMode,
                    onClick = { onModeChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("网络定位")
                }
                SegmentedButton(
                    selected = preciseLocationMode,
                    onClick = { onModeChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("GPS模式")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (preciseLocationMode) {
                    "使用 GPS 获取高精度位置"
                } else {
                    "使用网络定位（省电）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GpsPermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "需要 GPS 权限")
        },
        text = {
            Text(text = "精确位置模式需要 GPS 权限才能工作。是否授予 GPS 定位权限？")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "授予权限")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        }
    )
}
