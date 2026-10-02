# Picaris

**An independent Pixiv client for Android, designed with Material 3 Expressive.**

[![Android](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/11)
[![Release](https://img.shields.io/github/v/release/Radiqalo/Picaris?label=release)](https://github.com/Radiqalo/Picaris/releases/latest)
[![License](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)

[简体中文](README.md)

Picaris is an independently developed, open-source Pixiv client for browsing and reading illustrations, manga, and novels. It requires Android 11 (API 30) or newer, and a Pixiv account to browse content.

> Not affiliated with Pixiv Inc. Artwork remains the property of its creators. Follow Pixiv's terms of service and respect each creator's usage permissions.

## Download and install

**[Get the latest version from the Releases page →](https://github.com/Radiqalo/Picaris/releases/latest)**

| Package | Target devices |
| --- | --- |
| `arm64-v8a` | Most phones from the last several years |
| `armeabi-v7a` | Older 32-bit devices |
| `x86_64` | Emulators and some tablets |
| `universal` | Use when unsure; largest download |

The first install requires allowing installs from that source in system settings. The app is not published on any app store, so new versions appear only on the Releases page — follow this repository's release notifications to be notified.

## Features

- **Browse and discover** illustrations, manga, novels, rankings, followed creators, followed series, related works, and PIXIVISION articles.
- **Search** works, creators, and tags; translate supported tags while preserving the original search term.
- **Read** multi-page works horizontally or vertically, zoom original images, play animated works, and adjust novel typography.
- **Download for offline use** with a queue, configurable concurrency, pause/resume/retry, progress, and list or gallery views.
- **Manage content** with favorites, browsing history, followed series, and downloaded works.
- **Personalize** themes, seed colors, navigation bar style, content filters, and network settings.
- **Protect account data** with encrypted credential storage, multi-account support, and private bookmarks.

## FAQ

**How do I update?**
There is no in-app updater. Check the [Releases page](https://github.com/Radiqalo/Picaris/releases/latest) and install the new version.

**Login fails, or lists and images stop loading.**
First check whether a newer version is available — server-side changes on Pixiv can break older versions. If you are already on the latest version, check your network and proxy settings.

**Can I use a proxy?**
Yes. Configure an HTTP or SOCKS proxy in settings; the API, images, and downloads all use it. Web login happens in the system browser, which needs its own access to Pixiv.

**Where are downloaded works saved?**
By default in the system pictures or downloads folder; you can pick another folder in settings. Animated works can be saved as GIFs or as source ZIP files.

## Privacy

- Login credentials are encrypted with AES/GCM using the Android Keystore and are stored only on your device.
- The project contains no analytics, telemetry, or crash-reporting SDK.
- The app's own network requests go only to Pixiv domains (`app-api.pixiv.net`, `oauth.secure.pixiv.net`, `www.pixiv.net`, `www.pixivision.net`) and the artwork image domain (`*.pximg.net`).

## Development

You need Android Studio (JDK 25 or newer), Android SDK Platform 37.2, and `sdk.dir` configured in `local.properties`.

```sh
./gradlew :app:assembleDebug

./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :designsystem:testDebugUnitTest
./gradlew ktlintCheck detekt
./gradlew :app:lintDebug
```

The debug and `qa` builds use separate application IDs (`io.github.radiqalo.picaris.debug` / `.qa`) and can be installed alongside the regular app. Existing ktlint and detekt findings are frozen in per-module baselines, so new findings fail the check.

| Module | Purpose |
| --- | --- |
| `app` | Android app, Compose screens, navigation, account flows, and download service |
| `core` | Pixiv protocols, persistence, paging, and domain logic |
| `designsystem` | Material 3 Expressive theme and shared UI resources |
| `benchmark` | Macrobenchmark and Baseline Profile tools |

## Notes

Pixiv does not provide a public API for this project. The client protocols may change or be restricted, so login, feeds, search, or media access may require updates when the service changes. Do not share refresh tokens or include credentials in source code, issues, or logs.

See [CHANGELOG.md](CHANGELOG.md) for release notes and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency acknowledgements.

## License

Picaris is licensed under [GNU GPL v3.0 or later](LICENSE). The Picaris name and app icon are not included in that license; see [NOTICE](NOTICE). Pixiv and related marks belong to their respective owners.

## Acknowledgements

Picaris's UI and implementation are original. The project takes inspiration from [Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft), [MaterialFiles](https://github.com/zhanghai/MaterialFiles), and [EhViewer](https://github.com/FooIbar/EhViewer). No source code, branding, or artwork from those projects is included.
