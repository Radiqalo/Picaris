# Picaris

**一款采用 Material 3 Expressive 设计的 Pixiv Android 客户端。**

[![Android](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/11)
[![Release](https://img.shields.io/github/v/release/Radiqalo/Picaris?label=release)](https://github.com/Radiqalo/Picaris/releases/latest)
[![License](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)

[English](README.en.md)

Picaris 是独立开发的开源 Pixiv 客户端，支持浏览和阅读插画、漫画与小说。需要 Android 11（API 30）及以上版本，浏览内容需要登录 Pixiv 账号。

> 非官方客户端，与 Pixiv Inc. 无关联。作品版权归创作者所有，请遵守 Pixiv 服务条款及作者的使用许可。

## 下载与安装

**[前往 Releases 页面下载最新版本 →](https://github.com/Radiqalo/Picaris/releases/latest)**

| 安装包 | 适用设备 |
| --- | --- |
| `arm64-v8a` | 近几年的绝大多数手机 |
| `armeabi-v7a` | 较旧的 32 位设备 |
| `x86_64` | 模拟器、部分平板 |
| `universal` | 不确定时使用，体积较大 |

首次安装需在系统设置中允许来自该来源的安装。应用不上架应用商店，新版本只发布在 Releases 页面，可订阅本仓库的 release 通知。

## 功能

- **浏览与发现**：插画、漫画、小说、排行榜、关注动态、追更系列、相关作品与 PIXIVISION 特辑。
- **搜索**：搜索作品、创作者和标签；支持标签翻译，同时保留原始搜索词。
- **阅读**：多页作品横向翻页或纵向阅读、原图缩放、动图播放，以及小说排版调整。
- **离线下载**：下载队列、并发数设置、暂停/继续/重试、进度显示，以及列表和图片流视图。
- **内容管理**：收藏、浏览历史、追更系列和下载作品。
- **个性化**：主题、种子颜色、导航栏样式、内容过滤与网络设置。
- **账号保护**：登录凭据加密存储，支持多账号切换与私人收藏。

## 常见问题

**如何更新？**
应用不提供应用内更新，请到 [Releases 页面](https://github.com/Radiqalo/Picaris/releases/latest) 查看并下载新版本。

**登录失败，或列表、图片加载不出来？**
先确认是否已有新版本——Pixiv 的服务端变更可能导致旧版本失效。若已是最新版本，请检查网络与代理设置。

**可以使用代理吗？**
可以。在设置中配置 HTTP 或 SOCKS 代理，API、图片和下载都会走该代理。网页登录由系统浏览器完成，需要浏览器自身也能访问 Pixiv。

**下载的作品保存在哪里？**
默认保存到系统的图片或下载目录，也可以在设置中指定其他目录。动图可保存为 GIF，或选择目录保存源 ZIP。

## 隐私

- 登录凭据经 Android Keystore 的 AES/GCM 加密后仅存本机。
- 不含统计、埋点或崩溃上报 SDK。
- 应用自身的网络请求只发往 Pixiv 域名（`app-api.pixiv.net`、`oauth.secure.pixiv.net`、`www.pixiv.net`、`www.pixivision.net`）与图片域名（`*.pximg.net`）。

## 开发

需要 Android Studio（JDK 25 或更高）与 Android SDK Platform 37.2，并在 `local.properties` 中配置 `sdk.dir`。

```sh
./gradlew :app:assembleDebug

./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :designsystem:testDebugUnitTest
./gradlew ktlintCheck detekt
./gradlew :app:lintDebug
```

Debug 与 `qa` 均使用独立包名（`io.github.radiqalo.picaris.debug` / `.qa`），可与正式版并行安装。ktlint 与 detekt 的既有问题由 baseline 冻结，新增问题会导致检查失败。

| 模块 | 用途 |
| --- | --- |
| `app` | Android 应用、Compose 页面、导航、账号流程与下载服务 |
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
