package com.uwb.ranging.uwb

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.*

/**
 * FiRa 标准 UWB 测距协议实现
 *
 * 使用 android.uwb 平台 API（需系统签名或小米等厂商支持）
 * 如果平台 API 不可用，自动回退到 BLE RSSI
 *
 * 小米 HyperOS 上 UWB 权限：
 * - android.permission.UWB_RANGING (Android 13+)
 * - android.permission.RANGING (Android 16+)
 *
 * 注意：android.uwb 是 @SystemApi，普通 App 可能无法直接调用
 * 小米手机可能通过系统级集成开放了此 API
 */
class FiRaRangingProtocol(private val context: Context) : RangingProtocol {

    companion object {
        private const val TAG = "FiRaRanging"
    }

    override val protocolName = "FiRa 标准 UWB"
    override val priority = 100

    private var uwbManager: Any? = null
    private var isRunning = false

    override suspend fun isAvailable(): Boolean {
        return try {
            // 检查 UWB 硬件特性
            val hasUwbFeature = context.packageManager.hasSystemFeature("android.hardware.uwb")
            if (!hasUwbFeature) {
                Log.d(TAG, "设备不支持 UWB 硬件特性")
                return false
            }

            // 尝试通过反射获取 UwbManager
            val uwbManagerClass = Class.forName("android.uwb.UwbManager")
            val getSystemService = Context::class.java.getMethod(
                "getSystemService", String::class.java
            )
            // UwbManager service name
            val serviceName = "uwb"
            uwbManager = getSystemService.invoke(context, serviceName)

            if (uwbManager != null) {
                Log.d(TAG, "UwbManager 获取成功")
                // 尝试获取能力
                tryGetCapabilities()
                true
            } else {
                Log.d(TAG, "UwbManager 为 null")
                false
            }
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "android.uwb.UwbManager 类不存在: ${e.message}")
            false
        } catch (e: Exception) {
            Log.d(TAG, "UWB 检查失败: ${e.message}")
            false
        }
    }

    private fun tryGetCapabilities() {
        try {
            val manager = uwbManager ?: return
            val method = manager.javaClass.getMethod("getRangingCapabilities")
            val caps = method.invoke(manager)
            if (caps != null) {
                val capsClass = caps.javaClass
                val distanceMethod = capsClass.getMethod("supportsDistanceMeasurement")
                val aoaMethod = capsClass.getMethod("supportsAzimuthAngleMeasurement")
                val distance = distanceMethod.invoke(caps) as? Boolean ?: false
                val aoa = aoaMethod.invoke(caps) as? Boolean ?: false
                Log.d(TAG, "UWB 能力 - 距离: $distance, AoA: $aoa")
            }
        } catch (e: Exception) {
            Log.d(TAG, "获取 UWB 能力失败: ${e.message}")
        }
    }

    override suspend fun startRanging(peer: PeerDevice): Flow<RangingData> {
        return channelFlow {
            try {
                val manager = uwbManager ?: throw IllegalStateException("UwbManager 未初始化")

                isRunning = true
                Log.d(TAG, "尝试启动 UWB 测距，目标: ${peer.name}")

                // 通过反射调用 UWB 测距 API
                // 不同厂商的 API 可能略有差异
                val rangingResult = tryStartRangingViaReflection(manager, peer)

                if (rangingResult) {
                    // 测距已启动，持续收集结果
                    while (isRunning) {
                        kotlinx.coroutines.delay(200)
                        // 实际结果通过回调获取，这里暂时模拟
                        // 真实实现需要注册 RangingResult.Callback
                    }
                } else {
                    send(RangingData(
                        isConnected = false,
                        protocolUsed = protocolName,
                        errorMessage = "UWB 测距启动失败"
                    ))
                }

            } catch (e: SecurityException) {
                Log.e(TAG, "UWB 权限不足", e)
                send(RangingData(
                    isConnected = false,
                    protocolUsed = protocolName,
                    errorMessage = "缺少 UWB 权限: ${e.message}"
                ))
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

    private fun tryStartRangingViaReflection(manager: Any, peer: PeerDevice): Boolean {
        return try {
            // 尝试不同的 API 版本
            // Android 12+: UwbManager.openRangingSession(params, executor, callback)
            // Android 16+: 可能有新的 Ranging API

            val managerClass = manager.javaClass

            // 检查是否有 openRangingSession 方法
            val methods = managerClass.methods.map { it.name }.toSet()
            Log.d(TAG, "UwbManager 可用方法: ${methods.filter { it.contains("rang", true) }}")

            // 尝试 openRangingSession
            val hasOpenSession = methods.any { it == "openRangingSession" }
            if (hasOpenSession) {
                Log.d(TAG, "发现 openRangingSession 方法")
                // 需要构建 RangingParameters 和 Callback
                // 这部分因厂商而异，需要具体适配
            }

            // 如果厂商 API 不同，可能需要其他方式
            false  // 暂时返回 false，让 BLE RSSI 兜底
        } catch (e: Exception) {
            Log.e(TAG, "反射调用失败", e)
            false
        }
    }

    override fun stopRanging() {
        isRunning = false
        Log.d(TAG, "FiRa 测距已停止")
    }
}
