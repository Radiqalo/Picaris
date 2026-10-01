# Picaris

独立实现的 Android 17 / Material 3 Expressive Pixiv 客户端。包名 `io.github.radiqalo.picaris`，当前版本 `0.2.2`。

只接受 Android 17（API 37）及以上，不包含旧系统兼容分支；浏览内容需要登录 Pixiv 账号。

> Picaris 是非官方客户端，与 Pixiv Inc. 无任何关联。所有作品版权归其创作者所有，使用时请遵守 Pixiv 服务条款及作品作者的授权要求。

## 特性

### 浏览与发现

- 插画、漫画、小说推荐；排行榜；关注动态；公开与非公开收藏。
- 标签、作品与创作者搜索，支持日期、匹配方式和排序筛选；热度排序仅对 Premium 账号显示。
- 发现页排行榜下方展示 PIXIVISION 特辑轮播，读取简体中文官网最新特辑，点击后使用 Custom Tabs 阅读。
- 标签显示接口提供的中文译名：推荐标签、搜索标签与作品标签显示译名和原标签双行，人气标签图片上同时显示原标签与译名；没有译名或译名与原文相同时仅显示原标签，点击搜索始终使用原文。可在设置 → 外观关闭。
- 作品详情、标签跳转、作者页面、小说系列、相关插画、收藏/取消收藏、关注/取消关注、系统分享及作品链接入口。
- 我的页面包含我的关注（公开/非公开）、粉丝、好 P 友和作品，用户列表支持分页与重试。

### 阅读器

- 插画与漫画：原图缩放、多页横向翻阅或纵向阅读；动图按 ZIP 帧时序播放。
- 小说：正文、章节、分页、插图、文字选择、字号与行距调整。
- 从图片流打开作品详情后，可左右滑动切换同一图片流的前后作品并继续加载后续分页；返回保留原列表位置。

### 详情与评论

- 插画和漫画详情使用单页纵向布局：首图在首屏图片区居中，标题、作者、标签、简介、评论入口及相关作品依次接在图片后面；多 P 默认显示首图，可原地展开全部图片或收起，首图保留共享元素。
- 点详情图片直接打开原图／漫画／动图阅读器，「查看原图」移入右上角更多菜单；小说保留「开始阅读」。
- 详情显示评论预览，完整评论区支持分页、贴图、回复、正文选择、发表评论和错误重试。无回复的评论直接打开回复输入框，已有回复的打开回复列表。

### 下载与离线

- Android User-Initiated Data Transfer 下载队列与通知，支持暂停／继续／取消／重试，以及带资源验证的 HTTP Range 续传。
- 可通过 MediaStore 或 SAF 自选目录保存；动图同时保存 ZIP 与帧时序 JSON。
- 下载管理支持按下载中、暂停、失败、完成和取消筛选，多选及当前筛选全选，批量暂停、继续／重试、取消和确认清理终态记录；清理记录不删除已保存文件，但会移除应用内离线关联。显示已传输／总大小及暂停、失败时的进度，未知大小使用不定进度。
- Room 历史与阅读进度、搜索历史、分页缓存；按账号隔离。离线优先打开已下载的原图、小说文本和动图文件。
- 主页分页会话保留在 ViewModel；从详情、搜索和底栏其它页面返回时复用已加载内容与滚动位置，手动下拉刷新、账号切换与内容过滤修改才开启新的主页加载。
- 作品卡片的 ❤️ 可直接收藏／取消收藏；列表和详情同步展示收藏状态与计数，不通过刷新主页来同步。

### 外观与交互

- 跟随系统／浅色／深色、动态配色、自定义种子色、阅读器纯黑背景。
- 界面图标统一使用 Google Material Symbols Rounded 官方矢量资源，收藏与底栏选中态保留空心／实心区分，返回与方向箭头支持 RTL 镜像。
- 标准底栏，或设置 → 外观开启 Floating 底栏：使用原生 `HorizontalFloatingToolbar` 与横向 `ShortNavigationBarItem`，选中胶囊高度弹簧变化，文字展开／收起并滑动渐变，容器随内容自然伸缩，连续点击从当前动画状态接续；避让系统导航区域，大屏仍使用侧栏。
- 顶部标题栏统一使用紧凑的单行布局，上滑收起、回滚出现；每个页面与底栏目的地独立保存标题栏状态。
- 设置 → 外观 →「显示标签翻译」「显示作者与作品名」只改变呈现，不刷新图片流或改变搜索词，设置持久保存。
- 成人内容、AI 作品、标签和作者屏蔽；系统／直连／HTTP／SOCKS 网络配置。

