<div align="center">

<img src="docs/icon.png" width="132" alt="Aniko" />

# Aniko

**An unofficial multiplatform client for [Anixart](https://anixart.tv)**<br/>
for iPhone, Android and Mac — one Kotlin Multiplatform codebase

<br/>

[![Release](https://img.shields.io/badge/release-v0.1.0-8B6FF0?style=for-the-badge)](https://github.com/ArkHak/Aniko-app/releases/tag/v0.1.0)
[![License](https://img.shields.io/badge/license-GPL--3.0-0A0C12?style=for-the-badge)](LICENSE)
[![Platforms](https://img.shields.io/badge/iOS%20·%20Android%20·%20macOS-0A0C12?style=for-the-badge)](#-download)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![CI](https://img.shields.io/github/actions/workflow/status/ArkHak/Aniko-app/ci.yml?branch=main&style=for-the-badge&label=CI)](https://github.com/ArkHak/Aniko-app/actions/workflows/ci.yml)
[![Telegram](https://img.shields.io/badge/Telegram-aniko__portal-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/aniko_portal)

[🇷🇺 Русский](README.md) · **🇬🇧 English**

[**Download**](#-download) · [Features](#-features) · [Install](#-installation) · [FAQ](#-faq) · [Support](#-support)

</div>

<div align="center">
  <img src="docs/media/aniko-launch.gif" width="300" alt="Aniko launch: icon, home screen, catalog and title page" />
</div>

---

> **The project started as an iOS app.** There is no official Anixart client for iPhone, and Aniko
> fills exactly that gap. Android and Desktop came almost for free thanks to the shared codebase —
> and they are full clients, not stubs.

> [!NOTE]
> **Status: early public release (v0.1.0).** It is usable right now, but the project is under active
> development: bugs are possible, some Anixart features are not implemented yet, and the local data
> format may change between versions.

## 📥 Download

Ready-made builds are on the [**Releases**](https://github.com/ArkHak/Aniko-app/releases) page.

| Platform | File | Size | Requirements | How to install |
|---|---|---|---|---|
| 🤖 **Android** | `aniko-v0.1.0-android.apk` | 4.6 MB | Android 8.0+ (API 26) | [guide](#-android) |
| 🍎 **iPhone / iPad** | `aniko-v0.1.0-ios-unsigned.ipa` | 16 MB | iOS 15+ | [guide](#-iphone-without-a-paid-apple-developer-account) |
| 💻 **macOS** | `aniko-v0.1.0-macos.dmg` | 172 MB | Apple Silicon Mac (M1 or newer) + [VLC](https://www.videolan.org/vlc/) | [guide](#-macos) |

> The app is free and ad-free. Signing in requires an Anixart account (you can create one inside the app).

## 📸 Screenshots

<div align="center">

**iPhone**

| Home | Catalog | Title page |
|:---:|:---:|:---:|
| <img src="docs/screenshots/ios-home.png" width="260" alt="Home, iOS" /> | <img src="docs/screenshots/ios-catalog.png" width="260" alt="Catalog, iOS" /> | <img src="docs/screenshots/ios-release.png" width="260" alt="Title page, iOS" /> |

**macOS**

| Home | Catalog | Title page |
|:---:|:---:|:---:|
| <img src="docs/screenshots/desktop-home.png" alt="Home, macOS" /> | <img src="docs/screenshots/desktop-catalog.png" alt="Catalog, macOS" /> | <img src="docs/screenshots/desktop-release.png" alt="Title page, macOS" /> |

| Schedule | My lists |
|:---:|:---:|
| <img src="docs/screenshots/desktop-schedule.png" alt="Schedule, macOS" /> | <img src="docs/screenshots/desktop-library.png" alt="My lists, macOS" /> |

</div>

## ✨ Features

<table>
<tr>
<td width="50%" valign="top">

### 🔎 Catalog & search
- "Anime" / "Donghua" tabs, "All" / "New" sorting
- Search and filters: genre, year, status, type
- Release schedule by day of the week
- Feeds, collections, "Trending discussions", "New episodes"

### 🎬 Playback
- Voice-over and source picker, pinned ("favorite") voice-overs
- Per-title voice-over memory
- "Resume from where you left" dialog, ±10 / −30 s seek, gestures
- Default quality and seamless quality switching (Desktop)
- Picture-in-Picture (Android)
- For titles licensed in your country — links to legal streaming platforms

</td>
<td width="50%" valign="top">

### 📚 Library
- Lists: watching · planned · completed · on hold · dropped
- Favorites and watch history, list / grid view
- Optimistic writes + offline queue: changes made offline sync automatically

### 👤 Profile & community
- Watch statistics, activity chart, favorite genres, earned badges
- Comments: write, vote, reply
- Notifications with an unread badge

### 🎨 Interface
- iOS HIG-inspired design with a Liquid Glass material
- Adaptive layouts: phone · tablet · desktop
- Light and dark themes, RU / EN
- `aniko://release/…` links to open a title or an episode

</td>
</tr>
</table>

**Account and data.** Sign-in and sync go through your official Anixart account: your lists, history
and progress match what you see in the original app. Your session token stays on the
device (Keychain on iOS/macOS, encrypted storage on Android).

**Not there yet.** Instant push notifications (new episodes arrive through periodic polling),
an achievements catalog (only badges you have already earned are shown), and quality / subtitle / audio-track selection where the video opens inside a
third-party embedded player page (Android and iOS).

## 📦 Installation

### 🤖 Android

1. Download `aniko-v0.1.0-android.apk` from [Releases](https://github.com/ArkHak/Aniko-app/releases).
2. Allow installing from unknown sources. Android usually offers this itself: when you first open the APK,
   tap "Settings" and turn on "Allow from this source". If no dialog appears, turn it on manually:
   *Settings → Apps → Special app access → Install unknown apps* → pick the browser or file manager you
   open the file with. On Samsung, Xiaomi and other skins the items may be named differently — search the
   settings for "unknown apps".
3. Open the APK and confirm the installation. If Google Play Protect warns about an unknown
   developer, choose "Install anyway": the build is signed with the project's own key, not a Google
   Play key.

> [!IMPORTANT]
> `v0.0.x` builds were signed with a debug key. The `0.1.0` release build is signed with a different
> key, so **you must uninstall the old Aniko first** (Android cannot update an app over a build with a
> different signature). Your account data is safe — it lives on Anixart's servers.

### 🍎 iPhone without a paid Apple Developer account

Apple requires a signature for *any* app installed outside the App Store. "Without a developer
account" here means **without the paid Apple Developer Program ($99/year)**: a regular free Apple ID
is enough. The price of the free route is a **7-day certificate** — after that the app has to be
re-signed (your data is kept). Pick one of the methods:

<details>
<summary><b>Method 1. Xcode</b> — no third-party tools (Mac required)</summary>

<br/>

1. Install [Xcode](https://apps.apple.com/app/xcode/id497799835) and add your Apple ID:
   *Xcode → Settings → Accounts → "+"*.
2. Clone the repository and open the project:
   ```bash
   git clone https://github.com/ArkHak/Aniko-app.git Aniko
   open Aniko/iosApp/iosApp.xcodeproj
   ```
3. Put your Team ID into `iosApp/Configuration/Config.xcconfig` (`TEAM_ID=XXXXXXXXXX`). You can find it
   under *Xcode → Settings → Accounts → your Apple ID → Personal Team*, or in the *Signing &
   Capabilities* tab of the `iosApp` target. Do not commit this value.
4. On the iPhone enable *Settings → Privacy & Security → Developer Mode* (a reboot is required,
   iOS 16+) and connect the phone with a cable.
5. Select the iPhone in Xcode and press ▶ **Run**. The first Kotlin/Native build takes a few minutes.
6. On first launch: *Settings → General → VPN & Device Management →* your Apple ID → **Trust**.
7. After 7 days, connect the phone and repeat step 5 — local data is preserved.

</details>

<details>
<summary><b>Method 2. AltStore / SideStore</b> — ready-made .ipa, re-signing over Wi-Fi</summary>

<br/>

1. Download `aniko-v0.1.0-ios-unsigned.ipa` from [Releases](https://github.com/ArkHak/Aniko-app/releases)
   on the iPhone (or transfer the file to it).
2. Install [AltServer](https://altstore.io/) (Mac/Windows) or [SideStore](https://sidestore.io/) and
   sign in with your free Apple ID.
3. Install AltStore/SideStore on the iPhone (the first time via cable) and trust the developer in
   the iPhone settings.
4. In AltStore tap "+" and pick the `.ipa` — it gets signed with your Apple ID and installed.
5. While the computer running AltServer is on the same Wi-Fi network, the signature is renewed
   automatically.

</details>

<details>
<summary><b>Method 3. Sideloadly</b> — graphical installer (Mac/Windows)</summary>

<br/>

1. Download [Sideloadly](https://sideloadly.io/) and connect the iPhone with a cable.
2. Drag `aniko-v0.1.0-ios-unsigned.ipa` into the window, enter your free Apple ID and press **Start**.
3. Trust the developer: *Settings → General → VPN & Device Management*.
4. Repeat the installation every 7 days to renew the signature.

</details>

> The free Apple ID limits (7 days, a cap on simultaneously signed apps) are Apple policy. They cannot
> be bypassed without the paid program or a jailbreak.

### 💻 macOS

1. Download `aniko-v0.1.0-macos.dmg`, open it and drag **Aniko** to *Applications*.
2. Install [VLC](https://www.videolan.org/vlc/) into `/Applications` — Aniko uses its libraries to
   play video. The VLC build must match your Mac's architecture (Apple Silicon).
3. Launch Aniko. The build is ad-hoc signed and not notarized by Apple, so on first launch Gatekeeper
   refuses to open it ("app is damaged" or "developer cannot be verified"). Use any of these:

   - **Right-click → "Open"** on `Aniko.app`, then "Open" again in the dialog (once).
   - **System Settings → Privacy & Security** → "Open Anyway" next to Aniko.
   - **Terminal** — remove the quarantine flag:
     ```bash
     xattr -dr com.apple.quarantine /Applications/Aniko.app
     ```

Don't trust the prebuilt binary? Build it yourself — [it is straightforward](docs/DEVELOPMENT_EN.md#build-from-source), and
a locally built app is not quarantined.

**Windows and Linux.** There are no prebuilt binaries. If you need them, write in Telegram: [t.me/aniko_portal](https://t.me/aniko_portal).

### 🚀 First launch

Aniko opens a sign-in screen. Enter your Anixart login and password, or tap "Registration" —
account creation (with an emailed confirmation code) is built into the app. The app does not work
without signing in: the catalog and profile are tied to the account.

## ❓ FAQ

<details>
<summary><b>macOS says "Aniko is damaged" and offers to move it to the Bin</b></summary>

That's Gatekeeper: the app is not notarized by Apple. Run
`xattr -dr com.apple.quarantine /Applications/Aniko.app` or use right-click → "Open".
</details>

<details>
<summary><b>The UI opens on Mac, but video doesn't play</b></summary>

Make sure VLC is installed in `/Applications` and that its architecture matches your Mac's
processor (Apple Silicon → arm64 or universal VLC build).
</details>

<details>
<summary><b>On iPhone: "Unable to verify app" / the app won't launch</b></summary>

The 7-day signature of a free Apple ID has expired. Re-sign the app (Xcode, AltStore or Sideloadly) —
your data is kept. Also make sure Developer Mode is on.
</details>

<details>
<summary><b>Android won't let me install the APK</b></summary>

Allow installs from unknown sources for the app you open the file with. If Aniko is already
installed and signed with a different key, uninstall it first (see the note in the Android section).
</details>

<details>
<summary><b>"Watch" is missing and a list of platforms is shown instead of episodes</b></summary>

The rights holder has licensed the title in your country, so in-app playback is disabled (just like
in the official client). Aniko shows links to legal streaming platforms instead.
</details>

<details>
<summary><b>The catalog doesn't load</b></summary>

Check your internet connection and whether the Anixart service is reachable from your network. Aniko
contains no VPN, proxy or other circumvention tools (see [Legal](#-legal)).
</details>

## 💬 Support

The official place for Aniko support is Telegram: **[t.me/aniko_portal](https://t.me/aniko_portal)**. Ask a question, report a problem, suggest an idea or request a build for another platform there.

## 📚 For developers

The source code is open. Building from source, architecture and tests are described in [`docs/DEVELOPMENT_EN.md`](docs/DEVELOPMENT_EN.md); the Anixart API description is in [`docs/api/ANIXART_API.md`](docs/api/ANIXART_API.md) (in Russian).

## 📜 Legal

The project is developed and documented primarily for a Russian-speaking audience; the legal notes are
therefore anchored in Russian law. Summary:

- **Content rights.** Aniko is only a client interface to the Anixart service: the app does not store,
  produce or distribute audiovisual works. All content (video, images, descriptions) is loaded from
  Anixart's servers and third-party video hosts and belongs to the respective rights holders
  (Art. 1255, 1270 of the Civil Code of the Russian Federation). The user is responsible for
  respecting copyright when watching.
- **Service rights and trademarks.** Anixart and related names and logos belong to their owners. Aniko
  is not affiliated with them and does not present itself as an official client.
- **API research.** The protocol description in [`docs/api/ANIXART_API.md`](docs/api/ANIXART_API.md)
  was obtained by studying the app's interaction with the service for research purposes, including
  interoperability (cf. Art. 1280 of the Civil Code of the Russian Federation), and is published as
  technical documentation, not as instructions for unauthorized access.
- **No circumvention.** The app contains no VPN, proxy or other means of bypassing blocks, and is not
  intended for accessing information whose distribution is restricted in the Russian Federation
  (Art. 15.1–15.8 of Federal Law No. 149-FZ). The fallback address chain mentioned in the API
  documentation is a property of the original Anixart service; it is not implemented in Aniko.
- **Age restrictions.** Age ratings are shown as provided by the Anixart service. Users must comply
  with Federal Law No. 436-FZ on protecting children from harmful information themselves.
- **Personal data.** Credentials and the session token are stored locally on the user's device and are
  sent only to Anixart's servers. The Aniko developer does not collect or process users' personal data
  and is not their operator (Federal Law No. 152-FZ on personal data).

## 📄 License

The code is licensed under the **[GNU GPL v3.0](LICENSE)**. The Desktop version uses
[vlcj](https://github.com/caprica/vlcj) (GPL-3.0) and the [VLC](https://www.videolan.org/vlc/)
libraries (LGPL-2.1+), so the project as a whole is released under GPL-3.0. Other dependencies are
under Apache-2.0 and other compatible licenses. Copyright © 2026 ArkHak and Aniko contributors.

## 🙏 Acknowledgements

[Anixart](https://anixart.tv) for the service and content · [JetBrains](https://www.jetbrains.com) for
Kotlin and Compose Multiplatform · [VLC](https://www.videolan.org/vlc/) and
[vlcj](https://github.com/caprica/vlcj) for the Desktop player · [AltStore](https://altstore.io/) and
[SideStore](https://sidestore.io/) for making it possible to install apps on iPhone without a paid
subscription.

---

<div align="center">
  <sub>Aniko is an independent project, not affiliated with Anixart. Made for research and personal purposes.</sub>
</div>
