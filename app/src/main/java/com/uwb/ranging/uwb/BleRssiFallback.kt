package com.uwb.ranging.uwb

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/**
 * BLE RSSI 降级测距方案
 * 当 UWB 不可用时，通过 BLE 信号强度粗略估算距离
 * 精度：±1-3m（远不如 UWB，但能用）
 */
class BleRssiFallback(private val context: Context) : RangingProtocol {

    companion object {
        private const val TAG = "BleRssiFallback"
        // RSSI 距离估算参数（Log-Distance Path Loss Model）
        private const val TX_POWER = -59  // 1米处的参考 RSSI
        private const val PATH_LOSS_EXPONENT = 2.5
    }

    override val protocolName = "BLE RSSI 估算"
    override val priority = 10  // 最低优先级

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var isRunning = false

    // RSSI 平滑滤波
    private val rssiHistory = mutableListOf<Int>()
    private val historySize = 10

    override suspend fun isAvailable(): Boolean {
        return bluetoothAdapter?.bluetoothLeScanner != null
    }

    @SuppressLint("MissingPermission")
    override suspend fun startRanging(peer: PeerDevice): Flow<RangingData> {
        return channelFlow {
            val adapter = bluetoothAdapter
            if (adapter == null) {
                trySend(RangingData(
                    isConnected = false,
                    protocolUsed = protocolName,
                    errorMessage = "蓝牙不可用"
                ))
                return@channelFlow
            }
            isRunning = true
            scanner = adapter.bluetoothLeScanner

            val filter = ScanFilter.Builder()
                .setDeviceAddress(peer.address)
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (!isRunning) return

                    val rssi = result.rssi
                    rssiHistory.add(rssi)
                    if (rssiHistory.size > historySize) {
                        rssiHistory.removeAt(0)
                    }

                    // 平滑后的 RSSI
                    val avgRssi = rssiHistory.average()

                    // Log-Distance Path Loss Model 估算距离
                    val distanceM = estimateDistance(avgRssi)
                    val distanceCm = distanceM * 100

                    trySend(RangingData(
                        distanceCm = distanceCm,
                        azimuth = 0.0,  // BLE 无法提供角度
                        elevation = 0.0,
                        peerAddress = peer.address,
                        isConnected = true,
                        protocolUsed = protocolName,
                        timestamp = System.currentTimeMillis(),
                        confidenceLevel = 30  // 低置信度
                    ))
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "BLE 扫描失败: $errorCode")
                    trySend(RangingData(
                        isConnected = false,
                        protocolUsed = protocolName,
                        errorMessage = "BLE RSSI 扫描失败: $errorCode"
                    ))
                }
            }

            try {
                scanner?.startScan(listOf(filter), settings, callback)
                Log.d(TAG, "BLE RSSI 测距已启动，目标: ${peer.address}")

                // 保持运行直到被停止
                while (isRunning) {
                    delay(100)
                }
            } finally {
                try {
                    scanner?.stopScan(callback)
                } catch (e: Exception) {
                    Log.w(TAG, "停止扫描异常", e)
                }
            }
        }
    }

    override fun stopRanging() {
        isRunning = false
        rssiHistory.clear()
        Log.d(TAG, "BLE RSSI 测距已停止")
    }

    /**
     * Log-Distance Path Loss Model:
     * d = 10 ^ ((TxPower - RSSI) / (10 * n))
     *
     * n = path loss exponent (2.0 自由空间, 2.5-4.0 室内)
     */
    private fun estimateDistance(avgRssi: Double): Double {
        if (avgRssi >= 0) return 0.1  // 异常值
        return Math.pow(10.0, (TX_POWER - avgRssi) / (10 * PATH_LOSS_EXPONENT))
    }
}
