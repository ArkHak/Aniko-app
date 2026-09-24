# Third-party notices

Aniko is licensed under the [GNU GPL v3.0](LICENSE). It builds on the open-source components below,
each of which remains under its own license. All of them are compatible with GPL-3.0.

## Runtime libraries

| Component | License |
|---|---|
| [Kotlin](https://kotlinlang.org), kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform), Skiko, JetBrains AndroidX ports | Apache-2.0 |
| AndroidX (Activity, Lifecycle, Navigation, WorkManager, Security Crypto, …) | Apache-2.0 |
| [Ktor](https://ktor.io), OkHttp | Apache-2.0 |
| [Koin](https://insert-koin.io) | Apache-2.0 |
| [SQLDelight](https://sqldelight.github.io/sqldelight/), [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) | Apache-2.0 |
| [Coil](https://coil-kt.github.io/coil/) | Apache-2.0 |
| [Multiplatform Settings](https://github.com/russhwolf/multiplatform-settings) | Apache-2.0 |
| [Lyricist](https://github.com/adrielcafe/lyricist) | MIT |
| [vlcj](https://github.com/caprica/vlcj), vlcj-natives (Desktop only) | GPL-3.0 |
| [JNA](https://github.com/java-native-access/jna) (via vlcj) | Apache-2.0 / LGPL-2.1 |

The Desktop build talks to a **separately installed** [VLC](https://www.videolan.org/vlc/) (libVLC,
LGPL-2.1+) through vlcj; libVLC is not bundled with Aniko. vlcj is licensed under GPL-3.0, which is
why the project as a whole is released under GPL-3.0.

## Fonts

Bundled in `shared/ui/src/commonMain/composeResources/font/`; license texts are in
[`shared/ui/licenses/`](shared/ui/licenses).

| Font | License | Text |
|---|---|---|
| [Manrope](https://github.com/sharanda/manrope) | SIL OFL 1.1 | [`OFL-Manrope.txt`](shared/ui/licenses/OFL-Manrope.txt) |
| [Inter](https://github.com/rsms/inter) | SIL OFL 1.1 | [`OFL-Inter.txt`](shared/ui/licenses/OFL-Inter.txt) |
| [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) | SIL OFL 1.1 | [`OFL-JetBrainsMono.txt`](shared/ui/licenses/OFL-JetBrainsMono.txt) |
| [Material Symbols](https://github.com/google/material-design-icons) | Apache-2.0 | [`Apache-2.0-MaterialSymbols.txt`](shared/ui/licenses/Apache-2.0-MaterialSymbols.txt) |

## Build tooling (not distributed)

Gradle, Android Gradle Plugin, ktlint, detekt — Apache-2.0 / MIT.

## Trademarks and content

"Anixart" and its logos belong to their respective owners; Aniko is an unofficial client and is not
affiliated with them. All video, images and descriptions shown in the app come from Anixart's servers
and third-party video hosts and belong to their rights holders. See the *Legal* section of the
[README](README_EN.md#-legal).
