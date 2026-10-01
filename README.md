# PixivNext

独立实现的 Android 17 / Material 3 Expressive Pixiv 客户端。包名 `io.github.pixivnext`，版本 `0.2.2`。只接受 Android 17（API 37）及以上，不包含旧系统兼容分支。

## 0.2.2

- 主页默认仅显示作品图片；设置 → 外观 →「显示作者与作品名」可恢复文字。设置持久保存，只改变呈现，不刷新主页分页。
- 喜欢底框缩小，并保留 48dp 点击范围；与作品页数角标统一圆角和内边距。
- 删除各页面顶部的说明性副标题及主页入口卡片的说明文字。
- 顶部标题栏统一使用紧凑的单行布局，上滑收起、回滚出现；每个页面与底栏目的地独立保存标题栏状态。
- QA 构建使用独立包名 `io.github.pixivnext.qa`、debug 签名及 R8/资源压缩，可与主包并行安装。

## 0.2.0 交互调整

- 主页分页会话保留在 ViewModel；从详情、搜索和底栏其它页面返回时复用已加载内容与滚动位置。手动下拉刷新、账号切换与内容过滤修改才开启新的主页加载。
- 作品卡片的 ❤️ 可直接收藏/取消收藏；列表和详情同步展示收藏状态与计数，不通过刷新主页来同步。
- 底栏依次为主页、发现、动态、搜索、我的；搜索为第四个页面，移除右上角搜索入口，搜索结果仍作为可返回的独立页面。
- 首页、发现、动态不保留空标题栏；列表初始避让状态栏，滚动时内容可延伸至透明状态栏后方。搜索主页面直接显示搜索框，不显示「搜索」标题，搜索框保留状态栏安全间距。
- 普通页面转场按类型匹配：设置、搜索、收藏等页面滑动进入和返回；作品详情、阅读器缩放进入和返回。预测性返回统一采用整页圆角卡片缩放预览，保持页面不透明，左侧手势略向右移、右侧手势略向左移；标题、图片与底部控件一起缩放、裁剪。取消手势沿原轨迹恢复，确认返回后才整页淡出。动效与圆角使用 MD3E 主题。
- 预测性预览在整个导航场景的最外层裁剪，手势期间明确使用主题大圆角，不依赖子页面的可见性动画；大屏双栏也使用同一个整页边界。
- 预测性返回的圆角与图片裁剪直接跟随 NavigationEvent 的真实手势状态，确认或取消后的收尾动画保留圆角，直到页面转场结束；不使用动画工具的 `isSeeking` 标志判断手势。
- 返回时底页面保持原尺寸与位置，不做整页缩放或平移；作品详情返回图片流时，仅对应作品的喜欢按钮产生局部涟漪反馈，不触发收藏操作。
- 全部页面导航与底栏切换由统一事务协调；页面实例身份随状态保存。导航提交即转移交互权，退出页仅由 GraphicsLayer 缓存绘制，控件、弹窗、焦点与旧触摸随退出撤销；无坐标转发或等待动画结束的交互锁。
- 图片与头像共享过渡使用独立绘制图层，退出页面撤销实际控件后仍可继续动画。快速重进、切换作品及连续底栏切换从当前位置与速度接续，过期动画回调不能清理新动画。
- 预测性返回开始时暂停当前共享图片动画，将其当前画面随整页缩放与圆角一起投影；确认或取消后从预览最后一帧接续。图片尺寸、可见裁剪边界与四角圆角分别连续变化，不恢复到未缩放尺寸或直角中间帧。
- 返回期间目标列表仍可滚动、点击收藏和切换底栏；共享图片跟随真实目标卡片更新位置，目标移出可见布局时淡出。绘制资源由动画生命周期释放，不用固定延时维持交互或共享元素。
- 导航和底栏的退出容器不保留 Surface 的触摸拦截层；共享图片使用单一帧循环采样当前位置与速度，再转向最新目标，不在每次目标坐标变化时取消和重建协程。原图绘制图层复用，页面运动结束前不提前释放共享图片。
- 进入或退出设置等二级页面时，仅前景页面滑动；底页和主页底栏保持原位置，不做跟随式平移。
- 入场样式由本次目标页面决定，设置、收藏、关注等普通页面始终滑动进入，包括返回动画中快速重新打开；只接续同尺寸滑动画面的位移和速度，不把返回预览的缩放变为新页面的缩放入场。预测性返回及手势取消恢复仍保留圆角缩放。
- 发现页排行榜下方直接展示 PIXIVISION 特辑轮播：读取简体中文官网最新特辑，显示封面、标题与分页圆点，支持左右滑动；点击卡片使用 Custom Tabs 阅读对应特辑。
- 标签使用接口提供的中文译名：推荐标签、搜索标签与作品标签显示译名和原标签双行，人气标签图片上显示原标签和译名。没有译名或译名与原文相同时仅显示原标签，点击搜索始终使用原文。
- 我的页面增加我的关注（公开/非公开）、粉丝、好 P 友和作品。用户列表支持分页与重试。
- 点详情图片直接打开原图/漫画/动图阅读器；「查看原图」移入右上角更多菜单。小说保留「开始阅读」。
- 详情显示评论预览，完整评论区支持分页、贴图、回复、正文选择、发表评论和错误重试。无回复评论直接打开回复输入框；已有回复打开回复列表。

