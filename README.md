# CallStudio

CallStudio is a native Kotlin + Jetpack Compose Android app for scheduling local incoming-call experiences. It does not place phone-network calls and does not transmit imported audio to another phone.

## Included in this project

- Local caller profiles with optional photo and contact selection.
- Immediate incoming-call UI and scheduled calls, including delay in seconds and specific date/time.
- Room persistence for caller profiles, schedules, call history and microphone recording metadata.
- AlarmManager scheduling with exact-alarm access checks and an inexact fallback.
- High-priority incoming-call notification with full-screen intent, Answer and Decline actions.
- Foreground ringtone service, system ringtone picker, vibration setting and optional caller audio playback after answering.
- Active-call timer, audio replay/pause, speaker routing controls and explicit microphone recording.
- Six visual styles and DataStore-backed settings.
- GitHub Actions test/build workflow which uploads a debug APK artifact.

## Build on GitHub

1. Create a new GitHub repository named `CallStudio` (or choose another empty repository).
2. Upload the contents of this project folder to the repository root, including `.github/workflows/android.yml`, `gradlew`, `gradlew.bat`, `settings.gradle.kts` and `app/`.
3. Ensure the default branch is named `main`, or change the branch filter in `.github/workflows/android.yml`.
4. Open **Actions → Build CallStudio APK** and select **Run workflow**, or push a commit to `main`.
5. Wait for the run to finish. A green run means the configured unit tests and debug build completed successfully.
6. Open the successful run, scroll to **Artifacts**, and download **CallStudio-debug-apk**.
7. Extract the downloaded ZIP, install `app-debug.apk` on the phone, and approve Android's installation prompt.

The workflow builds the APK at `app/build/outputs/apk/debug/app-debug.apk` and uploads that exact file. The debug APK is not a Play Store release and is not signed with a private release key.

## Local build

You need JDK 17, Android SDK Platform 36, Android Build Tools 36.0.0 and internet access to resolve Gradle/Maven dependencies. Open the folder in Android Studio or run:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The included `gradlew` is a lightweight Gradle bootstrap script because the standard Gradle wrapper JAR is not bundled. It downloads Gradle 8.13 into the Gradle user cache the first time it runs. On Windows use `gradlew.bat`.

## First-launch setup

Open **Settings** and review Notifications, Exact alarms and Full-screen incoming call. Grant the access that Android offers for your device. You can also choose a system ringtone, import caller audio and choose one of the six themes.

To test an incoming call, create a caller profile first, then tap **Start incoming call** or schedule a delay. Use **Answer** or **Decline** to finish the ringing stage. After answering, use **End call** to finish the session.

## Important Android limitations

- The app creates a local call-like screen; it is not an actual cellular, VoIP or SIM call.
- Imported caller audio plays on the same phone after answering. It is not transmitted to a remote caller.
- The recording feature records the device microphone after explicit permission and user action. It does not secretly record telephone calls or capture another app's call audio. The UI labels the saved recording as a microphone recording.
- Android 14+ may require the user to allow full-screen intents separately. If access is denied, Android may show a notification instead.
- Exact alarms may be denied. In that case Android can delay a trigger; battery-management restrictions can vary by manufacturer.
- Force-stopping the app can prevent alarms from firing until the user opens the app again.
- The current call session is intended for local testing. Verify lock-screen display, ringtone behaviour, permissions, and scheduling on the target phone after installing the APK.
- A build workflow has been added, but no APK is included in this source archive because this workspace has no Android SDK/Gradle installation and could not run the Android build locally. The APK becomes downloadable after the GitHub Actions workflow successfully completes.