### 导航与动效

- Navigation 3 自适应布局：手机底栏、大屏侧栏，以及列表与详情双栏；支持系统返回手势、edge-to-edge 与大字体。
- 全部页面导航与底栏切换由统一事务协调，页面实例身份随状态保存；导航提交即转移交互权，退出页仅由 GraphicsLayer 缓存绘制，控件、弹窗、焦点与旧触摸随退出撤销，无坐标转发或等待动画结束的交互锁。
- 预测性返回采用整页圆角卡片缩放预览，保持页面不透明，左侧手势略向右移、右侧手势略向左移；取消手势沿原轨迹恢复，确认返回后才整页淡出。
- 图片与头像共享过渡使用独立绘制图层，退出页面撤销实际控件后仍可继续动画；快速重进、切换作品及连续底栏切换从当前位置与速度接续，过期动画回调不能清理新动画。
- 返回时底页面保持原尺寸与位置，不做整页缩放或平移；作品详情返回图片流时仅对应作品的喜欢按钮产生局部涟漪反馈，不触发收藏操作。
- 返回期间目标列表仍可滚动、点击收藏和切换底栏；共享图片跟随真实目标卡片更新位置，目标移出可见布局时淡出。绘制资源由动画生命周期释放，不用固定延时维持交互或共享元素。
- 动效统一取自 MD3E 主题的 `MotionScheme`，并遵循系统的动画时长缩放设置（开启「移除动画」时退化为瞬时切换）。

### 账号

- 官方网页 PKCE 登录、refresh token 导入、多账号切换与移除。
- 并发请求的凭据刷新合并；登录凭据使用 Android Keystore AES-GCM 加密。
- 备份和设备迁移排除应用数据。

## 技术版本

版本锁定在 `gradle/libs.versions.toml`；预发布组件按用户要求启用。

| 组件 | 版本 |
| --- | --- |
| SDK | compile 37.2 / min 37 / target 37 |
| Gradle / Android Gradle Plugin | 9.8.0 / 9.5.0-alpha07 |
| Kotlin / KSP / Hilt | 2.4.20 / 2.3.12 / 2.60.1 |
| Compose BOM alpha / Material 3 | 2026.09.01 / 1.5.0-alpha29 |
| Navigation 3 / adaptive-navigation3 | 1.3.0-alpha01 / 1.4.0-alpha02 |
| Activity / Lifecycle | 1.14.0-alpha03 / 2.12.0-alpha04 |
| Room / DataStore / Paging | 2.8.5 / 1.3.0-alpha11 / 3.5.1 |
| Ktor / OkHttp / Coil / Telephoto | 3.6.0 / 5.5.0 / 3.6.3 / 0.19.0 |
| MaterialKolor / 色彩规范 | 5.0.1 / MD3 Expressive SPEC_2025 |
| WorkManager / Macrobenchmark | 2.12.0 / 1.5.0 |

Kotlin 2.5.0-Beta1 已尝试，但 Hilt 2.60.1 的元数据读取器拒绝 Kotlin 2.5 元数据；因此使用最新已验证兼容的 Kotlin 2.4.20。Compose 最新 alpha 要求 compile SDK 至少 37.1，因此使用 37.2，而运行最低版本仍为 Android 17 API 37。

## 构建

安装支持该 AGP 的 Android Studio、Android SDK Platform 37.2、Platform Tools 和 Build Tools。最终构建及 Lint 使用 Android Studio JBR 25；源码字节码目标为 JVM 21。早期构建也曾在 JDK 27 下通过，但预发布 Lint 出现过 FIR 分析会话错误，因此推荐 JBR 25。工程默认关闭 Gradle 项目并行，串行完整检查已通过。

在项目根目录创建本机 `local.properties`：

