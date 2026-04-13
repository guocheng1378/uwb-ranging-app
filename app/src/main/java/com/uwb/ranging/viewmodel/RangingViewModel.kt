package com.uwb.ranging.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uwb.ranging.ble.BleDiscovery
import com.uwb.ranging.uwb.UwbRangingManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RangingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "RangingViewModel"
    }

    val bleDiscovery = BleDiscovery(application)
    val uwbManager = UwbRangingManager(application)

    // 距离历史记录（最近60个点）
    private val _distanceHistory = MutableStateFlow<List<Pair<Long, Double>>>(emptyList())
    val distanceHistory: StateFlow<List<Pair<Long, Double>>> = _distanceHistory.asStateFlow()

    // 当前状态
    private val _appState = MutableStateFlow(AppState.IDLE)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // UWB 是否可用
    private val _uwbSupported = MutableStateFlow(false)
    val uwbSupported: StateFlow<Boolean> = _uwbSupported.asStateFlow()

    enum class AppState {
        IDLE,           // 空闲
        SCANNING,       // 扫描设备中
        CONNECTING,     // 连接中
        RANGING,        // 测距中
        ERROR           // 错误
    }

    init {
        checkUwbSupport()
        observeRangingData()
    }

    private fun checkUwbSupport() {
        viewModelScope.launch {
            val supported = uwbManager.initialize()
            _uwbSupported.value = supported
            if (!supported) {
                _appState.value = AppState.ERROR
                _errorMessage.value = "此设备不支持 UWB 或 UWB 服务不可用"
            }
        }
    }

    private fun observeRangingData() {
        viewModelScope.launch {
            uwbManager.rangingData.collect { data ->
                if (data.isConnected && data.distanceCm > 0) {
                    val current = _distanceHistory.value.toMutableList()
                    current.add(Pair(data.timestamp, data.distanceCm))
                    // 保留最近 120 个点
                    if (current.size > 120) {
                        current.removeAt(0)
                    }
                    _distanceHistory.value = current
                }
            }
        }
    }

    /**
     * 开始扫描附近设备
     */
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

    /**
     * 停止扫描
     */
    fun stopDiscovery() {
        bleDiscovery.stopAll()
        if (_appState.value == AppState.SCANNING) {
            _appState.value = AppState.IDLE
        }
    }

    /**
     * 连接到发现的设备并开始测距
     */
    fun connectToDevice(address: String) {
        _appState.value = AppState.CONNECTING
        _errorMessage.value = null

        // 停止扫描
        bleDiscovery.stopAll()

        viewModelScope.launch {
            try {
                // TODO: 通过 BLE 交换 UWB 地址信息后启动测距
                // 这里简化处理：直接启动为受控端模式
                _appState.value = AppState.RANGING
                uwbManager.startRangingAsControlee()

                Log.d(TAG, "已连接到设备: $address")
            } catch (e: Exception) {
                Log.e(TAG, "连接失败", e)
                _appState.value = AppState.ERROR
                _errorMessage.value = "连接失败: ${e.message}"
            }
        }
    }

    /**
     * 停止测距
     */
    fun stopRanging() {
        uwbManager.stopRanging()
        _appState.value = AppState.IDLE
        _distanceHistory.value = emptyList()
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 重置到初始状态
     */
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
