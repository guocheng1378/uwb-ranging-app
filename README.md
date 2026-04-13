# UWB 测距 App

基于 Android 平台 API 的手机间 UWB 测距应用，支持 FiRa 标准 UWB + BLE RSSI 双协议。

## 功能

- **设备发现** — BLE 广播 + 扫描，自动发现附近运行同 App 的设备
- **实时测距** — UWB DS-TWR 双向测距，精度 ~10-30cm
- **距离显示** — 大数字实时距离 + 圆弧进度指示器
- **方向引导** — AoA 方位角箭头指示
- **距离趋势** — 实时折线图 + 最近/最短/最长/平均统计
- **信号强度** — 基于距离的信号强度条
- **自动降级** — UWB 不可用时自动切换 BLE RSSI 估算

## 技术栈

- Kotlin + Jetpack Compose
- `android.uwb` 平台 API（通过反射访问）
- BLE (Scanner + Advertiser) — 设备发现 + RSSI 降级
- Material 3 + Dynamic Color

## 硬件要求

- Android 12+ (API 31+)，推荐 Android 13+
- UWB 硬件支持（小米 12S Ultra、小米 13/14/15/17 系列等）
- BLE 支持

## 协议支持

| 协议 | 优先级 | 精度 | 说明 |
|------|--------|------|------|
| FiRa 标准 UWB | 100 | ~10-30cm | 标准 UWB 测距 |
| BLE RSSI | 10 | ~1-3m | 信号强度估算，兜底方案 |

## 构建

GitHub Actions 自动编译：
1. Push 到 `main` 分支自动触发
2. 在 Actions → Artifacts 下载 APK

本地构建：
```bash
./gradlew assembleDebug
```

## 使用流程

1. 两台手机都安装此 App
2. 双方打开 App → 自动广播 + 扫描
3. 在设备列表中点击对方设备
4. 进入测距页面，实时显示距离和方向

## License

MIT
