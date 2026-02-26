# FMO Android GPS 工具
英文版：[README_EN.md](README_EN.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-1.5.0-blue.svg)](https://developer.android.com/jetpack/compose)
一个极简的 Android 工具，用于在同一局域网内发现 FMO 设备，并按设定周期将手机 GPS 坐标通过 WebSocket 写入 FMO。

## 项目简介
FMO Android GPS 工具是一个专为车载/移动场景设计的 Android 应用。它能够在后台持续运行，自动发现局域网内的 FMO 设备，并定期将手机的 GPS 坐标发送到设备中。

### 核心目标
-  获取手机当前位置（WGS84 坐标系）
-  通过 WebSocket 调用 FMO GEO 接口写入坐标
-  支持 1~30 分钟的周期同步
-  支持局域网自动发现（mDNS：`fmo.local`）
-  支持后台持续运行（前台服务 + 常驻通知）

### 适用场景
- 车载导航系统需要实时位置更新
- 移动设备需要定期向 FMO 设备发送位置信息
- 需要"启动后稳定同步"的自动化场景

## 功能特性

### 核心功能
- **设备发现**：通过 mDNS 自动发现局域网内的 FMO 设备
- **坐标同步**：定期获取手机 GPS 坐标并发送到 FMO 设备
- **后台运行**：使用前台服务确保应用在后台持续运行
- **状态通知**：常驻通知显示当前同步状态
- **配置持久化**：应用设置自动保存，重启后保留

### 技术特性
- **现代化 UI**：使用 Jetpack Compose 构建的现代化界面
- **MVVM 架构**：清晰的架构分离，便于维护和测试
- **协程支持**：使用 Kotlin 协程处理异步操作
- **数据持久化**：使用 DataStore 存储应用设置
- **权限管理**：完善的 Android 权限请求和处理

### 权限说明
应用需要以下权限：
- **定位权限**：获取设备当前位置
- **通知权限**：显示后台运行状态通知
- **前台服务权限**：确保应用在后台持续运行

首次启动时，应用会引导用户授予所需权限。

### 同步流程
1. 用户点击"启动定位"
2. 应用检查权限和 Host 设置
3. 建立 WebSocket 连接到 FMO 设备
4. 立即获取一次位置并发送
5. 进入周期同步任务
6. 每次同步后更新状态和坐标显示
7. 用户点击"停止定位"后停止同步

## 技术架构

### 项目结构
```
app/src/main/java/com/example/fmogeoapp/
├── MainActivity.kt              # 主 Activity，处理权限请求
├── data/                        # 数据层
│   ├── SettingsDataStore.kt     # 设置数据存储
│   └── model/AppSettings.kt     # 应用设置模型
├── network/                     # 网络层
│   └── FmoGeoProtocol.kt        # FMO GEO 协议实现
├── service/                     # 服务层
│   ├── SyncForegroundService.kt # 前台同步服务
│   ├── DiscoveryService.kt      # 设备发现服务
│   ├── LocationService.kt       # 定位服务
│   ├── SyncServiceBinder.kt     # 服务绑定器
│   └── UnifiedServiceState.kt   # 统一服务状态
├── ui/                          # UI 层
│   ├── screens/                 # 屏幕组件
│   │   ├── MainScreen.kt        # 主界面
│   │   └── SettingsScreen.kt    # 设置界面
│   └── theme/                   # 主题和样式
└── viewmodel/                   # ViewModel 层
    └── MainViewModel.kt         # 主界面 ViewModel
```

## 协议说明

### FMO GEO 协议概述
FMO GEO 协议使用 WebSocket 进行通信，消息格式为 JSON。

### 消息格式
```json
{
  "type": "config",
  "subType": "setCordinate",
  "data": {
    "latitude": 31.2304,
    "longitude": 121.4737
  },
  "code": 0
}
```

### 支持的消息类型
- `setCordinate`：设置坐标
- `getCordinate`：获取坐标
- `setCordinateResponse`：设置坐标响应
- `getCordinateResponse`：获取坐标响应

### 坐标系统
- 使用 WGS84 坐标系
- 纬度范围：-90.0 到 90.0
- 经度范围：-180.0 到 180.0

## 贡献指南
欢迎任何形式的贡献！

## 许可证
本项目采用 MIT 许可证，详情请参阅 [LICENSE](LICENSE) 文件。

## 联系方式
如有任何问题或建议，请通过以下方式联系我：
- 邮箱：xifengzui@yeah.net
