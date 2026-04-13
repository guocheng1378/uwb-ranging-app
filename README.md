# UWB 测距 App

基于 Android 平台 API 的手机间 UWB 测距应用，支持 FiRa 标准 UWB + BLE RSSI 双协议，实现厘米级精度的设备间距离测量。

## ✨ 功能特性

| 功能 | 说明 |
|------|------|
| **设备发现** | BLE 广播 + 扫描，自动发现附近运行同 App 的设备 |
| **实时测距** | UWB DS-TWR 双向测距，精度 ~10-30cm |
| **大数字距离显示** | 居中实时距离数值 + 圆弧进度指示器 |
| **方向引导** | AoA（到达角度）方位角箭头指示 |
| **距离趋势** | 实时折线图 + 最近/最短/最长/平均统计 |
| **信号强度** | 基于距离的信号强度条（4 格） |
| **自动降级** | UWB 不可用时自动切换 BLE RSSI 估算（精度 ~1-3m） |

## 📱 使用方法

### 硬件要求

- **Android 12+**（API 31+），推荐 Android 13+
- 支持 UWB 的设备（小米 12S Ultra、小米 13/14/15/17 系列、Google Pixel 6 Pro 及以上等）
- BLE（蓝牙低功耗）支持

### 安装

1. 前往 [Releases](../../releases) 页面下载最新版 `app-debug.apk`
2. 在手机上安装 APK（需要开启"允许安装未知来源应用"）
3. 首次打开 App，按提示授予以下权限：
   - 蓝牙扫描 / 蓝牙连接 / 蓝牙广播
   - 位置信息
   - 附近设备（Android 13+）
   - UWB 测距

### 测距步骤

1. **双方安装** — 两台手机都安装此 App
2. **双方打开** — 打开 App 后自动开始 BLE 广播和扫描
3. **发现设备** — 等待设备列表中出现对方的设备
4. **点击连接** — 点击对方设备卡片，进入测距页面
5. **实时测距** — 页面实时显示：
   - 📏 **距离** — 厘米级精度的实时距离
   - 🧭 **方向** — AoA 方位角箭头
   - 📊 **趋势** — 距离变化折线图
   - 📶 **信号** — 基于距离的信号强度指示

### 协议说明

App 会自动选择最佳测距协议：

| 协议 | 优先级 | 精度 | 使用场景 |
|------|--------|------|----------|
| FiRa 标准 UWB | 最高 | ~10-30cm | 双方设备均支持 UWB 时 |
| BLE RSSI | 兜底 | ~1-3m | UWB 不可用时自动降级 |

## 📋 权限说明

| 权限 | 用途 | 必需 |
|------|------|------|
| `BLUETOOTH_SCAN` | BLE 扫描发现附近设备 | ✅ |
| `BLUETOOTH_CONNECT` | 连接 BLE 设备 | ✅ |
| `BLUETOOTH_ADVERTISE` | BLE 广播让对方发现本机 | ✅ |
| `ACCESS_FINE_LOCATION` | BLE 扫描所需 | ✅ |
| `NEARBY_WIFI_DEVICES` | Android 13+ 设备发现 | ✅ (API 33+) |
| `UWB_RANGING` | UWB 测距 | ✅ (有 UWB 时) |
| `RANGING` | Android 16+ 通用测距 | ✅ (API 36) |
| `FOREGROUND_SERVICE` | 后台保持测距连接 | ✅ |

## 🛠 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3 + Dynamic Color
- **UWB**: `android.uwb` 平台 API（通过反射访问）
- **BLE**: Scanner + Advertiser（设备发现 + RSSI 降级）
- **架构**: ViewModel + StateFlow

## 🔧 构建

### GitHub Actions（推荐）

Push 到 `main` 分支自动触发构建，前往 [Actions](../../actions) 页面下载 Artifact。

### 本地构建

```bash
# 需要 JDK 17 + Android SDK
./gradlew assembleDebug
# APK 输出: app/build/outputs/apk/debug/app-debug.apk
```

## ❓ 常见问题

**Q: 扫描不到对方设备？**
- 确保双方都打开了 App
- 检查蓝牙和位置权限是否已授予
- 确认蓝牙已开启（App 会引导开启）
- 部分设备需要手动前往设置授予权限（勾选"不再询问"后）

**Q: 显示 BLE RSSI 而不是 UWB？**
- 对方或本机不支持 UWB，正在使用 BLE 信号强度估算
- 检查设备是否在 [UWB 支持列表](https://www.nxp.com/products/wireless-connectivity/uwb:UWB) 中

**Q: 测距精度不准？**
- UWB 模式下精度约 10-30cm，避免金属遮挡
- BLE RSSI 模式下精度约 1-3m，受环境干扰较大

## 📄 License

MIT
