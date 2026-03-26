# Repository Guidelines

## Project Structure & Module Organization
This is a single-module Android app project (`:app`) for an LSPosed/Xposed module.
- Core Kotlin code: `app/src/main/java/com/gemini/threefingerscreenshot/`
- App manifest/resources: `app/src/main/AndroidManifest.xml`, `app/src/main/res/`
- Xposed entry declaration: `app/src/main/assets/xposed_init`
- Local Xposed API stub: `app/libs/api-82.jar` (`compileOnly`)
- Build configuration: `build.gradle`, `app/build.gradle`, `settings.gradle`, `gradle/wrapper/`

Generated artifacts live under `build/` and `app/build/`; do not treat them as source.

## Build, Test, and Development Commands
Run from repository root:
- `.\gradlew.bat clean` - remove build outputs.
- `.\gradlew.bat assembleDebug` - build debug APK.
- `.\gradlew.bat installDebug` - install debug APK on a connected device.
- `.\gradlew.bat lint` - run Android lint checks.
- `.\gradlew.bat testDebugUnitTest` - run JVM unit tests.
- `.\gradlew.bat connectedDebugAndroidTest` - run instrumentation tests on device/emulator.

Use `./gradlew` instead of `gradlew.bat` on macOS/Linux.

## Coding Style & Naming Conventions
- Language: Kotlin + Android framework APIs.
- Indentation: 4 spaces; keep lines and methods readable and focused.
- Naming: `PascalCase` classes (`GestureHook`), `camelCase` methods/properties (`handleTouchEvent`), `UPPER_SNAKE_CASE` constants.
- Keep package names under `com.gemini.threefingerscreenshot`.
- Use Android Studio’s default Kotlin formatter and optimize imports before committing (no dedicated ktlint/detekt config is present).

## Testing Guidelines
There are currently no committed test sources. Add tests with standard Android layout:
- Unit tests: `app/src/test/kotlin/...`
- Instrumentation tests: `app/src/androidTest/kotlin/...`

Name tests `*Test` (unit) and `*InstrumentedTest` (device), and prioritize gesture logic edge cases (pointer count, swipe threshold, cooldown timing).

## Commit & Pull Request Guidelines
Git history is not available in this checkout, so follow Conventional Commits:
- Examples: `feat: add systemui hook fallback`, `fix: prevent duplicate screenshot triggers`

For pull requests, include:
- clear problem/solution summary,
- linked issue (if any),
- test evidence (commands run and results),
- relevant logs/screenshots for behavior changes.

## Security & Configuration Notes
- Do not commit machine-specific `local.properties` changes.
- Keep `xposed_init` aligned with the actual hook class path.
- Treat root/Xposed behavior changes as high risk; test on a non-primary device first.
