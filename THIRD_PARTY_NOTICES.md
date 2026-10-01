# Third-party notices

Picaris uses the following libraries through Gradle. Versions and the complete resolved dependency graph can be inspected with `./gradlew :app:dependencies --configuration benchmarkRuntimeClasspath`.

| Dependency family | Project / license |
| --- | --- |
| AndroidX Compose, Material 3, Adaptive, Navigation 3, Activity, Lifecycle, Room, DataStore, Paging, Browser, DocumentFile, WorkManager, ProfileInstaller, Benchmark and AndroidX Test | [AndroidX](https://android.googlesource.com/platform/frameworks/support/), Apache-2.0 |
| Kotlin, kotlinx.coroutines, kotlinx.serialization, Ktor | [JetBrains Kotlin](https://github.com/JetBrains/kotlin), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization), [Ktor](https://github.com/ktorio/ktor), Apache-2.0 |
| Dagger / Hilt | [Dagger](https://github.com/google/dagger), Apache-2.0 |
| OkHttp and MockWebServer | [OkHttp](https://github.com/square/okhttp), Apache-2.0 |
| Okio (transitive) | [Okio](https://github.com/square/okio), Apache-2.0 |
| Coil | [Coil](https://github.com/coil-kt/coil), Apache-2.0 |
| Material Symbols Rounded vector assets (navigation, search, favorite outline/filled, comments, download, settings, history, share, media controls and appearance icons) | [Google Material Design Icons](https://github.com/google/material-design-icons), Apache-2.0; original path data preserved |
| MaterialKolor / Material Color Utilities | [MaterialKolor](https://github.com/jordond/MaterialKolor), Apache-2.0 (Kotlin port of Google Material Color Utilities) |
| Telephoto | [Telephoto](https://github.com/saket/telephoto), Apache-2.0 |
| JUnit 4 (tests only) | [JUnit 4](https://github.com/junit-team/junit4), EPL-1.0 |
| Gradle Wrapper and Android Gradle Plugin (build tooling) | [Gradle](https://github.com/gradle/gradle), [Android tools](https://android.googlesource.com/platform/tools/base/), Apache-2.0 |
| Kotlin Symbol Processing (build tooling) | [KSP](https://github.com/google/ksp), Apache-2.0 |

Upstream license terms and notices remain applicable to the bundled dependencies. The source archive includes the Apache-2.0 license text used by the dependency families above in `licenses/Apache-2.0.txt`.

The Picaris project itself is released under the GNU General Public License v3.0 (only); see `LICENSE` for the license text and `NOTICE` for the copyright notice. That license covers the project's own code; the Apache-2.0 texts under `licenses/` and the bundled assets apply to the third-party dependencies listed above.

Reference projects are identified for design/function attribution only: [CeuiLiSA/Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft), [zhanghai/MaterialFiles](https://github.com/zhanghai/MaterialFiles), [FooIbar/EhViewer](https://github.com/FooIbar/EhViewer). Their source files were not incorporated into this project. Pixiv names, services and user-uploaded works belong to their respective rights holders.
