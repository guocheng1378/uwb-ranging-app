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
        val isUwbCapable: Boolean = true,
        val isSameApp: Boolean = false  // 是否是运行同 App 的设备
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

    private fun safeAdvertiser(): BluetoothLeAdvertiser? {
        return try {
            bluetoothAdapter?.bluetoothLeAdvertiser
        } catch (e: Exception) {
            Log.w(TAG, "获取 Advertiser 失败: ${e.message}")
            null
        }
    }

    private fun safeScanner(): BluetoothLeScanner? {
        return try {
            bluetoothAdapter?.bluetoothLeScanner
        } catch (e: Exception) {
            Log.w(TAG, "获取 Scanner 失败: ${e.message}")
            null
        }
    }

    /**
     * 开始 BLE 扫描，发现附近同 App 设备
     */
    fun startScanning() {
        if (_isScanning.value) return
        val scanner = safeScanner()
        if (scanner == null) {
            Log.e(TAG, "BLE Scanner 不可用")
            return
        }

        try {
            // 小米等设备可能不支持带 UUID 的 ScanFilter，降级为无过滤扫描
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    val address = device.address
                    val name = device.name

                    // 判断是否是运行同 App 的设备
                    val serviceUuids = result.scanRecord?.serviceUuids
                    val isSameApp = serviceUuids?.any { it.uuid == SERVICE_UUID } == true
                            || result.scanRecord?.serviceData?.keys?.any { it.uuid == SERVICE_UUID } == true

                    // 跳过完全无信息的设备（无名称且非同 App）
                    if (name.isNullOrBlank() && !isSameApp) return

                    val discovered = DiscoveredDevice(
                        address = address,
                        name = name ?: address,
                        rssi = result.rssi,
                        isSameApp = isSameApp
                    )

                    val current = _discoveredDevices.value.toMutableMap()
                    current[address] = discovered
                    _discoveredDevices.value = current

                    Log.d(TAG, "发现设备: ${name ?: address} ($address) RSSI: ${result.rssi} sameApp=$isSameApp")
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "扫描失败: $errorCode")
                    _isScanning.value = false
                }
            }

            // 无过滤扫描，发现所有 BLE 设备
            scanner.startScan(scanSettings, scanCallback)
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
        val adv = safeAdvertiser()
        if (adv == null) {
            Log.w(TAG, "BLE Advertiser 不可用，跳过广播（仅接收模式）")
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
                    Log.w(TAG, "BLE 广播失败: $errorCode，继续扫描模式")
                    _isAdvertising.value = false
                    advertiseCallback = null
                }
            }

            adv.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.w(TAG, "缺少蓝牙广播权限，跳过广播", e)
            advertiseCallback = null
            _isAdvertising.value = false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "蓝牙未开启或不支持广播，跳过", e)
            advertiseCallback = null
            _isAdvertising.value = false
        } catch (e: Exception) {
            // 小米等设备可能在这里崩溃，全面兜底
            Log.w(TAG, "广播启动异常，跳过: ${e.message}", e)
            advertiseCallback = null
            _isAdvertising.value = false
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

    fun isBluetoothEnabled(): Boolean {
        return try {
            bluetoothAdapter?.isEnabled == true
        } catch (e: SecurityException) {
            Log.w(TAG, "检查蓝牙状态需要权限", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "检查蓝牙状态异常", e)
            false
        }
    }

    fun hasBluetoothLe(): Boolean {
        return try {
            context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE
            ) && bluetoothAdapter != null
        } catch (e: Exception) {
            false
        }
    }
}
