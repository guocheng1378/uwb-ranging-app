package com.uwb.ranging.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uwb.ranging.ui.components.*
import com.uwb.ranging.uwb.UwbRangingManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangingScreen(
    uwbManager: UwbRangingManager,
    distanceHistory: List<Pair<Long, Double>>,
    onBack: () -> Unit
) {
    val rangingData by uwbManager.rangingData.collectAsState()
    val isRanging by uwbManager.isRanging.collectAsState()
    val selectedProtocol by uwbManager.selectedProtocol.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("UWB 测距")
                        // 显示当前使用的协议
                        if (selectedProtocol != null) {
                            Text(
                                text = selectedProtocol!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    ConnectionStatusChip(
                        isConnected = rangingData.isConnected,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 主距离显示区
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DistanceIndicator(
                        distanceCm = rangingData.distanceCm,
                        isConnected = rangingData.isConnected
                    )
                }
            }

            // 当前使用的协议标识
            if (rangingData.protocolUsed.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text(rangingData.protocolUsed) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (rangingData.protocolUsed) {
                                        "FiRa 标准 UWB" -> Icons.Rounded.Sensors
                                        "BLE RSSI 估算" -> Icons.Rounded.Bluetooth
                                        else -> Icons.Rounded.DeviceHub
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            // 方向 + 信号强度并排
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DirectionIndicator(
                        azimuth = rangingData.azimuth,
                        elevation = rangingData.elevation,
                        isConnected = rangingData.isConnected
                    )

                    Divider(
                        modifier = Modifier
                            .height(80.dp)
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "信号",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SignalStrengthBar(
                            distanceCm = rangingData.distanceCm,
                            isConnected = rangingData.isConnected
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (rangingData.isConnected) {
                            Text(
                                text = "精度: ${rangingData.confidenceLevel}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 历史轨迹图
            if (distanceHistory.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "距离变化趋势",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(180.dp)
                    ) {
                        DistanceChart(
                            data = distanceHistory,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        )
                    }
                }
            }

            // 统计卡片
            if (distanceHistory.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val distances = distanceHistory.map { it.second }
                        StatCard("最近", formatDist(rangingData.distanceCm), Icons.Rounded.MyLocation, Modifier.weight(1f))
                        StatCard("最短", formatDist(distances.min()), Icons.Rounded.ArrowDownward, Modifier.weight(1f))
                        StatCard("最长", formatDist(distances.max()), Icons.Rounded.ArrowUpward, Modifier.weight(1f))
                        StatCard("平均", formatDist(distances.average()), Icons.Rounded.Equalizer, Modifier.weight(1f))
                    }
                }
            }

            // 错误信息
            val errorMsg = rangingData.errorMessage
            if (errorMsg != null) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = errorMsg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // 停止按钮
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(52.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("停止测距", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DistanceChart(data: List<Pair<Long, Double>>, modifier: Modifier = Modifier) {
    if (data.size < 2) return

    val lineColor = Color(0xFF1976D2)
    val fillColor = Color(0x331976D2)
    val gridColor = Color(0xFFE0E0E0)

    val values = data.map { it.second.toFloat() }
    val maxVal = values.max()
    val minVal = values.min()
    val range = (maxVal - minVal).coerceAtLeast(1f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 4.dp.toPx()

        for (i in 0..4) {
            val y = pad + (h - 2 * pad) * i / 4f
            drawLine(gridColor, Offset(pad, y), Offset(w - pad, y), 1.dp.toPx())
        }

        val path = Path()
        val fillPath = Path()
        values.forEachIndexed { i, v ->
            val x = pad + (w - 2 * pad) * i / (values.size - 1).toFloat()
            val y = pad + (h - 2 * pad) * (1f - (v - minVal) / range)
            if (i == 0) { path.moveTo(x, y); fillPath.moveTo(x, h - pad); fillPath.lineTo(x, y) }
            else { path.lineTo(x, y); fillPath.lineTo(x, y) }
        }
        fillPath.lineTo(w - pad, h - pad); fillPath.close()
        drawPath(fillPath, fillColor)
        drawPath(path, lineColor, style = Stroke(2.5.dp.toPx()))
    }
}

private fun formatDist(cm: Double): String {
    return if (cm >= 100) String.format("%.1fm", cm / 100) else String.format("%.0fcm", cm)
}
