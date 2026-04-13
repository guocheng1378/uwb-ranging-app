package com.uwb.ranging.uwb

import android.content.Context
import android.util.Log
import androidx.core.uwb.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UWB 测距管理器
 * 封装 androidx.core.uwb API，处理控制器/受控端测距逻辑
 */
class UwbRangingManager(private val context: Context) {

    companion object {
        private const val TAG = "UwbRangingManager"
    }

    data class RangingData(
        val distanceCm: Double = 0.0,
        val azimuth: Double = 0.0,       // 水平角 (度)
        val elevation: Double = 0.0,     // 仰角 (度)
        val peerAddress: String = "",
        val isConnected: Boolean = false,
        val timestamp: Long = System.currentTimeMillis(),
        val confidenceLevel: Int = 0,    // 置信度 0-100
        val errorMessage: String? = null
    )

    private val _rangingData = MutableStateFlow(RangingData())
    val rangingData: StateFlow<RangingData> = _rangingData.asStateFlow()

    private val _isRanging = MutableStateFlow(false)
    val isRanging: StateFlow<Boolean> = _isRanging.asStateFlow()

    private val _uwbAvailable = MutableStateFlow(false)
    val uwbAvailable: StateFlow<Boolean> = _uwbAvailable.asStateFlow()

    private var uwbManager: UwbManager? = null
    private var clientSessionScope: UwbClientSessionScope? = null

    /**
     * 初始化 UWB 管理器
     */
    suspend fun initialize(): Boolean {
        return try {
            uwbManager = UwbManager.createInstance(context)

            // 检查 UWB 硬件是否可用
            val capabilities = uwbManager?.getRangingCapabilities()
            if (capabilities != null) {
                _uwbAvailable.value = true
                Log.d(TAG, "UWB 可用 - 支持 AoA: ${capabilities.supportsAzimuthAngleMeasurement}, " +
                        "支持距离: ${capabilities.supportsDistanceMeasurement}")
                true
            } else {
                _uwbAvailable.value = false
                _rangingData.value = _rangingData.value.copy(
                    errorMessage = "UWB 硬件不可用"
                )
                false
            }
        } catch (e: UwbHardwareNotAvailableException) {
            Log.e(TAG, "UWB 硬件不存在", e)
            _uwbAvailable.value = false
            _rangingData.value = _rangingData.value.copy(
                errorMessage = "设备不支持 UWB 硬件"
            )
            false
        } catch (e: UwbServiceNotAvailableException) {
            Log.e(TAG, "UWB 服务不可用", e)
            _uwbAvailable.value = false
            _rangingData.value = _rangingData.value.copy(
                errorMessage = "UWB 服务暂不可用，请检查系统更新"
            )
            false
        } catch (e: Exception) {
            Log.e(TAG, "UWB 初始化失败", e)
            _rangingData.value = _rangingData.value.copy(
                errorMessage = "UWB 初始化失败: ${e.message}"
            )
            false
        }
    }

    /**
     * 作为控制器发起测距
     * @param peerAddress 对端 UWB 地址
     * @param peerDevice 对端 UWB 设备信息
     */
    suspend fun startRangingAsController(
        peerAddress: UwbAddress,
        peerDevice: UwbDevice
    ) {
        try {
            val manager = uwbManager ?: throw IllegalStateException("UWB 未初始化")

            // 获取或创建控制器会话
            val sessionScope = manager.controllerSessionScope()

            // 配置测距参数
            val rangingParams = RangingParameters(
                uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
                sessionId = 0, // 自动生成
                subSessionId = 0,
                sessionKeyInfo = byteArrayOf(),
                subSessionKeyInfo = byteArrayOf(),
                complexChannel = null,
                peerDevices = listOf(peerDevice),
                updateRateType = RangingParameters.RANGING_UPDATE_RATE_FREQUENT
            )

            // 打开会话
            val rangingSession = sessionScope.openSession(rangingParams)

            // 监听测距结果
            rangingSession.rangingResults.collect { results ->
                for (result in results) {
                    when (result) {
                        is RangingResult.RangingResultPosition -> {
                            val position = result.position
                            val distance = position.distance?.value?.toDouble() ?: 0.0
                            val azimuth = position.azimuth?.value?.toDouble() ?: 0.0
                            val elevation = position.elevation?.value?.toDouble() ?: 0.0
                            val confidence = position.distance?.confidence ?: 0

                            _rangingData.value = RangingData(
                                distanceCm = distance,
                                azimuth = azimuth,
                                elevation = elevation,
                                peerAddress = result.device.address.toString(),
                                isConnected = true,
                                timestamp = System.currentTimeMillis(),
                                confidenceLevel = confidence
                            )

                            Log.d(TAG, "距离: ${distance}cm, 方位角: ${azimuth}°, 仰角: ${elevation}°")
                        }
                        is RangingResult.RangingResultPeerDisconnected -> {
                            Log.d(TAG, "对端设备断开: ${result.device}")
                            _rangingData.value = _rangingData.value.copy(
                                isConnected = false,
                                errorMessage = "对端设备已断开"
                            )
                        }
                    }
                }
            }

            // 启动测距
            rangingSession.startRanging(rangingParams)
            clientSessionScope = sessionScope
            _isRanging.value = true

            Log.d(TAG, "UWB 测距已启动 (控制器模式)")

        } catch (e: Exception) {
            Log.e(TAG, "启动测距失败", e)
            _rangingData.value = _rangingData.value.copy(
                errorMessage = "启动测距失败: ${e.message}"
            )
        }
    }

    /**
     * 作为受控端响应测距
     */
    suspend fun startRangingAsControlee() {
        try {
            val manager = uwbManager ?: throw IllegalStateException("UWB 未初始化")

            val sessionScope = manager.controleeSessionScope()

            // 受控端等待控制器发起测距
            sessionScope.rangingResults.collect { results ->
                for (result in results) {
                    when (result) {
                        is RangingResult.RangingResultPosition -> {
                            val position = result.position
                            val distance = position.distance?.value?.toDouble() ?: 0.0
                            val azimuth = position.azimuth?.value?.toDouble() ?: 0.0
                            val elevation = position.elevation?.value?.toDouble() ?: 0.0

                            _rangingData.value = RangingData(
                                distanceCm = distance,
                                azimuth = azimuth,
                                elevation = elevation,
                                peerAddress = result.device.address.toString(),
                                isConnected = true,
                                timestamp = System.currentTimeMillis()
                            )
                        }
                        is RangingResult.RangingResultPeerDisconnected -> {
                            _rangingData.value = _rangingData.value.copy(
                                isConnected = false,
                                errorMessage = "对端设备已断开"
                            )
                        }
                    }
                }
            }

            _isRanging.value = true
            Log.d(TAG, "UWB 测距已启动 (受控端模式)")

        } catch (e: Exception) {
            Log.e(TAG, "受控端启动失败", e)
            _rangingData.value = _rangingData.value.copy(
                errorMessage = "受控端启动失败: ${e.message}"
            )
        }
    }

    /**
     * 停止测距
     */
    fun stopRanging() {
        try {
            clientSessionScope?.close()
            clientSessionScope = null
            _isRanging.value = false
            _rangingData.value = RangingData()
            Log.d(TAG, "UWB 测距已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止测距失败", e)
        }
    }

    fun getCapabilities(): RangingCapabilities? {
        return try {
            uwbManager?.getRangingCapabilities()
        } catch (e: Exception) {
            null
        }
    }
}
