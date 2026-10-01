# Picaris

**An independent Pixiv client for Android, designed with Material 3 Expressive.**

[![Android](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/11)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)

[简体中文](README.md)

Picaris is an independently developed, open-source Pixiv client for browsing and reading illustrations, manga, and novels. It supports Android 11 (API 30) and newer. A Pixiv account is required to browse content.

> Picaris is not affiliated with or endorsed by Pixiv Inc. Artwork remains the property of its creators. Follow Pixiv's terms of service and respect each creator's usage permissions.

## Features

- **Browse and discover** illustrations, manga, novels, rankings, followed creators, tags, and related works.
- **Search** works, creators, and tags; translate supported tags while preserving the original search term.
- **Read** multi-page works horizontally or vertically, zoom original images, play animated works, and adjust novel typography.
- **Download for offline use** with a queue, configurable concurrency, pause/resume/retry, progress, and list or gallery views. Animated works can be saved as GIFs or as source ZIP files to a chosen folder.
- **Manage content** with favorites, browsing history, followed series, and downloaded works.
- **Personalize** themes, seed colors, navigation bar style, content filters, and network settings.
- **Protect account data** with encrypted credential storage and multi-account support.

## Get started

### Requirements

- Android Studio compatible with the current toolchain (JDK 25 recommended).
- Android SDK Platform 37.2 and corresponding Build Tools.
- An Android 11 (API 30) or newer device or emulator.

### Build

Clone the repository and configure the Android SDK path in `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

Build the debug app:

```sh
./gradlew :app:assembleDebug
```

Run unit tests:

```sh
./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :designsystem:testDebugUnitTest
```

The debug build uses the separate application ID `io.github.radiqalo.picaris.qa`, so it can be installed alongside the regular app. Release builds require your own signing configuration.

## Project structure

| Module | Purpose |
| --- | --- |
| `app` | Android app, Compose screens, account flows, and download service |
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
