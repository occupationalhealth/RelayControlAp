RelayControl Android app (Kotlin)
--------------------------------
This is a minimal Android Studio project that:
- Connects to an ESP8266 Access Point at http://192.168.4.1
- Shows two buttons (Relay 1, Relay 2)
- Uses BiometricPrompt (fingerprint/biometrics) for auth before toggling
- Periodically (every 2s) polls /api/status to update UI

How to build:
1. Open this folder in Android Studio (Arctic Fox or newer recommended).
2. Let Gradle sync and download dependencies.
3. Build -> Build APK(s) or run on a device.
Or build from command line:
./gradlew assembleDebug

Notes:
- minSdkVersion is 23 to support BiometricPrompt.
- The app assumes the phone is connected to the ESP AP (SSID: Relay-Control).
- If you want a signed production APK, generate a signing key and configure signingConfigs in app/build.gradle.
