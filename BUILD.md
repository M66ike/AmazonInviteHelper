# Building the APK

This project intentionally uses only Android platform APIs, with no third-party runtime dependencies.

Recommended toolchain as of September 2026:

- Android Gradle Plugin 9.1.1
- Gradle 9.3.1 or newer
- JDK 17+
- Android SDK platform 37
- Android Build Tools 36.0.0+

In Android Studio, open the project and choose **Build > Build APK(s)**.

From a configured command line:

```bash
./gradlew assembleDebug
```

The APK will be at:

```text
app/build/outputs/apk/debug/app-debug.apk
```
