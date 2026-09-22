<p align="center">
  <img src="docs/icon.png" width="128" alt="Aniko" />
</p>

<h1 align="center">Aniko</h1>

<p align="center">
  Unofficial multiplatform client for <a href="https://anixart.tv">Anixart</a><br/>
  Kotlin Multiplatform + Compose Multiplatform · single codebase
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-0.0.2--alpha-8B6FF0" />
  <img alt="Platforms" src="https://img.shields.io/badge/platforms-Android%20·%20iOS%20·%20macOS-0A0C12" />
  <img alt="Stack" src="https://img.shields.io/badge/stack-Kotlin%20Multiplatform%20·%20CMP-7F52FF" />
</p>

<p align="center">
  <a href="README.md">🇷🇺 Русский</a> · <b>🇬🇧 English</b>
</p>

---

> **This project was originally built for the iOS version.** There is no official Anixart client for
> iPhone — Aniko fills exactly that gap, and the author uses it daily on his own iPhone. A convenient
> way to distribute the iOS build to everyone is still being figured out (for now it's side-loading
> with your own Apple ID, see «[Installation](#-installation)»), but the iOS version itself is fully
> functional. Android and Desktop came along "for free" thanks to the shared codebase — and they are
> full-fledged clients, not stubs.

> [!NOTE]
> **Status: alpha (`v0.0.2`).** The project is under active development: some Anixart features are
> not implemented yet (see "what's already implemented" in [`docs/api/ANIXART_API.md`](docs/api/ANIXART_API.md)),
> bugs, breaking changes and loss of local data between versions are possible.

## 📸 Screenshots

**iOS (iPhone)**

| Home | Catalog | Release details |
|---|---|---|
| ![Home, iOS](docs/screenshots/ios-home.png) | ![Catalog, iOS](docs/screenshots/ios-catalog.png) | ![Release details, iOS](docs/screenshots/ios-release.png) |

**Desktop (macOS)**

| Home | Catalog | Release details |
|---|---|---|
| ![Home](docs/screenshots/desktop-home.png) | ![Catalog](docs/screenshots/desktop-catalog.png) | ![Release details](docs/screenshots/desktop-release.png) |

| Schedule | My lists |
|---|---|
| ![Schedule](docs/screenshots/desktop-schedule.png) | ![My lists](docs/screenshots/desktop-library.png) |

## ✨ Features

- **Catalog & search** — filters by genre, year, status, type; "All / New" tabs
- **Release page** — description, genres, rating histogram, related titles, comments, voting
- **Player** — voice-over and source selection, resume dialog, seek gestures, quality switching
  (Desktop), PiP (Android)
- **My lists** — watching / planned / completed / on-hold / dropped, favorites, watch history
- **Profile** — watch statistics, activity charts, achievements, privacy settings
- **Sync** — with your official Anixart account (same token as the original app), offline action
  queue, background notifications about new episodes
- **Adaptive UI** — phone / tablet / desktop layouts, themes, RU/EN localization

## 🛠 Tech stack

| | |
|---|---|
| **Language / UI** | Kotlin Multiplatform, Compose Multiplatform (Android · iOS · Desktop) |
| **Networking** | Ktor, kotlinx.serialization |
| **DI / storage** | Koin, SQLDelight, Multiplatform Settings |
| **Images** | Coil |

| Module | Purpose |
|---|---|
| `shared:model` | Shared domain models |
| `shared:network` | HTTP client, API configuration, error handling |
| `shared:data` | Repositories, DTOs, API interfaces (`shared/data/.../api`) |
| `shared:player` | Video player (embed/iframe sources) |
| `shared:ui` | Shared components, theme, i18n, image handling |
| `composeApp` | Platform entry points (Android / iOS framework / Desktop), screens and navigation |

Full description of the Anixart API (endpoints, models, authentication) —
in [`docs/api/ANIXART_API.md`](docs/api/ANIXART_API.md) (in Russian).

## 📦 Installation

### Android

1. Download the `.apk` from the [Releases](https://github.com/ArkHak/Aniko/releases) page.
2. Allow installation from unknown sources (Settings → Apps → Special access → Install unknown
   apps — for the browser/file manager you open the APK with).
3. Open the downloaded `.apk` and install.

No developer account / Google Play required — Android allows installing APKs signed with any key.

### iOS (iPhone) — without a paid Apple Developer account

Apple **always** requires the app to be signed with some Apple ID, even for side-loading — this is an
iOS limitation. "Without a developer account" means **without the paid Apple Developer Program
($99/year)**: a regular free Apple ID (the same one used for iCloud/App Store) is enough. Its
limitations: the signing certificate lasts **7 days** (then re-signing is needed), and there's a cap
on simultaneously signed apps.

<details>
<summary><b>Option 1. Direct install via Xcode</b> — simplest, but the phone must be connected to a Mac periodically</summary>

Requirements: a Mac with Xcode, a free Apple ID, a cable (or Wi-Fi debugging).

1. Install [Xcode](https://apps.apple.com/app/xcode/id497799835) from the App Store.
2. Xcode → Settings → Accounts → add your Apple ID (no paid subscription needed).
3. Clone the repository, open `iosApp/iosApp.xcodeproj`.
4. In `iosApp/Configuration/Config.xcconfig` set `TEAM_ID` to your personal team ID
   (Xcode → Settings → Accounts → your Apple ID → `<Your name> (Personal Team)`; the Team ID is
   visible via "Manage Certificates" or in the Signing & Capabilities tab of the `iosApp` target
   after selecting the team). `CODE_SIGN_STYLE` is already set to `Automatic`.
5. Connect the iPhone via cable (or connect once via cable, then Xcode → Window → Devices and
   Simulators → "Connect via network").
6. On the iPhone: Settings → Privacy & Security → Developer Mode → enable → reboot (iOS 16+).
7. In Xcode select your iPhone and press ▶ Run. The Gradle build of the shared framework starts
   automatically (build phase `embedAndSignAppleFrameworkForXcode`) — the first build takes a few
   minutes.
8. On first launch you'll see "Untrusted Developer": Settings → General → VPN & Device Management →
   select your Apple ID → "Trust".
9. After 7 days the certificate expires ("Unable to verify app") — connect the phone and repeat
   step 7; local data is preserved.
</details>

<details>
<summary><b>Option 2. AltStore / SideStore</b> — side-load without a cable for each launch</summary>

Use this if you don't want to plug the phone into a Mac every week. A computer (Mac/Windows) is still
needed for the initial install and background signature refresh over Wi-Fi.

1. Take the ready unsigned `.ipa` from the [Releases](https://github.com/ArkHak/Aniko/releases) page
   (`aniko-v0.0.2-ios-unsigned.ipa`), or build it yourself: in Xcode (after setting `TEAM_ID` as in
   Option 1) Product → Archive for a real device → Distribute App → Development → export the `.ipa`.
2. Install [AltServer](https://altstore.io/) (or [SideStore](https://sidestore.io/) — a more flexible
   fork) on your computer and sign in with the same free Apple ID.
3. Via AltServer install AltStore/SideStore on the iPhone (first time — via cable), then trust the
   developer in iPhone Settings.
4. In AltStore on the iPhone tap "+" and pick the `.ipa` — it will be signed with your Apple ID and
   installed.
5. As long as the computer running AltServer is reachable on the same Wi-Fi network, the signature
   is renewed automatically.
</details>

> Both options are limited by Apple's policy for free Apple IDs (7-day signature, App ID cap) — a
> system iOS restriction that cannot be bypassed without a paid Apple Developer Program (or a
> jailbreak).

### macOS (Desktop)

Download `aniko-v0.0.2-macos.dmg` from the [Releases](https://github.com/ArkHak/Aniko/releases) page,
open it and drag `Aniko.app` to `Applications`. The app is not signed or notarized by Apple — on first
launch allow it via Settings → Privacy & Security → "Open Anyway" (or build the `.dmg` from source,
see below).

## 🔧 Building from source

Requirements: JDK 17+, Android SDK (for the Android target), Xcode (for the iOS target, macOS only).

```bash
# Android — debug APK
./gradlew :composeApp:assembleDebug
# output: composeApp/build/outputs/apk/debug/composeApp-debug.apk

# Desktop (macOS) — DMG
./gradlew :composeApp:packageDmg

# iOS — open in Xcode
open iosApp/iosApp.xcodeproj
```

<details>
<summary><b>Android release signing</b> (<code>assembleRelease</code> / <code>bundleRelease</code>)</summary>

`assembleDebug` needs no signing (AGP generates a debug keystore). A release build requires your own
key — the repository does not contain one (see `.gitignore`: `*.jks`, `keystore.properties`):

```bash
cp keystore.properties.example keystore.properties
keytool -genkeypair -v -keystore composeApp/release/aniko-release.jks \
  -alias aniko-release -keyalg RSA -keysize 2048 -validity 10000
# fill in storePassword/keyPassword in keystore.properties
# (PKCS12 requires storePassword == keyPassword)

./gradlew :composeApp:assembleRelease
# output: composeApp/build/outputs/apk/release/composeApp-release.apk
```

Without `keystore.properties` (or the `ANIKO_KEYSTORE_PATH` / `ANIKO_KEYSTORE_PASSWORD` /
`ANIKO_KEY_ALIAS` / `ANIKO_KEY_PASSWORD` environment variables — used in CI) the release build fails
at the signing step instead of producing an unsigned artifact.
</details>

## 📚 Documentation

- [`docs/api/ANIXART_API.md`](docs/api/ANIXART_API.md) — full description of the Anixart API in use
  (endpoints, models, authentication, live response samples; in Russian)
- [`docs/REELWAVE_PLAN.md`](docs/REELWAVE_PLAN.md) — development tracker: phase statuses, key
  decisions, tech debt, remaining tasks (in Russian)
- [`AGENTS.md`](AGENTS.md) — repository rules for agents and contributors (git flow, code
  conventions, validation; in Russian)

## ⚖️ Legal information

- **Content rights.** Aniko is only a client interface to the Anixart service: the app does not
  store, produce or distribute audiovisual works. All content (video, images, descriptions) is loaded
  from Anixart servers and third-party video hostings and belongs to the respective rights holders.
  The user is responsible for complying with copyright when watching.
- **Service and trademark rights.** Anixart and related names and logos belong to their respective
  owners. The Aniko project is not affiliated with them and does not claim to be an official client.
- **API research.** The protocol description in [`docs/api/ANIXART_API.md`](docs/api/ANIXART_API.md)
  was obtained by studying the app's interaction with the service for research purposes, including
  interoperability, and is published as technical documentation — not as instructions for
  unauthorized access.
- **No circumvention.** The app contains no VPN, proxy or other means of bypassing blocks and is not
  intended to access restricted information. The fallback address chain mentioned in the API
  documentation is a property of the original Anixart service; it is not implemented in Aniko.
- **Age ratings.** Age ratings are displayed exactly as provided by the Anixart service. Users must
  comply with the age-restriction laws of their jurisdiction on their own.
- **Personal data.** Credentials and the session token are stored locally on the user's device and
  are transmitted only to Anixart servers. The Aniko developer does not collect or process users'
  personal data.

## 📄 License

No license specified. The code is published for personal/research use; for distribution questions
contact the repository author.

---

<p align="center">
  <sub>Aniko is an independent project, not affiliated with Anixart. Made for research and personal use.</sub>
</p>
