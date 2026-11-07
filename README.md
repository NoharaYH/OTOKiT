# OTOKiT

Minimal native Android application using Kotlin and Views. Existing architecture
notes are preserved in `docs/`; no feature implementation has been migrated.

Open this directory in Android Studio and sync the Gradle project.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
```

The project pins its build dependencies in `gradle/libs.versions.toml` and uses
the Gradle Wrapper. Configure the Android SDK in the local development environment
or an untracked `local.properties` file. No global Gradle installation is needed.

The application ID and Kotlin package are `com.otokit.noharayh`.
