# Android verification

## Local checks

With JDK 17 and Android SDK 36 installed, run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :fixture:assembleDebug :fixture:assembleDebugAndroidTest
```

Use `./gradlew.bat` in PowerShell. Unit tests cover command parsing, prompt size and selection, model payload handling, and search control and result matching.

## Device checks

Install `app-debug.apk`, `fixture-debug.apk`, and `fixture-debug-androidTest.apk` from the corresponding `build/outputs/apk/` directories. Add `-t` when installing the instrumentation APK with `adb`. Enable Hyperchat in Android Accessibility settings. For the two Naver Map runs, install the app separately, complete onboarding, and dismiss any modal dialogs. Bring its map home to the foreground before each run. For the guide, import the `.litertlm` file before starting the probe. The guide probe uses the English `Search on NAVER Maps` label.

```sh
adb shell am instrument -w -e mode fixture dev.hanzicho.hyperchat.fixture.test/dev.hanzicho.hyperchat.fixture.HyperchatProbe
adb shell am instrument -w -e mode guide dev.hanzicho.hyperchat.fixture.test/dev.hanzicho.hyperchat.fixture.HyperchatProbe
adb shell am instrument -w -e mode naver-search dev.hanzicho.hyperchat.fixture.test/dev.hanzicho.hyperchat.fixture.HyperchatProbe
```

Run the probes serially on a single device. In a multi-device setup, use `adb -s <serial>`. Check for `outcome=PASS` and `INSTRUMENTATION_CODE: -1`. The Naver Map search probe separately reports the overlay status and asserts the query and result container in Naver Map. The probe runs with `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` so Android leaves Hyperchat enabled during assertions. It saves screenshots under the fixture's external files directory. Keep screenshots and diagnostics local if they contain third-party UI or user data.

## Recorded environment

The local run used JDK 17, Android SDK 36, and an Android 16 API 36 x86-64 emulator. Naver Map was version `6.12.1.1` (`61201000`), running unmodified with ARM translation.

- **Guide.** The pinned Qwen3-0.6B CPU model returned the `Search on NAVER Maps` control, independently observed in the original app window.
- **Naver Map search.** `/search Seoul Station` opened the Naver Map result screen. The probe found the committed query and Naver Map result container after approval. Hyperchat timed out on its own result check and displayed `검색어 제출 완료 · 결과 확인 필요`.
- **Fixture search.** The overlay reported verified completion and the probe independently found the result in the fixture's own window.

Physical Galaxy and One UI testing remains pending.
