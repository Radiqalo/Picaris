# Picaris

**一款采用 Material 3 Expressive 设计的 Pixiv Android 客户端。**

[![Android](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/11)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)

[English](README.en.md)

Picaris 是独立开发的开源 Pixiv 客户端，支持浏览和阅读插画、漫画与小说。支持 Android 11（API 30）及以上版本，浏览 Pixiv 内容需要登录账号。

> Picaris 是非官方客户端，与 Pixiv Inc. 无任何关联或背书。作品版权归创作者所有，请遵守 Pixiv 服务条款及作者的使用许可。

## 功能

- **浏览与发现**：插画、漫画、小说、排行榜、关注动态、标签和相关作品。
- **搜索**：搜索作品、创作者和标签；支持标签翻译，同时保留原始搜索词。
- **阅读**：多页作品横向翻页或纵向阅读、原图缩放、动图播放，以及小说排版调整。
- **离线下载**：下载队列、并发数设置、暂停/继续/重试、进度显示，以及列表和图片流视图。动图可保存为 GIF，或选择目录保存源 ZIP。
- **内容管理**：收藏、浏览历史、追更系列和下载作品。

## 开始使用

### 环境要求

- 支持当前工具链的 Android Studio（建议使用 JDK 25）。
- Android SDK Platform 37.2 及对应 Build Tools。
- Android 11（API 30）及以上设备或模拟器。

### 构建

克隆仓库，在 `local.properties` 中配置 Android SDK 路径：

```properties
sdk.dir=/path/to/Android/Sdk
```

构建 Debug 版本：

```sh
./gradlew :app:assembleDebug
```

运行单元测试：

```sh
./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :designsystem:testDebugUnitTest
```

## 项目结构

| 模块 | 用途 |
| --- | --- |
| `app` | Android 应用、Compose 页面、账号流程与下载服务 |
| `core` | Pixiv 协议、数据持久化、分页和领域逻辑 |
| `designsystem` | Material 3 Expressive 主题与共享 UI 资源 |
| `benchmark` | Macrobenchmark 与 Baseline Profile 工具 |

## 说明

Pixiv 未向本项目提供公开 API。客户端使用的协议可能发生变化或受到限制，因此登录、信息流、搜索或媒体访问等功能可能需要随服务端变化更新。请勿分享 refresh token，也不要将凭据写入源码、Issue 或日志。

版本记录见 [CHANGELOG.md](CHANGELOG.md)，第三方依赖声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 许可

Picaris 使用 [GNU GPL v3.0 或更高版本](LICENSE) 发布。Picaris 名称与应用图标不包含在许可授权范围内，详情见 [NOTICE](NOTICE)。Pixiv 及相关商标归其各自所有者所有。

## 致谢

Picaris 的界面和实现为独立创作。项目设计参考了 [Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft)、[MaterialFiles](https://github.com/zhanghai/MaterialFiles) 和 [EhViewer](https://github.com/FooIbar/EhViewer)，未包含这些项目的源代码、品牌素材或作品图片。