## 已实现

- 官方网页 PKCE 登录、refresh token 导入、多账号切换与移除、并发请求的凭据刷新合并。登录凭据使用 Android Keystore AES-GCM 加密；备份和设备迁移排除应用数据。
- 插画、漫画、小说推荐；排行榜；关注动态；公开/非公开收藏；标签、作品与创作者搜索；日期、匹配方式和排序筛选。热度排序仅对 Premium 账号显示。
- 作品详情、标签跳转、作者页面、小说系列、相关插画、收藏/取消收藏、关注/取消关注、系统分享及作品链接入口。
- 原图缩放、多页横向翻阅/纵向阅读、动图 ZIP 帧时序播放；小说正文、章节、分页、插图、文字选择、字号与行距调整。
- 从图片流打开作品详情后，可左右滑动切换同一图片流的前后作品，并继续加载后续分页；返回保留原列表位置。
- Room 历史与阅读进度、搜索历史、分页缓存；按账号隔离。离线优先打开已下载的原图、小说文本和动图文件。
- Android User-Initiated Data Transfer 下载队列、通知、暂停/继续/取消/重试、带资源验证的 HTTP Range 续传；MediaStore 或 SAF 自选目录。动图同时保存 ZIP 与帧时序 JSON。
- 跟随系统/浅色/深色、动态配色、自定义种子色、阅读器纯黑背景；成人内容、AI 作品、标签和作者屏蔽；系统/直连/HTTP/SOCKS 网络配置。
- Navigation 3 自适应列表与详情双栏、手机底栏/大屏侧栏、系统返回手势、edge-to-edge、大字体布局。
- 内容浏览需要登录 Pixiv；已移除离线演示入口和模拟数据，保留真实内容缓存与已下载作品的离线读取。

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

`debug` 使用独立包名 `io.github.pixivnext.qa`，开发和设备测试不会覆盖已登录的客户端。`benchmark` 是启用 R8 与资源压缩、使用本机 debug 证书签名的非 debuggable 测试版，也是本次交付 APK 所用变体；同签名可直接升级 0.1.0，保留应用数据。`release` 不绑定私人签名配置，正式发布前需要自行配置发布密钥。不要使用测试签名发布到商店。

源码归档不含 `local.properties`、SDK、模拟器、Gradle 缓存、构建结果或用户凭据；首次构建需要下载依赖。

## Baseline Profile

在可 root 的 Android 17 AOSP/Google APIs 模拟器或支持的真机上运行：

