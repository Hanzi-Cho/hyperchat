# Hyperchat

Hyperchat brings local AI guidance and place search to Naver Map through an Android chat overlay. Enable the accessibility service, open Naver Map, and tap **Hyperchat**. A legacy app can expose a useful screen flow even when you have no integration API. Hyperchat reads visible controls for local guidance and uses a separate, explicitly approved search action.

```text
Clone https://github.com/Hanzi-Cho/hyperchat and read the Android build files and README.
Install the declared JDK/Android SDK dependencies, build the debug APK, and find the app and accessibility-service entry points.
On an Android test device with Naver Map installed, run the guide and search checks in docs/testing.md.
```

## Try the prototype

1. Open the project in Android Studio with JDK 17 and Android SDK 36, sync Gradle, and build a debug APK. For the command line, run `./gradlew :app:assembleDebug` (`.\gradlew.bat :app:assembleDebug` in PowerShell).
2. Install the debug APK on an Android test device and open Hyperchat. Enable the Hyperchat service in Android's Accessibility settings and install Naver Map separately. Close any Naver Map onboarding or promotional dialogs and return to the map home.
3. Tap the Hyperchat shortcut at the bottom right. Ask `Which button should I tap to search for a place?` after importing a compatible `.litertlm` model. The optional PowerShell script `./tools/get-model.ps1` fetches the pinned Qwen3-0.6B test model and verifies its size and SHA-256. Copy the file to the Android device and select it using **모델** in Hyperchat.
4. To run a search, send `/search Seoul Station` and select **검색 실행**. Hyperchat reports whether it verified a new result screen. The search action can run before any model is imported.

For a test without Naver Map, build `:fixture:assembleDebug` and install the supplied fixture on a test device. Open it instead of Naver Map and repeat the search; look for `검색 결과: 홍대 카페`. Fixture results are synthetic.

## Evidence and limits

| Check | Status |
| --- | --- |
| Android build and automated checks | Debug APK, 11 unit tests and Android lint |
| Android fixture search | Passed on Android 16 emulator with an independent result assertion |
| Naver Map screen guidance | Passed on Android 16 emulator with Naver Map 6.12.1.1 and imported Qwen3-0.6B |
| Naver Map search | Passed the independent result-screen assertion for `Seoul Station`. The overlay reported submission and asked the user to check the result. |
| Physical Galaxy / One UI | Pending |

Hyperchat observes Android accessibility nodes and its own in-memory diagnostic events. Naver Map is unmodified. This prototype uses Android's user-enabled Accessibility Service. Galaxy/One UI hardware and OEM integration have separate verification requirements.

[Architecture](docs/architecture.md) · [Design system](docs/design-system.md) · [Testing](docs/testing.md) · [Changelog](docs/CHANGELOG.md)
