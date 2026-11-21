# LYRA — Personal Calling Assistant

**Package ID:** `com.ajay.lyra`  
**Owner / Developer:** Ajay  
**Minimum SDK:** 26 (Android 8.0) • **Target / Compile SDK:** 36  

Lyra is a dark-first, futuristic Android personal calling assistant built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Room**, **DataStore**, **Android Telecom APIs**, and **Firebase AI Logic**.

---

## Core Features

- **Caller Identification & Owner Priority**
  - Robust phone number normalization handling `+91XXXXXXXXXX`, `0XXXXXXXXXX`, and `XXXXXXXXXX` formats.
  - Highlights **Owner / Priority Caller** (`Ajay`), **Priority Contacts**, **Known Contacts**, and **Unknown Callers**.
- **Contacts Directory & Priority Management (`ContactsViewModel`)**
  - Reads system contacts via `ContactsContract.CommonDataKinds.Phone.CONTENT_URI` (when `READ_CONTACTS` is granted), merged with Room priority contacts.
  - Real-time search, filter chips (`All`, `Priority`, `Device`), one-tap priority toggling, and an inline **Caller ID Test** tool.
- **Incoming & Active Call Interface**
  - Dedicated incoming call UI with Accept, Decline, Quick Message, and Silence actions.
  - Active call dashboard with live timer, Mute / Speaker / DTMF Keypad / Hold controls, and real-time in-call notes.
- **Voice Audio Transcription (`gemini-3.5-transcribe`)**
  - Record microphone audio (`16 kHz` mono WAV) with live waveform amplitude metering.
  - Streams audio to **Firebase AI Logic** (`gemini-3.5-transcribe`) protected by **Firebase App Check**, with one-tap copy and saving into Lyra's local Call Notes database.
- **Dialer Keypad, Call History & Local Backup**
  - Ergonomic one-handed mobile keypad (`DialerScreen`) with predictive contact matching, speed-dial chips, and persistent bottom navigation.
  - Local Room persistence for call notes and summaries, plus local JSON contact backup/restore.

---

## GitHub Actions CI/CD Workflows

This repository includes automated GitHub Actions workflows in `.github/workflows/`:

1. **`android-ci.yml`** (runs on push, pull requests, and manual dispatch):
   - Runs unit & Robolectric tests (`gradle :app:testDebugUnitTest`)
   - Runs Android Lint (`gradle :app:lintDebug`)
   - Builds the installable Debug APK (`gradle :app:assembleDebug`)
   - Uploads `lyra-debug-apk`, test reports, and lint reports as GitHub Actions artifacts.
2. **`android-release.yml`** (runs on `v*` tags or manual dispatch):
   - Builds signed Release APK / AAB when release keystore secrets are configured (or Debug APK fallback otherwise) and attaches outputs to a GitHub Release on version tags.

### Downloading the APK on Your Phone via GitHub

1. Open your GitHub repository in your mobile browser or the GitHub mobile app.
2. Go to the **Actions** tab and open the latest **Android CI - Build, Test & APK** workflow run.
3. Scroll to **Artifacts** and tap **`lyra-debug-apk`** to download and install the APK.

### Optional GitHub Repository Secrets (for Signed Releases)

- `RELEASE_KEYSTORE_BASE64`: Base64-encoded `.jks` upload keystore
- `STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key password
- `GOOGLE_SERVICES_JSON_BASE64`: Optional Base64-encoded `app/google-services.json` (if not committed)
