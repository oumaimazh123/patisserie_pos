# GitHub Actions CI

The workflow in `.github/workflows/ci.yml` runs on pushes, pull requests, and manual dispatch. No repository secrets are required. Actions are pinned to commits; the token has read-only repository access.

## Checks

- Windows 2022 and Ubuntu 24.04: shared-logic and desktop tests, followed by a self-contained Compose Desktop build. Linux tests run under Xvfb.
- Packaged desktop JAR verification: application entry point, image composables, `DesktopImageCache`, and its backing-map class must be present. This catches missing packaged classes; it does not detect an IDE runtime being rebuilt while running.
- Android: debug unit tests, lint, and debug APK assembly.
- `CI required`: a stable aggregate check which fails if any required job fails or is skipped. Configure this check in the GitHub branch ruleset to block merging failed builds; the workflow alone does not enable branch protection.

Java 21 runs Gradle; Java 17 is also installed for the Kotlin/JVM toolchain. Android SDK package `platforms;android-37.0` matches `compileSdk` in `app/build.gradle.kts`. Gradle uses the checked-in wrapper. Configuration caching is disabled in CI; Gradle dependency caching and wrapper validation are provided by `setup-gradle`.

## Results and downloads

Open the commit or pull request's Actions run. Test/lint reports are retained for 14 days, including failed runs when reports exist. Successful jobs upload desktop application archives and the debug APK for 7 days. Extract desktop archives with `tar -xzf` to preserve Linux executable permissions.

Desktop artifacts are unpacked applications with a bundled runtime, not signed installers. The Android APK is a debug build. CI does not publish releases, sign installers, generate licences, or access production POS data. It does not replace emulator/device tests, printer/drawer testing, or fresh-install/upgrade acceptance.

## Running equivalent checks locally

Close development POS instances before rebuilding their runtime outputs.

```powershell
.\gradlew.bat :sharedLogic:desktopTest :desktopApp:desktopTest --continue --stacktrace --no-configuration-cache
.\gradlew.bat :desktopApp:createDistributable --stacktrace --no-configuration-cache
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --continue --stacktrace --no-configuration-cache
```

On Linux use `bash ./gradlew`; prefix desktop tests with `xvfb-run -a` on a headless host. Existing test failures are intentionally not suppressed.