```properties
sdk.dir=/your/path/to/Android/Sdk
```

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :core:testDebugUnitTest :designsystem:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleBenchmark
```

`debug` 使用独立包名 `io.github.radiqalo.picaris.qa`，开发和设备测试不会覆盖主包。`benchmark` 是启用 R8 与资源压缩、使用本机 debug 证书签名的非 debuggable 测试版，也是交付 APK 所用变体。`release` 不绑定私人签名配置，正式发布前需要自行配置发布密钥；不要使用测试签名发布到商店。

源码归档不含 `local.properties`、SDK、模拟器、Gradle 缓存、构建结果或用户凭据；首次构建需要下载依赖。

## Baseline Profile

在可 root 的 Android 17 AOSP/Google APIs 模拟器或支持的真机上运行：

```sh
./gradlew :benchmark:connectedBenchmarkAndroidTest -PgenerateProfile \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.radiqalo.picaris.benchmark.ClientBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

`-PgenerateProfile` 临时关闭目标 APK 的混淆，以采集能映射到下一次 R8 构建的原始类/方法规则。将输出的 `*-baseline-prof.txt` 放入 `app/src/main/baseline-prof.txt`，`*-startup-prof.txt` 放入 `app/src/main/startup-prof.txt`。正常构建时不传该参数，恢复 R8 和资源压缩。

冷启动/设置页滚动测试使用 `ClientBenchmark` 类运行；采集无需登录，设置页场景仅在未登录时执行。模拟器输出只能用于可执行性检查，不能代表真机性能。

## 登录与网络

推荐使用「网页登录」，通过 Custom Tabs 打开 Pixiv 的官方页面，完成后回调到应用。应用不收集密码。若设备装有其它处理 `pixiv://` 的客户端，系统可能要求选择 Picaris；refresh token 导入可作为替代入口。

应用内代理用于 API、图片与下载；Custom Tabs 遵循浏览器/系统网络设置。不内置域名绕过、内置节点或第三方登录服务。请不要把 refresh token 发给别人、写入源码或日志。

Pixiv 没有为本项目授权公开 API。OAuth 与应用接口采用第三方客户端通用协议，服务端可能变更、限制或拒绝请求；网页小说格式发生变化时会显示错误，而不是静默显示空正文。使用时遵守 Pixiv 条款及作品作者的权限要求。

## 模块

- `app`：Compose 页面、Hilt ViewModel、系统登录入口、UIDT 下载与维护任务。
- `core`：协议模型、Ktor/OkHttp、Keystore 凭据、Room/DataStore、Paging、小说解析和可验证续传。
- `designsystem`：MD3 Expressive 主题、动态图色、原创矢量图标。
- `benchmark`：冷启动/滚动 Macrobenchmark 和 Baseline Profile 采集。

## 边界与未实现

- 离线演示已移除；依赖模拟作品与本地评论的设备用例暂时停用，等待隔离 API 测试替身，状态栏、登录页设置和自有下载文件用例保留。
- 创作者搜索仅加载服务端第一页。
- 小说支持常见章节／分页／ruby／插图标记，复杂 `jump`／`jumpuri` 等标记未完整排版；下载小说导出文本，不打包小说插图。
- 动图输出 ZIP + JSON，不导出视频。插图离线读取依赖 Coil 缓存或已保存文件。
- 全新网页登录、真实下载／动图播放、真实小说评论和已有回复的服务端列表尚未完整联调；预发布工具链的稳定性仍需长期使用验证。
- FANBOX/COMIC、通知中心、上传编辑、视频转码和其它扩展产品未包含在范围内。

## 设计参考与依赖

功能与布局参考 [Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft)，MD3 视觉参考 [MaterialFiles](https://github.com/zhanghai/MaterialFiles) 与 [FooIbar/EhViewer](https://github.com/FooIbar/EhViewer)。界面和图标均为本项目独立实现；没有复制参考仓库的 UI 源文件、品牌图或作品素材。依赖来源和许可证见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

## 许可

本项目以 [GNU General Public License v3.0](LICENSE) 发布，版权归 `Copyright (C) 2026 Radiqalo`；声明见 [`NOTICE`](NOTICE)。

这是 **GPL-3.0-only**（不含 "or any later version"）。以目标代码形式分发时，对应源码见 <https://github.com/Radiqalo/Picaris>；GPL-3.0 要求分发者向接收者提供完整许可文本与对应源码，二者均已随本仓库和 APK 提供。

「Picaris」名称与应用图标不在许可授权范围内，请不要在衍生分发中使用，以免与官方版本混淆。Pixiv、PIXIVISION 等名称与商标归其各自所有者。

第三方依赖的许可证与声明见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)，随源码归档的许可文本位于 [`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt)。

版本变更记录见 [`CHANGELOG.md`](CHANGELOG.md)。
