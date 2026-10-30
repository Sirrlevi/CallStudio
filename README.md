# CallStudio

CallStudio is a native Android app (Kotlin, Jetpack Compose, Material 3) that creates a **local, call-like experience on your own phone**: custom callers, scheduled "incoming calls", themed ringing and in-call screens, a caller voice clip played on the device, and optional microphone recording.

It does **not** place real calls, contact anyone, or send audio anywhere. There is no server, login or API key. Callers, schedules, settings, recordings and history stay on the device.

> **Build status:** this project was written without access to an Android SDK or the network, so it has **not been compiled or run yet**. The first GitHub Actions run is the first real build. See [Verification status](#verification-status).

## Get the APK from GitHub Actions

1. Push this project to a GitHub repository (branch `main` or `master`). The workflow in `.github/workflows/android.yml` runs on every push to `main`. You can also start it manually: **Actions** tab → **Android CI** → **Run workflow**.
2. Open the repository's **Actions** tab and click the most recent **Android CI** run. A green check means unit tests and the build passed. A red cross means something failed: open the failing step for the log.
3. Scroll to the **Artifacts** section at the bottom of the run page and click **CallStudio-debug-apk** to download a zip (kept for 30 days).
4. Unzip it. Inside is `app-debug.apk`.
5. Copy the APK to your phone (or download the artifact on the phone directly) and open it. If Android asks, allow your browser or file manager to **install unknown apps**, then tap **Install**. This is a debug-signed build for personal use.

## Turn on what the app needs

Open CallStudio → **Settings → Permissions and setup**. It shows live status and a button for each item:

| Item | Why | Where |
| --- | --- | --- |
| Notifications | Calls arrive as notifications (Android 13+ asks at runtime). | Allow notifications |
| Full-screen call screen | Lets a call take over the screen, including when locked (Android 14+ special access). Without it the call is a banner you tap. | Open settings → allow "full-screen notifications" |
| Exact timing | Calls ring at the chosen second (Android 12+ special access). Without it Android may deliver a call a few minutes late, and the app says so. | Open settings → allow "alarms & reminders" |
| Microphone | Only when you tap **Record** during a call. Nothing records on its own. | Asked at that moment |
| Contacts | Only to copy the photo of the contact you choose. | Asked when you tap Choose from contacts |

Contacts: asked only when you tap **Choose from contacts**. Android's picker doesn't share the contact's photo by itself, so the permission is used only to copy the photo of the contact you pick. If you say no, the name and number still import and you can choose a photo yourself.

## Using it

- **Callers**: add from contacts (name, number and the contact's photo come in together) or create a custom caller. Photos are copied into app storage, so they keep working after restarts.
- **Country code**: pick the country next to the number (flag and +code, searchable). The code is stored and shown separately, for example `+91 80590 56588`. Typing `+44 ...` or importing a contact with a `+` number sets the country automatically.
- **Call now / delayed / at a time**: from Home, or the Schedule screen (seconds, minutes, or a date and time). Scheduled calls are listed under **Scheduled**, where you can reschedule or cancel them.
- **Themes**: seven complete themes (Pixel, One UI, iOS, Vivo, Truecaller style, Minimal dark, Minimal light) with different layouts, type and controls. The Truecaller style theme is an original blue caller-ID screen: staggered entrance, ripples around the photo, breathing rings on the buttons, and press-and-hold buttons that swell while the other fades (release to confirm, slide off to cancel). In call it has Mute, Keypad (plays real DTMF tones), Speaker, Hold (pauses the clip and recording), Message (opens your messaging app) and End. It doesn't use any third-party name or logo; the header reads "Caller ID". Pick one under **Settings → Call theme**; thumbnails are the real screens.
- **Audio**: ringtone (default, system sound, your own file, or none) and vibration live in Settings.
- **Caller voice per call**: when you schedule a call you can optionally add an audio clip for that call only. Different calls can have different clips, and a call without one plays nothing. Settings only decides how a clip plays (once, repeat, or manual). The clip finishing never ends the call; only **End call** does.
- **Recording**: tap **Record** in the call screen. Mute silences your microphone in the recording. Recordings are microphone-only 16-bit WAV files, listed under **Settings → Recordings**.

## Build locally

Requirements: JDK 17, Android SDK 35, Gradle 8.9.

```
gradle :app:testDebugUnitTest   # unit tests
gradle :app:assembleDebug       # APK at app/build/outputs/apk/debug/app-debug.apk
```

No Gradle wrapper is committed (its binary jar could not be generated offline). Run `gradle wrapper --gradle-version 8.9` once if you want `./gradlew`; the CI workflow does not need it.

Instrumentation tests (`app/src/androidTest`) need a device or emulator: `gradle :app:connectedDebugAndroidTest`.

## Design notes

- `domain/`: pure Kotlin, unit tested: call state machine (`SCHEDULED → RINGING → ACTIVE → ENDED`, plus cancelled / rejected / missed / error), schedule math, duplicate rule, reboot-recovery planner.
- `scheduling/`: `AlarmManager` (exact when allowed, approximate otherwise, reported honestly), thin receivers, recovery after reboot, app update, clock or time-zone change.
- `session/`: `CallSessionManager` owns the single live call; `CallService` is a thin foreground service. Ringing and active-call state are separate.
- `notifications/`: call-style notifications, full-screen intent when allowed, unique `PendingIntent` request codes per call and action.
- `audio/`, `recording/`: ringtone, caller voice, routing, and microphone recorder, each released when the call ends.
- `data/`, `preferences/`: Room (callers, calls/history, recordings; database version 2 with a migration from version 1) and DataStore (settings).
- `domain/Countries.kt`, `domain/PhoneNumbers.kt`: built-in country table, number parsing and grouping (no extra library).
- `ui/`: Compose screens and the six call themes.

## Limits you should know about

- Android and OEM skins differ. Some devices hide full-screen calls or restrict background audio; the app falls back to a normal notification and shows what is missing in Setup.
- The app respects silent mode and Do Not Disturb; it does not bypass them.
- Force-stopping the app cancels its alarms. Opening the app once re-arms them.
- Calls that fall due while the phone is off ring on boot only if they are less than 2 minutes overdue; otherwise they are recorded as missed.
- Recording captures the microphone only. It does not capture a phone line or other apps. If the caller clip plays on speaker, the microphone may pick it up acoustically.
- If Android refuses to start the background service (for example on some Android 15 boot paths), ringing falls back to a notification with the system ringtone.
- Mute affects microphone recording. When nothing is recording it only remembers the setting and says so.

## Verification status

| Check | Status |
| --- | --- |
| Source written, unit and instrumentation tests written (including country codes, per-call clips, migration columns) | Done |
| Database migration 1 → 2 | Written, **not tested** on a real upgrade |
| Gradle build, unit tests, `assembleDebug` | **Not run** (no SDK or network where this was written). Run in GitHub Actions. |
| Behaviour on a phone or emulator (ringing, lock-screen, audio routing, recording, notifications) | **Not verified** |
