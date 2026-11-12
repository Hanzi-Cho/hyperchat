# Hyperchat

Hyperchat brings local AI guidance and place search to Naver Map through an Android chat overlay. Enable the accessibility service, open Naver Map, and tap the floating Hyperchat icon. A legacy app can expose a useful screen flow even when you have no integration API. Hyperchat reads visible controls for local guidance and uses a separate, explicitly approved search action.

```text
Clone https://github.com/Hanzi-Cho/hyperchat and read the Android build files and README.
Install the declared JDK/Android SDK dependencies, build the debug APK, and find the app and accessibility-service entry points.
On an Android test device with Naver Map installed, run the guide and search checks in docs/testing.md.
```

## Try the prototype

1. Open the project in Android Studio with JDK 17 and Android SDK 36, sync Gradle, and build a debug APK. For the command line, run `./gradlew :app:assembleDebug` (`.\gradlew.bat :app:assembleDebug` in PowerShell).
2. Install the debug APK on an Android test device and open Hyperchat. Enable the Hyperchat service in Android's Accessibility settings and install Naver Map separately. Close any Naver Map onboarding or promotional dialogs and return to the map home.
3. Tap the chat bubble, which starts at the bottom right. Open **+ → 로컬 모델** to import a compatible `.litertlm` file. The optional PowerShell script `./tools/get-model.ps1` fetches the pinned Qwen3-0.6B test model and verifies its size and SHA-256. Ask `Which button should I tap to search for a place?` and press the paper-plane icon.
4. To run a place search, send `/search Seoul Station` or `서울역 검색해줘` and select **검색 실행**. Hyperchat reports whether it verified a new result screen. This action can run before any model is imported.

**+** also opens the system gallery or text-file picker. Hyperchat extracts text from selected photos on the device and accepts UTF-8 text files. Ask about the attachment or press Send for a short summary.

For a test without Naver Map, build `:fixture:assembleDebug` and install the supplied fixture on a test device. Open it instead of Naver Map and repeat the search; look for `검색 결과: 홍대 카페`. Fixture results are synthetic.

## Open Hyperchat with a shortcut

- **Floating bubble.** Tap the icon or start a quick upward swipe on it. Hold and drag to move the bubble. A stationary long press dims the screen and shows **삭제하기** at the top. Tap outside or press Back to cancel. To bring the bubble back after deletion, use **Hyperchat 설정 → 동작 매핑 → 채팅 버블 다시 표시** or open chat with a system shortcut. Hyperchat remembers the position.
- **Action mapping.** In Hyperchat settings, use **동작 매핑** to assign tap, upward swipe, and the system shortcut independently to **채팅 열기**, **Hyperchat 설정 열기**, or **사용 안 함**. Each opens chat by default.
- **Android accessibility shortcut.** In Android Settings, open **Accessibility → Hyperchat**, then enable its shortcut. If gesture navigation offers **Finger swipe**, swipe up from the bottom with two fingers, or three with TalkBack enabled. With three-button navigation, use the accessibility button when available. Android handles its one-finger Home and app-switch gestures at the bottom edge. [Android accessibility shortcuts](https://support.google.com/accessibility/android/answer/7650693) · [Android gesture navigation](https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav#conflicts_with_home_or_quick-switch_gestures)
- **Launcher or Zebra TC501.** On a launcher that shows app shortcuts, press and hold the Hyperchat app icon and select **Hyperchat 바로가기**. On TC501, open **Settings → Key Programmer → PTT** (`LEFT_TRIGGER_2`) and select **Hyperchat 바로가기** if the picker lists it. This uses the same **시스템 바로가기** mapping as the Android accessibility shortcut. [Zebra TC501 product guide](https://www.ptsmobile.com/barcode-scanners/hardware/zebra/tc501/tc501-prg-en.pdf#page=67)

Zebra reserves the TC501 power key for system controls. Where Android offers **Hold for Assistant**, the system routes the power hold to its selected Assistant. [Zebra key mapping limits](https://techdocs.zebra.com/mx/keymappingmgr/#support-notes) · [Android Assistant service](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)

For a managed TC501, configure the **PTT** key in StageNow **KeyMappingMgr → Remap a key → Send Intent → On key up → Start activity**. Set the fields below. StageNow's Alert Button profile can bind a long press on a programmable key with MX 10.2 or later and compatible device policy. [StageNow key mapping](https://techdocs.zebra.com/stagenow/5-22/csp/keymap/#send-intent) · [StageNow Alert Button](https://techdocs.zebra.com/stagenow/5-18/stagingprofiles/#alertbutton)

| Field | Value |
| --- | --- |
| Key identifier | `LEFT_TRIGGER_2` |
| Package | `dev.hanzicho.hyperchat` |
| Class | `dev.hanzicho.hyperchat.ui.ShortcutActivity` |
| Action | `dev.hanzicho.hyperchat.action.OPEN_SHORTCUT` |

## Evidence and limits

| Check | Status |
| --- | --- |
| Android build and automated checks | Debug APK, 12 unit tests and Android lint |
| Floating bubble and shortcuts | Android 16 emulator verified drag, tap, Back, swipe, delete, outside-tap cancellation, and restoration from the app shortcut |
| Composer and attachments | Android 16 emulator checked the menu and draft, a system-picked text document, and a local-model answer from photo OCR |
| Android fixture search | Passed on Android 16 emulator with an independent result assertion |
| Naver Map screen guidance | Passed on Android 16 emulator with Naver Map 6.12.1.1 and imported Qwen3-0.6B |
| Naver Map search | Passed the independent result-screen assertion for `Seoul Station`. The overlay reported submission and asked the user to check the result. |
| Physical shortcut integration | TC501 Key Programmer and Galaxy / One UI pending |

Hyperchat observes Android accessibility nodes and its own in-memory diagnostic events. Naver Map is unmodified. This prototype uses Android's user-enabled Accessibility Service. Galaxy/One UI hardware and OEM integration have separate verification requirements.

Natural-language app discovery, app launch, and store search are outside the current implementation. Local AI answers questions about visible controls in supported apps. Place search uses a separate command parser and the user approves the action.

[Architecture](docs/architecture.md) · [Design system](docs/design-system.md) · [Testing](docs/testing.md) · [Changelog](docs/CHANGELOG.md)
