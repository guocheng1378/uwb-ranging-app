package com.uwb.ranging.uwb

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 测距协议接口
 * 支持多协议适配：FiRa 标准 / 小米私有 / BLE RSSI 降级
 */
interface RangingProtocol {

    val protocolName: String

    /**
     * 检查当前设备是否支持此协议
     */
    suspend fun isAvailable(): Boolean

    /**
     * 开始测距
     */
    suspend fun startRanging(peer: PeerDevice): Flow<RangingData>

    /**
     * 停止测距
     */
    fun stopRanging()

    /**
     * 协议优先级（越高越优先）
     */
    val priority: Int
}

/**
 * 对端设备信息
 */
data class PeerDevice(
    val address: String,
    val name: String,
    val supportedProtocols: Set<String>  // BLE 协商后得知对方支持的协议
)

/**
 * 测距数据
 */
data class RangingData(
    val distanceCm: Double = 0.0,
    val azimuth: Double = 0.0,
    val elevation: Double = 0.0,
    val peerAddress: String = "",
    val isConnected: Boolean = false,
    val protocolUsed: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val confidenceLevel: Int = 0,
    val errorMessage: String? = null
)

/**
 * BLE 广播中的协议能力声明
 */
data class ProtocolCapability(
    val deviceName: String,
    val supportedProtocols: List<String>,  // "fira", "xiaomi_uwb", "ble_rssi"
    val version: Int = 1
) {
    fun toBytes(): ByteArray {
        // 简单序列化：版本(1B) + 协议数(1B) + 各协议ID(1B each)
        val protoIds = supportedProtocols.map {
            when (it) {
                "fira" -> 0x01
                "xiaomi_uwb" -> 0x02
                "ble_rssi" -> 0x03
                else -> 0x00
            }
        }
        return byteArrayOf(version.toByte(), protoIds.size.toByte()) + protoIds.map { it.toByte() }
    }

    companion object {
        fun fromBytes(bytes: ByteArray, deviceName: String): ProtocolCapability? {
            if (bytes.size < 2) return null
            val version = bytes[0].toInt()
            val count = bytes[1].toInt()
            if (bytes.size < 2 + count) return null

            val protocols = (0 until count).mapNotNull { i ->
                when (bytes[2 + i].toInt()) {
                    0x01 -> "fira"
                    0x02 -> "xiaomi_uwb"
                    0x03 -> "ble_rssi"
                    else -> null
                }
            }
            return ProtocolCapability(deviceName, protocols, version)
        }
    }
}
