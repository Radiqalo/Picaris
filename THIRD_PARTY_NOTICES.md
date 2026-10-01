# Third-party notices

Picaris uses the following libraries through Gradle. Versions and the complete resolved dependency graph can be inspected with `./gradlew :app:dependencies --configuration benchmarkRuntimeClasspath`.

## Direct dependencies

| Dependency family | Project / license |
| --- | --- |
| AndroidX Compose, Material 3, Adaptive, Navigation 3, Activity, Lifecycle, Room, DataStore, Paging, Browser, DocumentFile, WorkManager, ProfileInstaller and AndroidX Test | [AndroidX](https://android.googlesource.com/platform/frameworks/support/), Apache-2.0 |
| Kotlin, kotlinx.coroutines, kotlinx.serialization, Ktor | [JetBrains Kotlin](https://github.com/JetBrains/kotlin), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization), [Ktor](https://github.com/ktorio/ktor), Apache-2.0 |
| Dagger / Hilt | [Dagger](https://github.com/google/dagger), Apache-2.0 |
| OkHttp | [OkHttp](https://github.com/square/okhttp), Apache-2.0 |
| Coil | [Coil](https://github.com/coil-kt/coil), Apache-2.0 |
| Telephoto | [Telephoto](https://github.com/saket/telephoto), Apache-2.0 |
| MaterialKolor | [MaterialKolor](https://github.com/jordond/MaterialKolor), **MIT**. The Kotlin port is MIT; the Google project it ports, [material-color-utilities](https://github.com/material-foundation/material-color-utilities), is Apache-2.0. |

## Transitive dependencies

| Dependency | License |
| --- | --- |
| Okio | Apache-2.0 |
| Jakarta Dependency Injection and javax.inject (via Dagger) | Apache-2.0 |
| Guava listenablefuture, JSR-305 annotations and JSpecify | Apache-2.0 |
| Accompanist Drawable Painter (via Coil) | Apache-2.0 |
| Poko annotations (via Telephoto) | Apache-2.0 |
| colormath (via MaterialKolor) | MIT |
| SLF4J (via Ktor) | MIT |

## Bundled assets

| Asset | Source / license |
| --- | --- |
| Material Symbols Rounded vector assets (navigation, search, favorite outline/filled, comments, download, settings, history, share, media controls and appearance icons) | [Google Material Design Icons](https://github.com/google/material-design-icons), Apache-2.0; original path data preserved |

## Build tooling and tests

These entries are not shipped in the application package.

| Dependency | License |
| --- | --- |
| Gradle Wrapper and Android Gradle Plugin | [Gradle](https://github.com/gradle/gradle), [Android tools](https://android.googlesource.com/platform/tools/base/), Apache-2.0 |
| Kotlin Symbol Processing (KSP) | [KSP](https://github.com/google/ksp), Apache-2.0 |
| MockWebServer | [OkHttp](https://github.com/square/okhttp), Apache-2.0 |
| JUnit 4 | [JUnit 4](https://github.com/junit-team/junit4), EPL-1.0 |
| Hamcrest | [Hamcrest](https://github.com/hamcrest/JavaHamcrest), BSD-3-Clause |

## License texts

Upstream license terms and notices remain applicable to the bundled dependencies. The full texts ship with this project:

- `licenses/Apache-2.0.txt` — Apache-2.0, covering the AndroidX, Kotlin, Ktor, Dagger, OkHttp, Okio, Coil, Telephoto, Accompanist, Poko and remaining transitive families listed above.
- `licenses/MIT.txt` — MIT, covering MaterialKolor, colormath and SLF4J, with each component's copyright notice.
- `LICENSE` — the Picaris project's own license, GNU GPL v3.0 (only).

## Project license

The Picaris project itself is released under the GNU General Public License v3.0 (only); see `LICENSE` for the license text and `NOTICE` for the copyright notice. That license covers the project's own code only; the texts above apply to the third-party dependencies.

Reference projects are identified for design/function attribution only: [CeuiLiSA/Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft), [zhanghai/MaterialFiles](https://github.com/zhanghai/MaterialFiles), [FooIbar/EhViewer](https://github.com/FooIbar/EhViewer). Their source files were not incorporated into this project. Pixiv names, services and user-uploaded works belong to their respective rights holders.
