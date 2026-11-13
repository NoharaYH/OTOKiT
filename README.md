# OTOKiT

Native Android application using Kotlin and Views. The UI shell, adaptive
navigation, settings, theme selection, and background rendering are implemented;
score sync and music data screens currently remain placeholders.

Architecture and current UI contracts are documented in `docs/UI架构.md` and
`docs/主题选择.md`. Planned directories in the architecture overview are explicitly
marked and are not a list of implemented components.

Open this directory in Android Studio and sync the Gradle project.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
```

Component tests use real Android Views and system input/accessibility on a device
or emulator. To target one emulator explicitly (without touching a connected
physical device):

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r com.otokit.noharayh.test/androidx.test.runner.AndroidJUnitRunner
```

Use the serial shown by `adb devices`. Instrumentation results must report `OK`;
the `adb` process exit code alone does not establish that tests passed. The GPU
frame/screenshot test requires API 29 or newer. Tests launch the application's
Activity and mount a temporary component host; they do not change theme preferences.

The project pins its build dependencies in `gradle/libs.versions.toml` and uses
the Gradle Wrapper. Configure the Android SDK in the local development environment
or an untracked `local.properties` file. No global Gradle installation is needed.

The application ID and Kotlin package are `com.otokit.noharayh`.
