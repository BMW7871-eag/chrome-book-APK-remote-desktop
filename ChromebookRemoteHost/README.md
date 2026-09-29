# Chromebook Remote Host

A Kotlin/Android Studio project that turns an Android-capable Chromebook into a password-protected web-accessible screen-sharing host using Android MediaProjection, an embedded local HTTP/WebSocket server, and the official ngrok Java SDK.

## Important ChromeOS limitation

This app does **not** claim unrestricted ChromeOS desktop control. Android's MediaProjection can capture content only after the user grants screen-capture consent. A normal Android application does not receive a general ChromeOS API for injecting global mouse and keyboard events into the ChromeOS desktop. The web UI therefore exposes the input protocol and reports input as unavailable rather than pretending to control the host.

The app is still useful for the parts ChromeOS/Android actually exposes: authenticated web hosting, ngrok exposure, screen capture, status, and a browser-based remote viewer.

## Security

- ngrok Auth Token is encrypted with Android Keystore AES-GCM.
- Remote password is encrypted with Android Keystore AES-GCM.
- Remote pages require a random HttpOnly session cookie after password login.
- `/screen.jpg`, `/capabilities`, `/desktop`, and the WebSocket endpoint are not intended to be public without the login session.
- The stop button stops the local server, ngrok session, and MediaProjection service.

## Build

1. Install Android Studio with Android SDK, platform tools, and an Android NDK.
2. Open this folder in Android Studio.
3. Run `tools/build-ngrok-native.sh` from the project root. It clones the official ngrok Java source, compiles the native Android runtime, and copies the required files into the project.
4. Sync Gradle.
5. Build `app` > `assembleDebug`.

## Runtime flow

1. Enter your ngrok Auth Token.
2. Enter a remote password.
3. Choose a local port, normally 8080.
4. Press START REMOTE ACCESS.
5. Approve Android screen capture.
6. The local server starts.
7. The embedded ngrok session creates the public HTTPS URL.
8. Open the URL from Safari/Chrome/Firefox/Edge on another device.
9. Enter the remote password.
10. The viewer displays the captured screen.

## Browser viewer

The viewer uses compressed JPEG frames at a modest rate to keep the implementation simple and compatible. The WebSocket is used for the control channel and capability status. The input backend intentionally returns `INPUT_UNAVAILABLE` on normal ChromeOS Android installs.

## GitHub Actions: build the APK without Android Studio

This repository includes `.github/workflows/build-apk.yml`.

1. Create a GitHub repository.
2. Upload/push this entire project, including `gradlew` and `gradle/wrapper`.
3. Open the repository's **Actions** tab.
4. Select **Build Chromebook Remote Host APK**.
5. Choose **Run workflow**.
6. When it finishes, open the completed workflow run.
7. Under **Artifacts**, download `Chromebook-Remote-Host-debug`.
8. Extract it and install the APK on the Chromebook.

The workflow installs the Android SDK/NDK, runs Gradle, and uploads the generated debug APK as an artifact.

### Important ngrok note

The project intentionally does not download an unknown prebuilt native ngrok binary. If the ngrok Android native component required by the current source is not available to the Gradle build, the workflow will fail rather than silently substitute an unsupported binary. See `NGROK_ANDROID.md` for the supported ngrok integration requirements.
