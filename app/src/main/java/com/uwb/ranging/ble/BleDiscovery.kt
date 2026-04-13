package com.uwb.ranging.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * BLE 设备发现和配对管理器
 * 用于发现附近同样运行此 App 的设备，并交换 UWB 连接信息
 */
class BleDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "BleDiscovery"
        // 自定义 Service UUID，用于识别同 App 设备
        val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        // 用于在 scan response 中携带设备名称的 UUID
        val NAME_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    }

    data class DiscoveredDevice(
        val address: String,
        val name: String,
        val rssi: Int,
        val isUwbCapable: Boolean = true
    )

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    private val _discoveredDevices = MutableStateFlow<Map<String, DiscoveredDevice>>(emptyMap())
    val discoveredDevices: StateFlow<Map<String, DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    // 当收到连接请求时触发
    val connectionRequest = Channel<String>(Channel.CONFLATED)

    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null

    /**
     * 开始 BLE 扫描，发现附近同 App 设备
     */
    fun startScanning() {
        if (_isScanning.value) return
        val scanner = bleScanner
        if (scanner == null) {
            Log.e(TAG, "BLE Scanner 不可用")
            return
        }

        try {
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    val name = device.name ?: "未知设备"
                    val address = device.address

                    val discovered = DiscoveredDevice(
                        address = address,
                        name = name,
                        rssi = result.rssi
                    )

                    val current = _discoveredDevices.value.toMutableMap()
                    current[address] = discovered
                    _discoveredDevices.value = current

                    Log.d(TAG, "发现设备: $name ($address) RSSI: ${result.rssi}")
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "扫描失败: $errorCode")
                    _isScanning.value = false
                }
            }

            scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
            _isScanning.value = true
            Log.d(TAG, "开始 BLE 扫描")
        } catch (e: SecurityException) {
            Log.e(TAG, "缺少蓝牙权限", e)
            scanCallback = null
        } catch (e: IllegalStateException) {
            Log.e(TAG, "蓝牙未开启或不可用", e)
            scanCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "扫描启动异常", e)
            scanCallback = null
        }
    }

    /**
     * 停止 BLE 扫描
     */
    fun stopScanning() {
        try {
            scanCallback?.let { bleScanner?.stopScan(it) }
        } catch (e: Exception) {
            Log.w(TAG, "停止扫描异常", e)
        }
        scanCallback = null
        _isScanning.value = false
        Log.d(TAG, "停止 BLE 扫描")
    }

    /**
     * 开始 BLE 广播，让其他设备发现本机
     */
    fun startAdvertising() {
        if (_isAdvertising.value) return
        val adv = advertiser
        if (adv == null) {
            Log.e(TAG, "BLE Advertiser 不可用")
            return
        }

        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(false)  // 不可连接，仅广播发现
                .setTimeout(0)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()

            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    Log.d(TAG, "BLE 广播成功")
                    _isAdvertising.value = true
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "BLE 广播失败: $errorCode")
                    _isAdvertising.value = false
                }
            }

            adv.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "缺少蓝牙权限", e)
            advertiseCallback = null
        } catch (e: IllegalStateException) {
            Log.e(TAG, "蓝牙未开启或不可用", e)
            advertiseCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "广播启动异常", e)
            advertiseCallback = null
        }
    }

    /**
     * 停止 BLE 广播
     */
    fun stopAdvertising() {
        try {
            advertiseCallback?.let { advertiser?.stopAdvertising(it) }
        } catch (e: Exception) {
            Log.w(TAG, "停止广播异常", e)
        }
        advertiseCallback = null
        _isAdvertising.value = false
    }

    /**
     * 清除已发现的设备列表
     */
    fun clearDevices() {
        _discoveredDevices.value = emptyMap()
    }

    /**
     * 停止所有 BLE 操作
     */
    fun stopAll() {
        stopScanning()
        stopAdvertising()
        clearDevices()
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasBluetoothLe(): Boolean = context.packageManager.hasSystemFeature(
        android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE
    ) && bluetoothAdapter != null
}
