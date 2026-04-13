package com.uwb.ranging.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uwb.ranging.ble.BleDiscovery
import com.uwb.ranging.uwb.PeerDevice
import com.uwb.ranging.uwb.UwbRangingManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RangingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "RangingViewModel"
    }

    val bleDiscovery = BleDiscovery(application)
    val uwbManager = UwbRangingManager(application)

    // 距离历史记录
    private val _distanceHistory = MutableStateFlow<List<Pair<Long, Double>>>(emptyList())
    val distanceHistory: StateFlow<List<Pair<Long, Double>>> = _distanceHistory.asStateFlow()

    // 当前状态
    private val _appState = MutableStateFlow(AppState.IDLE)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 可用协议
    private val _availableProtocols = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val availableProtocols: StateFlow<Map<String, Boolean>> = _availableProtocols.asStateFlow()

    enum class AppState {
        IDLE,
        SCANNING,
        CONNECTING,
        RANGING,
        ERROR
    }

    init {
        checkPermissions()
        observeRangingData()
    }

    private fun checkPermissions() {
        viewModelScope.launch {
            val protocols = uwbManager.checkAvailability()
            _availableProtocols.value = protocols
            Log.d(TAG, "可用协议: $protocols")

            val hasAnyProtocol = protocols.values.any { it }
            if (!hasAnyProtocol) {
                _appState.value = AppState.ERROR
                _errorMessage.value = "无可用测距协议（需要 UWB 硬件或 BLE）"
            }
        }
    }

    private fun observeRangingData() {
        viewModelScope.launch {
            uwbManager.rangingData.collect { data ->
                if (data.isConnected && data.distanceCm > 0) {
                    val current = _distanceHistory.value.toMutableList()
                    current.add(Pair(data.timestamp, data.distanceCm))
                    if (current.size > 120) current.removeAt(0)
                    _distanceHistory.value = current
                }
            }
        }
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(), permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasRequiredPermissions(): Boolean {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.UWB_RANGING)
            if (Build.VERSION.SDK_INT >= 33) {  // Android 16 preview may use 33
                add(Manifest.permission.RANGING)
            }
        }
        return permissions.all { hasPermission(it) }
    }

    fun startDiscovery() {
        if (!bleDiscovery.isBluetoothEnabled()) {
            _errorMessage.value = "请先开启蓝牙"
            return
        }

        _appState.value = AppState.SCANNING
        _errorMessage.value = null

        bleDiscovery.startAdvertising()
        bleDiscovery.startScanning()

        Log.d(TAG, "开始设备发现")
    }

    fun stopDiscovery() {
        bleDiscovery.stopAll()
        if (_appState.value == AppState.SCANNING) {
            _appState.value = AppState.IDLE
        }
    }

    fun connectToDevice(address: String, name: String = "未知设备") {
        _appState.value = AppState.CONNECTING
        _errorMessage.value = null

        bleDiscovery.stopAll()

        viewModelScope.launch {
            try {
                _appState.value = AppState.RANGING

                val peer = PeerDevice(
                    address = address,
                    name = name,
                    supportedProtocols = emptySet() // TODO: 从 BLE 协商获取
                )

                uwbManager.startRanging(peer)

            } catch (e: Exception) {
                Log.e(TAG, "连接失败", e)
                _appState.value = AppState.ERROR
                _errorMessage.value = "连接失败: ${e.message}"
            }
        }
    }

    fun stopRanging() {
        uwbManager.stopRanging()
        _appState.value = AppState.IDLE
        _distanceHistory.value = emptyList()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun reset() {
        stopRanging()
        stopDiscovery()
        _appState.value = AppState.IDLE
        _errorMessage.value = null
        _distanceHistory.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        reset()
    }
}
