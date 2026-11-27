# Changelog

## 0.0.1 · 2026-11-27

- First signed [Android APK on GitHub Releases](https://github.com/Hanzi-Cho/hyperchat/releases/tag/v0.0.1), for Android 8.0+ on ARM64 and x86-64. The release includes a SHA-256 file for the installable APK.
- Rewrote the [quickstart](../README.md) around a model-free action in Android Settings, with expected results in VLC, Total Commander and AnkiDroid. The source build and device probes have separate entry points.

- Added an offline, model-free list of labeled controls and made it the first suggested task in settings.
- Target Android 16 (API 36). Added an in-app screen-access disclosure, a decline path, consent withdrawal, and service disconnection when consent is missing or withdrawn.
- Distinguished first-ranked and exact search results, restricted opening controls with unknown effects, and kept user-provided targets out of failure diagnostics.
- Editing the composer cancels a pending approval. Diagnostics in screen guidance are scoped to the current app and window.
- Added a version-pinned [offline test suite for legacy apps](legacy-app-scenarios.md), with APK inspection and independent assertions in the original apps.
- Generalized screen guidance and approved UI actions to the foreground app. Added exact-result opening, focused text entry, and bounded in-app navigation.
- Added a private search path cache that revalidates stable control IDs and supplies the current query at execution time.
- Added a movable 48 dp chat bubble with saved position, tap, and upward swipe.
- Added a stationary long-press delete action with a dimmed backdrop, outside-tap cancellation, and a persistent hide preference. Settings and the chat shortcut can restore the bubble.
- Added configurable actions for tap, swipe, and the system shortcut. The Android accessibility shortcut, launcher shortcut, and managed-device intent use the system shortcut action.
- Replaced the overlay header and transcript with a compact composer, Material Send icon and expandable attachment menu.
- Added local photo text recognition and UTF-8 text attachments. The same menu opens the local model settings.
- Added an Android chat overlay and a user-approved place search in Naver Map.
- Verified that `/search Seoul Station` opened the real Naver Map result screen in an Android 16 emulator. The overlay reports a submitted query separately when its own result check times out.
- Added local LiteRT-LM screen guidance and `.litertlm` import. An Android 16 emulator run returned the actual Naver Map home search control using the pinned Qwen3-0.6B model.
- Added overlay design tokens, an Android test fixture, and a reusable portfolio README skill.
- Added Profiler-compatible runtime events and a standalone LiteRT-LM diagnostic with an optional GPU request and explicit CPU retry. The pinned model's quantization and export options are reported from its digest.