```sh
./gradlew :benchmark:connectedBenchmarkAndroidTest -PgenerateProfile \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.pixivnext.benchmark.ClientBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

`-PgenerateProfile` 临时关闭目标 APK 的混淆，以采集能映射到下一次 R8 构建的原始类/方法规则。将输出的 `*-baseline-prof.txt` 放入 `app/src/main/baseline-prof.txt`，`*-startup-prof.txt` 放入 `app/src/main/startup-prof.txt`。正常构建时不传该参数，恢复 R8 和资源压缩。

冷启动/设置页滚动测试使用 `ClientBenchmark` 类运行；采集无需登录，设置页场景仅在未登录时执行。模拟器输出只能用于可执行性检查，不能代表真机性能。

## 登录与网络

推荐使用「网页登录」，通过 Custom Tabs 打开 Pixiv 的官方页面，完成后回调到应用。应用不收集密码。若设备装有其它处理 `pixiv://` 的客户端，系统可能要求选择 PixivNext；refresh token 导入可作为替代入口。

应用内代理用于 API、图片与下载；Custom Tabs 遵循浏览器/系统网络设置。不内置域名绕过、内置节点或第三方登录服务。请不要把 refresh token 发给别人、写入源码或日志。

Pixiv 没有为本项目授权公开 API。OAuth 与应用接口采用第三方客户端通用协议，服务端可能变更、限制或拒绝请求；网页小说格式发生变化时会显示错误，而不是静默显示空正文。使用时遵守 Pixiv 条款及作品作者的权限要求。

## 验证与边界

离线演示移除后，依赖模拟作品及本地评论的设备用例暂时停用，等待隔离 API 测试替身；状态栏、登录页设置和自有下载文件用例保留。以下 0.2.0 验证记录属于历史结果。

实际测试结果、设备信息与真实账号只读调试范围见交付目录的 `验证记录-0.2.0.md`。测试包含内容过滤、原图页序、小说 HTML 解析、并发凭据刷新、下载资源变化/续传范围/416 恢复、Room 账号隔离和暂停竞争，以及 Compose 页面流程。

本次使用已登录的 Android 17 真机，只读验证覆盖升级后登录保留、真实推荐、公开/非公开关注列表、评论正文与贴图读取，以及主页返回后的内容和位置保留。没有执行真实账号收藏/关注修改、发表评论或回复；这些写入流程使用隔离演示包和测试替身验证。全新网页登录、真实下载/动图播放、真实小说评论和已有回复的服务端列表仍未完整联调。预发布工具链的稳定性仍需长期使用验证。

当前创作者搜索仅加载服务端第一页；小说支持常见章节/分页/ruby/插图标记，复杂 jump/jumpuri 等标记未完整排版；下载小说导出文本，不打包小说插图；动图输出 ZIP + JSON，不导出视频。插图离线读取依赖 Coil 缓存或已保存文件。FANBOX/COMIC、通知中心、上传编辑、视频转码和其它扩展产品未包含在首版范围内。

## 模块

- `app`：Compose 页面、Hilt ViewModel、系统登录入口、UIDT 下载与维护任务。
- `core`：协议模型、Ktor/OkHttp、Keystore 凭据、Room/DataStore、Paging、小说解析和可验证续传。
- `designsystem`：MD3 Expressive 主题、动态图色、原创矢量图标。
- `benchmark`：冷启动/滚动 Macrobenchmark 和 Baseline Profile 采集。

## 设计参考与依赖

功能与布局参考 [Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft)，MD3 视觉参考 [MaterialFiles](https://github.com/zhanghai/MaterialFiles) 与 [FooIbar/EhViewer](https://github.com/FooIbar/EhViewer)。界面和图标均为本项目独立实现；没有复制参考仓库的 UI 源文件、品牌图或作品素材。依赖来源和许可证见 `THIRD_PARTY_NOTICES.md`。
