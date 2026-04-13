package com.uwb.ranging.uwb

import android.content.Context
import android.util.Log
import androidx.core.uwb.*
import kotlinx.coroutines.flow.*

/**
 * FiRa 标准 UWB 测距协议实现
 * 使用 Android 标准 androidx.core.uwb API
 */
class FiRaRangingProtocol(private val context: Context) : RangingProtocol {

    companion object {
        private const val TAG = "FiRaRanging"
    }

    override val protocolName = "FiRa 标准 UWB"
    override val priority = 100  // 最高优先级

    private var uwbManager: UwbManager? = null
    private var isRunning = false

    override suspend fun isAvailable(): Boolean {
        return try {
            uwbManager = UwbManager.createInstance(context)
            val caps = uwbManager?.getRangingCapabilities()
            val available = caps != null
            Log.d(TAG, "FiRa UWB 可用: $available, " +
                    "距离: ${caps?.supportsDistanceMeasurement}, " +
                    "AoA: ${caps?.supportsAzimuthAngleMeasurement}")
            available
        } catch (e: UwbHardwareNotAvailableException) {
            Log.w(TAG, "UWB 硬件不可用")
            false
        } catch (e: UwbServiceNotAvailableException) {
            Log.w(TAG, "UWB 服务不可用")
            false
        } catch (e: Exception) {
            Log.w(TAG, "FiRa 检查失败: ${e.message}")
            false
        }
    }

    override suspend fun startRanging(peer: PeerDevice): Flow<RangingData> {
        return channelFlow {
            try {
                val manager = uwbManager ?: throw IllegalStateException("FiRa 未初始化")

                val peerAddress = UwbAddress(peer.address.toByteArray())
                val peerDevice = UwbDevice(peerAddress)

                // 控制器模式：单播 DS-TWR，高频率更新
                val rangingParams = RangingParameters(
                    uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
                    sessionId = 0,
                    subSessionId = 0,
                    sessionKeyInfo = byteArrayOf(),
                    subSessionKeyInfo = byteArrayOf(),
                    complexChannel = null,
                    peerDevices = listOf(peerDevice),
                    updateRateType = RangingParameters.RANGING_UPDATE_RATE_FREQUENT
                )

                val sessionScope = manager.controllerSessionScope()
                val session = sessionScope.openSession(rangingParams)

                isRunning = true
                Log.d(TAG, "FiRa 测距已启动，目标: ${peer.name}")

                // 启动测距
                session.startRanging(rangingParams)

                // 收集结果
                session.rangingResults.collect { results ->
                    for (result in results) {
                        val data = when (result) {
                            is RangingResult.RangingResultPosition -> {
                                val pos = result.position
                                RangingData(
                                    distanceCm = pos.distance?.value?.toDouble() ?: 0.0,
                                    azimuth = pos.azimuth?.value?.toDouble() ?: 0.0,
                                    elevation = pos.elevation?.value?.toDouble() ?: 0.0,
                                    peerAddress = result.device.address.toString(),
                                    isConnected = true,
                                    protocolUsed = protocolName,
                                    timestamp = System.currentTimeMillis(),
                                    confidenceLevel = pos.distance?.confidence ?: 0
                                )
                            }
                            is RangingResult.RangingResultPeerDisconnected -> {
                                RangingData(
                                    isConnected = false,
                                    protocolUsed = protocolName,
                                    errorMessage = "FiRa: 对端断开"
                                )
                            }
                        }
                        send(data)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "FiRa 测距错误", e)
                send(RangingData(
                    isConnected = false,
                    protocolUsed = protocolName,
                    errorMessage = "FiRa 错误: ${e.message}"
                ))
            }
        }
    }

    override fun stopRanging() {
        isRunning = false
        uwbManager = null
        Log.d(TAG, "FiRa 测距已停止")
    }

    private fun String.toByteArray(): ByteArray {
        // MAC 地址 "AA:BB:CC:DD:EE:FF" → ByteArray
        return split(":").map { it.toInt(16).toByte() }.toByteArray()
    }
}
