# UWB 测距 App

基于 Android `androidx.core.uwb` 的手机间 UWB 测距应用。

## 功能

- **设备发现** — BLE 广播 + 扫描，自动发现附近运行同 App 的设备
- **实时测距** — UWB DS-TWR 双向测距，精度 ~10-30cm
- **距离显示** — 大数字实时距离 + 圆弧进度指示器
- **方向引导** — AoA 方位角箭头指示
- **距离趋势** — 实时折线图 + 最近/最短/最长/平均统计
- **信号强度** — 基于距离的信号强度条

## 技术栈

- Kotlin + Jetpack Compose
- `androidx.core.uwb:1.0.0-alpha05` — UWB 测距 API
- BLE (Scanner + Advertiser) — 设备发现
- Material 3 + Dynamic Color

## 硬件要求

- Android 12+ (API 31+)
- UWB 硬件支持（小米 12S Ultra、小米 13/14/15 系列、小米 17 Pro Max 等）
- BLE 支持

## ⚠️ 小米设备注意事项

1. **小米可能使用自研 UWB 协议**：小米部分机型的 UWB 实现可能不完全兼容标准 FiRa 协议。如果标准 `androidx.core.uwb` API 不可用，可能需要：
   - 使用小米开放平台的 UWB SDK
   - 联系小米开发者支持获取私有 API

2. **权限问题**：小米 MIUI/HyperOS 可能需要额外开启：
   - 设置 → 应用管理 → UWB 测距 → 权限 → 打开「附近设备」
   - 开发者选项中开启「UWB」开关（如有）

3. **后台限制**：确保在电池优化中将此 App 设为「不受限制」

## 构建方式

使用 Android Studio 打开项目：

```bash
# 或命令行构建
cd uwb-ranging-app
./gradlew assembleDebug
```

## 项目结构

```
app/src/main/java/com/uwb/ranging/
├── MainActivity.kt          # 入口 + Navigation
├── UwbRangingApp.kt         # Application 类
├── ble/
│   └── BleDiscovery.kt      # BLE 扫描/广播
├── uwb/
│   └── UwbRangingManager.kt # UWB 测距核心逻辑
├── viewmodel/
│   └── RangingViewModel.kt  # 状态管理
├── ui/
│   ├── theme/Theme.kt       # Material 3 主题
│   ├── components/
│   │   └── RangingComponents.kt  # 距离指示器、方向指示器等
│   └── screens/
│       ├── DeviceDiscoveryScreen.kt  # 设备发现页
│       └── RangingScreen.kt          # 测距页
```

## 使用流程

1. 两台小米手机都安装此 App
2. 双方打开 App → 自动广播 + 扫描
3. 在设备列表中点击对方设备
4. 进入测距页面，实时显示距离和方向
5. 移动手机观察距离变化

## License

MIT
