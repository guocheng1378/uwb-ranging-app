package com.uwb.ranging

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.uwb.ranging.ui.screens.DeviceDiscoveryScreen
import com.uwb.ranging.ui.screens.RangingScreen
import com.uwb.ranging.ui.theme.UWBRangingTheme
import com.uwb.ranging.viewmodel.RangingViewModel

class MainActivity : ComponentActivity() {

    private val requiredPermissions: Array<String>
        get() = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            // UWB_RANGING 需要 API 33+ (Android 13)
            if (Build.VERSION.SDK_INT >= 33) {
                add("android.permission.UWB_RANGING")
            }
        }.toTypedArray()

    private var permissionsGranted = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        permissionsGranted = allGranted
        if (!allGranted) {
            val denied = permissions.filter { !it.value }.keys
            Toast.makeText(
                this,
                "部分权限被拒绝: ${denied.joinToString()}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求权限
        val perms = requiredPermissions
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms)
        }

        setContent {
            UWBRangingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UWBApp()
                }
            }
        }
    }

    @Composable
    fun UWBApp(
        viewModel: RangingViewModel = viewModel()
    ) {
        val navController = rememberNavController()
        val hasPermissions by remember {
            derivedStateOf { viewModel.hasRequiredPermissions() }
        }

        NavHost(
            navController = navController,
            startDestination = "discovery"
        ) {
            composable("discovery") {
                DeviceDiscoveryScreen(
                    bleDiscovery = viewModel.bleDiscovery,
                    onDeviceSelected = { address ->
                        val device = viewModel.bleDiscovery.discoveredDevices.value[address]
                        viewModel.connectToDevice(address, device?.name ?: "未知设备")
                        navController.navigate("ranging") {
                            launchSingleTop = true
                        }
                    },
                    availableProtocols = viewModel.availableProtocols.collectAsState().value,
                    hasPermissions = hasPermissions,
                    onRequestPermissions = { permissionLauncher.launch(requiredPermissions) }
                )
            }

            composable("ranging") {
                RangingScreen(
                    uwbManager = viewModel.uwbManager,
                    distanceHistory = viewModel.distanceHistory.collectAsState().value,
                    onBack = {
                        viewModel.stopRanging()
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
