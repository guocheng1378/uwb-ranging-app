package com.uwb.ranging.uwb

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.*

/**
 * 多协议测距管理器
 * 自动选择最优可用协议：FiRa > BLE RSSI
 *
 * 未来可扩展加入小米私有协议：
 *   protocols.add(XiaomiRangingProtocol(context))
 */
class UwbRangingManager(private val context: Context) {

    companion object {
        private const val TAG = "UwbRangingManager"
    }

    // 按优先级排列的协议列表（高→低）
    private val protocols: List<RangingProtocol> = listOf(
        FiRaRangingProtocol(context),   // 优先级 100
        BleRssiFallback(context)         // 优先级 10（降级）
        // 未来：XiaomiRangingProtocol(context)  // 优先级 90
    )

    private var activeProtocol: RangingProtocol? = null

    private val _rangingData = MutableStateFlow(RangingData())
    val rangingData: StateFlow<RangingData> = _rangingData.asStateFlow()

    private val _isRanging = MutableStateFlow(false)
    val isRanging: StateFlow<Boolean> = _isRanging.asStateFlow()

    private val _selectedProtocol = MutableStateFlow<String?>(null)
    val selectedProtocol: StateFlow<String?> = _selectedProtocol.asStateFlow()

    /**
     * 检查所有协议的可用性
     */
    suspend fun checkAvailability(): Map<String, Boolean> {
        return protocols.associate { it.protocolName to it.isAvailable() }
    }

    /**
     * 协商协议：取双方都支持的最优协议
     * @param myProtocols 本机支持的协议名列表
     * @param peerProtocols 对端支持的协议名列表
     */
    fun negotiateProtocol(
        myProtocols: List<String>,
        peerProtocols: List<String>
    ): RangingProtocol? {
        val commonProtocols = myProtocols.intersect(peerProtocols.toSet())

        return protocols
            .filter { it.protocolName in commonProtocols || protocolToId(it.protocolName) in commonProtocols }
            .maxByOrNull { it.priority }
    }

    /**
     * 自动选择最优可用协议并开始测距
     */
    suspend fun startRanging(peer: PeerDevice) {
        // 按优先级尝试每个协议
        for (protocol in protocols.sortedByDescending { it.priority }) {
            try {
                val available = protocol.isAvailable()
                Log.d(TAG, "检查协议 [${protocol.protocolName}]: 可用=$available")

                if (available) {
                    activeProtocol = protocol
                    _selectedProtocol.value = protocol.protocolName
                    _isRanging.value = true

                    Log.d(TAG, "使用协议: ${protocol.protocolName}")

                    // 收集测距数据
                    protocol.startRanging(peer).collect { data ->
                        _rangingData.value = data
                    }

                    // 如果 flow 结束了，说明断开了
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "协议 [${protocol.protocolName}] 启动失败，尝试下一个", e)
                continue
            }
        }

        // 所有协议都失败了
        if (!_isRanging.value) {
            _rangingData.value = RangingData(
                isConnected = false,
                errorMessage = "所有测距协议均不可用"
            )
        }
    }

    /**
     * 停止测距
     */
    fun stopRanging() {
        activeProtocol?.stopRanging()
        activeProtocol = null
        _isRanging.value = false
        _selectedProtocol.value = null
        _rangingData.value = RangingData()
    }

    /**
     * 获取本机支持的协议 ID 列表（用于 BLE 协商广播）
     */
    suspend fun getSupportedProtocolIds(): List<String> {
        return protocols
            .filter { it.isAvailable() }
            .map { protocolToId(it.protocolName) }
    }

    private fun protocolToId(name: String): String {
        return when (name) {
            "FiRa 标准 UWB" -> "fira"
            "小米 UWB" -> "xiaomi_uwb"
            "BLE RSSI 估算" -> "ble_rssi"
            else -> name
        }
    }
}
