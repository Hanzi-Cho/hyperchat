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
adb shell am instrument -w -e mode fixture-guide dev.hanzicho.hyperchat.fixture.test/dev.hanzicho.hyperchat.fixture.HyperchatProbe
adb shell am instrument -w -e mode composer dev.hanzicho.hyperchat.fixture.test/dev.hanzicho.hyperchat.fixture.HyperchatProbe
adb shell am instrument -w -e mode bubble dev.hanzicho.hyperchat.fixture.test/dev.hanzicho.hyperchat.fixture.HyperchatProbe
adb shell am instrument -w -e mode bubble-delete dev.hanzicho.hyperchat.fixture.test/dev.hanzicho.hyperchat.fixture.HyperchatProbe
adb shell am instrument -w -e mode model-review dev.hanzicho.hyperchat.fixture.test/dev.hanzicho.hyperchat.fixture.HyperchatProbe
adb shell am instrument -w -e mode guide dev.hanzicho.hyperchat.fixture.test/dev.hanzicho.hyperchat.fixture.HyperchatProbe
adb shell am instrument -w -e mode naver-search dev.hanzicho.hyperchat.fixture.test/dev.hanzicho.hyperchat.fixture.HyperchatProbe
```

Run the probes serially on a single device. In a multi-device setup, use `adb -s <serial>`. Check for `outcome=PASS` and `INSTRUMENTATION_CODE: -1`. The Naver Map search probe separately reports the overlay status and asserts the query and result container in Naver Map. The probe runs with `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` so Android leaves Hyperchat enabled during assertions. It saves screenshots under the fixture's external files directory. Keep screenshots and diagnostics local if they contain third-party UI or user data.

The composer probe expects a fresh overlay and checks its idle row, horizontal menu layout and draft retention. For the text attachment probe, copy a non-empty UTF-8 `.txt` to the test device and make it visible in the system document picker's initial folder. Run `adb shell am instrument -w -e mode text-attachment -e file hyperchat-probe.txt dev.hanzicho.hyperchat.fixture.test/dev.hanzicho.hyperchat.fixture.HyperchatProbe` with its actual name. To check gallery OCR, select a test photo containing readable text and ask the local model about that text.

The bubble probe uses the default **채팅 열기** mappings for tap and upward swipe. It checks the collapsed size, long-press drag, tap to open, Back to minimize at the moved position, and swipe to reopen with the same draft. For the launcher or managed key entry point, run the command below while another app is visible, the service is enabled, and **시스템 바로가기** is set to **채팅 열기**. Verify the chat opens above that app.

```sh
adb shell am start -a dev.hanzicho.hyperchat.action.OPEN_SHORTCUT -n dev.hanzicho.hyperchat/.ui.ShortcutActivity
```

The delete probe uses the default system shortcut mapping and checks the stationary long press, top delete action, outside-tap cancellation at the original position, hidden state, and restoration via the explicit shortcut. The model-review probe checks the response to a natural-language app launch request outside supported apps and the confirmed natural-language place search in the fixture. It records UI timings for the place search path.

## Recorded environment

The local run used JDK 17, Android SDK 36, and an Android 16 API 36 x86-64 emulator. Naver Map was version `6.12.1.1` (`61201000`), running unmodified with ARM translation.

- **Guide.** The pinned Qwen3-0.6B CPU model returned the `Search on NAVER Maps` control, independently observed in the original app window.
- **Naver Map search.** `/search Seoul Station` opened the Naver Map result screen. The probe found the committed query and Naver Map result container after approval. Hyperchat timed out on its own result check and displayed `검색어 제출 완료 · 결과 확인 필요`.
- **Fixture search.** The overlay reported verified completion and the probe independently found the result in the fixture's own window.
- **Composer and attachments.** The Android 16 run checked the idle row, horizontal menu, draft retention and attachment removal. The system photo picker returned a test image, and the imported Qwen3-0.6B model answered with `ORBIT LANTERN` from its OCR text.
- **Floating bubble and mappings.** The Android 16 probe verified drag, tap, Back, upward swipe, and draft retention. The system accessibility button and explicit app shortcut both opened the overlay above the fixture. Changing the system mapping in Hyperchat settings was verified for all three actions.
- **Bubble deletion.** On the same emulator, the probe verified the top delete action, outside-tap cancellation, hiding the bubble, and restoration via the explicit app shortcut.

The system two-finger gesture and physical TC501 Key Programmer and Galaxy / One UI shortcut testing remain pending.

## Natural-language model review

- In Android Settings, `계산기 열어줘` left Settings in the foreground and returned the unsupported-screen message. The current app has no installed-app lookup, app launch or store search path.
- In the fixture, `홍대 카페 검색해줘` showed a search confirmation and produced an independently verified result after approval. In one Android 16 emulator run, input injection to confirmation was 2.606 s and approval to result was 7.419 s. These UI measurements include test overhead. The command parser and accessibility action handled the search without model inference.
- The pinned Qwen3-0.6B file is 343,490,560 bytes (327.6 MiB) with block-32 dynamic INT4 weights and FP32 activations. Hyperchat sends up to 10 visible control labels in a guide prompt and generates up to 128 output tokens using LiteRT-LM on CPU. The prompt is text and each turn starts a new conversation. [Qwen model card](https://huggingface.co/Qwen/Qwen3-0.6B) · [LiteRT export notes](https://huggingface.co/litert-community/Qwen3-0.6B)
- On the Android 16 x86-64 emulator, the first fixture guide initialized the CPU engine in 11.208 s, produced the first runtime text in 30.622 s after inference began, and completed inference in 35.352 s. Total cold runtime was 46.560 s. Two subsequent turns reused the same engine, with first runtime text in 12.109–28.351 s and completion in 20.927–41.684 s. The first answer named the visible `장소 검색` button, then incorrectly said it was not visible. The last answer named the button and repeated an instruction from the prompt.
- A previous TC501 CPU chat sample recorded 4.081 s to the first runtime text callback and 6.131 s to inference completion. The log for that request did not capture engine initialization. The overlay displays the answer when generation finishes.

## Physical-device model profiling

Build and install the app APK on the selected device, then start Profiler's OnDeviceAI capture for that device. Open Hyperchat, import a compatible `.litertlm` file, and run **온디바이스 모델 진단 실행**. This sends a short greeting prompt through the same CPU LiteRT-LM engine as overlay guidance and displays the generated response. For the same prompt with a GPU request, run **GPU 우선 진단 실행**. You can also ask a screen question from the overlay to capture a `chat` inference. Deterministic `/search` requests do not invoke the model.

For a local readout, look up the app UID and start the scoped stream before the run.

```sh
adb -s <serial> shell cmd package list packages -U --user current dev.hanzicho.hyperchat
adb -s <serial> logcat -b main --uid=<uid> -v threadtime HyperchatOnDeviceAI:I '*:S'
```

The `HyperchatOnDeviceAI` tag carries versioned JSON for import, engine initialization, and inference. Use `operation_id` to pair starts and ends, `request_id` for a generation and its engine initialization, and `engine_id` across requests that reuse the same engine. `t_ms` is device uptime including sleep. `elapsed_ms` measures the operation time. `first_output_ms` measures the delay to the runtime's first nonempty text callback, including conversation setup. The JSON contains no prompt, response, document path, or native error message.

The exact SHA-256 of the pinned Qwen3-0.6B artifact identifies its declared dynamic INT4 block-32 weights, FP32 model activations, and GPU composites with external weights. Unknown model files keep those fields unknown. These are export properties. `explicit_config` identifies the engine's selected backend and `fallback=unobserved` leaves operator-level placement unknown. [Pinned model card](https://huggingface.co/litert-community/Qwen3-0.6B/tree/8768ea43e6d801ee0d353e5036007e6790a2ba05) · [LiteRT-LM Kotlin guide](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.19.1/docs/api/kotlin/getting_started.md)

The GPU diagnostic retries once on CPU after a LiteRT-LM JNI error. The `fallback=app_retry` event uses the same request ID as both attempts and reports CPU as effective only after it returns a response. This is an application retry. Operator placement and an NPU-to-CPU fallback require delegate, runtime, or vendor evidence from the device.

On a TC501 with Android 15 and QCM6690, the pinned model imported successfully and returned a local answer through both the CPU and GPU diagnostics. The `fixture-guide` probe also passed on the physical device by asking through the overlay and checking for the test app's actual `장소 검색` control in the model response. The GPU run completed with the explicit GPU configuration. It did not trigger a retry. Naver Map verification above used the Android emulator.
