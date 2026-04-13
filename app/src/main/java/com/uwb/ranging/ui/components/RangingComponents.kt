package com.uwb.ranging.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

/**
 * 距离指示器 - 中心大数字 + 圆弧进度
 */
@Composable
fun DistanceIndicator(
    distanceCm: Double,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedDistance by animateFloatAsState(
        targetValue = distanceCm.toFloat(),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "distance"
    )

    // 距离映射到颜色：近=红，中=橙，远=绿
    val distanceColor = when {
        distanceCm < 100 -> Color(0xFFE53935)     // <1m 红色
        distanceCm < 500 -> Color(0xFFFF9800)      // 1-5m 橙色
        distanceCm < 2000 -> Color(0xFF2196F3)     // 5-20m 蓝色
        else -> Color(0xFF4CAF50)                   // >20m 绿色
    }

    // 圆弧进度：10m 内线性映射，超过则满
    val progress = (distanceCm / 1000.0).coerceIn(0.0, 1.0).toFloat()

    // 脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(260.dp)
    ) {
        // 背景圆弧
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // 背景圆环
            drawCircle(
                color = Color(0xFFE0E0E0).copy(alpha = 0.3f),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )

            // 进度圆弧
            if (isConnected) {
                val startAngle = -90f
                val sweepAngle = progress * 360f

                drawArc(
                    color = distanceColor.copy(alpha = pulseAlpha),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // 中心数字
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isConnected) {
                // 距离数字
                val displayValue = if (animatedDistance >= 100) {
                    String.format("%.1f", animatedDistance / 100f)
                } else {
                    String.format("%.0f", animatedDistance)
                }

                Text(
                    text = displayValue,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = distanceColor,
                    textAlign = TextAlign.Center
                )
                // 单位
                Text(
                    text = if (animatedDistance >= 100) "m" else "cm",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = distanceColor.copy(alpha = 0.7f)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.SignalWifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "等待连接",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * 方向指示器 - 箭头指向对端设备
 */
@Composable
fun DirectionIndicator(
    azimuth: Double,
    elevation: Double,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedAzimuth by animateFloatAsState(
        targetValue = azimuth.toFloat(),
        animationSpec = tween(300),
        label = "azimuth"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = "方向",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Canvas(modifier = Modifier.size(80.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 8.dp.toPx()

            // 圆形背景
            drawCircle(
                color = Color(0xFFE3F2FD),
                radius = radius
            )
            drawCircle(
                color = Color(0xFF1976D2),
                radius = radius,
                style = Stroke(width = 2.dp.toPx())
            )

            if (isConnected) {
                // 箭头
                val angleRad = Math.toRadians(animatedAzimuth.toDouble() - 90.0)
                val arrowLength = radius * 0.75f

                val endX = center.x + cos(angleRad).toFloat() * arrowLength
                val endY = center.y + sin(angleRad).toFloat() * arrowLength

                // 箭头线
                drawLine(
                    color = Color(0xFF1976D2),
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // 箭头头部
                val headLength = 12.dp.toPx()
                val headAngle = 0.5
                val path = Path().apply {
                    moveTo(endX, endY)
                    lineTo(
                        endX - cos(angleRad - headAngle).toFloat() * headLength,
                        endY - sin(angleRad - headAngle).toFloat() * headLength
                    )
                    moveTo(endX, endY)
                    lineTo(
                        endX - cos(angleRad + headAngle).toFloat() * headLength,
                        endY - sin(angleRad + headAngle).toFloat() * headLength
                    )
                }
                drawPath(path, Color(0xFF1976D2), style = Stroke(width = 3.dp.toPx()))
            } else {
                // 中心点
                drawCircle(
                    color = Color.Gray,
                    radius = 4.dp.toPx(),
                    center = center
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (isConnected) {
            Text(
                text = "${String.format("%.0f", azimuth)}°",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 信号强度指示条
 */
@Composable
fun SignalStrengthBar(
    distanceCm: Double,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    // 信号强度：基于距离，0-5 格
    val bars = when {
        !isConnected -> 0
        distanceCm < 50 -> 5
        distanceCm < 200 -> 4
        distanceCm < 500 -> 3
        distanceCm < 1500 -> 2
        else -> 1
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
    ) {
        repeat(5) { index ->
            val height = (12 + index * 6).dp
            val color = if (index < bars) {
                when (bars) {
                    5, 4 -> Color(0xFF4CAF50)
                    3 -> Color(0xFFFF9800)
                    else -> Color(0xFFE53935)
                }
            } else {
                Color(0xFFE0E0E0)
            }

            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

/**
 * 连接状态标签
 */
@Composable
fun ConnectionStatusChip(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isConnected) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val textColor = if (isConnected) Color(0xFF2E7D32) else Color(0xFFC62828)
    val text = if (isConnected) "● 已连接" else "○ 未连接"

    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
