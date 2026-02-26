package com.example.fmogeoapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fmogeoapp.service.SyncForegroundService
import com.example.fmogeoapp.ui.screens.MainScreen
import com.example.fmogeoapp.ui.screens.SettingsScreen
import com.example.fmogeoapp.ui.theme.FMOGeoAppTheme
import com.example.fmogeoapp.viewmodel.MainViewModel

/**
 * 主 Activity
 */
class MainActivity : ComponentActivity() {
    private var locationPermissionGranted by mutableStateOf(false)
    private var hasFineLocationPermission by mutableStateOf(false)
    private var notificationPermissionGranted by mutableStateOf(
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    )

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        locationPermissionGranted = fineLocation || coarseLocation
        hasFineLocationPermission = fineLocation
    }

    private val fineLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasFineLocationPermission = granted
        if (!granted) {
            val viewModel = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
            viewModel.setPreciseLocationModeAndSave(false)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestLocationPermission()
        requestNotificationPermission()

        setContent {
            FMOGeoAppTheme {
                AppNavigation(
                    locationPermissionGranted = locationPermissionGranted,
                    notificationPermissionGranted = notificationPermissionGranted,
                    hasFineLocationPermission = hasFineLocationPermission,
                    onRequestFineLocationPermission = { requestFineLocationPermission() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestFineLocationPermission() {
        fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

/**
 * 应用导航（简化版，使用状态切换）
 */
@Composable
fun AppNavigation(
    locationPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    hasFineLocationPermission: Boolean = false,
    onRequestFineLocationPermission: () -> Unit = {}
) {
    val viewModel: MainViewModel = viewModel()
    var currentScreen by remember { mutableStateOf("main") }

    when (currentScreen) {
        "main" -> MainScreen(
            viewModel = viewModel,
            locationPermissionGranted = locationPermissionGranted,
            notificationPermissionGranted = notificationPermissionGranted,
            onNavigateToSettings = { currentScreen = "settings" }
        )
        "settings" -> SettingsScreen(
            viewModel = viewModel,
            hasFineLocationPermission = hasFineLocationPermission,
            onRequestFineLocationPermission = onRequestFineLocationPermission,
            onBack = { currentScreen = "main" }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    FMOGeoAppTheme {
        AppNavigation(
            locationPermissionGranted = true,
            notificationPermissionGranted = true,
            hasFineLocationPermission = true,
            onRequestFineLocationPermission = {}
        )
    }
}
