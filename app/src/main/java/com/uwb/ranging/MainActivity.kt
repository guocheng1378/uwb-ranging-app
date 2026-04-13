package com.uwb.ranging

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.UWB_RANGING
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.UWB_RANGING
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(
                this,
                "需要蓝牙和位置权限才能使用测距功能",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求权限
        permissionLauncher.launch(requiredPermissions)

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

        NavHost(
            navController = navController,
            startDestination = "discovery"
        ) {
            // 设备发现页
            composable("discovery") {
                DeviceDiscoveryScreen(
                    bleDiscovery = viewModel.bleDiscovery,
                    onDeviceSelected = { address ->
                        viewModel.connectToDevice(address)
                        navController.navigate("ranging") {
                            launchSingleTop = true
                        }
                    },
                    isUwbSupported = viewModel.uwbSupported.collectAsState().value
                )
            }

            // 测距页
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
