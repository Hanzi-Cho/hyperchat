# Changelog

## Unreleased

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
