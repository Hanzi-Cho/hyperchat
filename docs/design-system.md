# Hyperchat design system

The collapsed overlay is a 48 dp circular chat icon. The open composer starts with Attach, a text field, and a paper-plane Send button. Attach expands a horizontal, scrollable row of circular options above the composer. Replies, status and action confirmation appear as needed.

The [design token file](design-tokens.json) defines color, spacing, radii, and type. The Android palette is in [`colors.xml`](../app/src/main/res/values/colors.xml). Android uses dp for layout and sp for text.

| Use | Tokens |
| --- | --- |
| Composer and replies | `surface`, `elevated`, `outline` with a 28 dp composer radius and 16 dp reply radius |
| Floating bubble | 48 dp circular touch target with a chat icon |
| Bubble deletion | Black scrim at 60% opacity and a top-centered action with `error` text on `elevated` |
| Icon action | 48 dp circular target, 24 dp Material icon, `accent` with `onAccent` for Send |
| Text and helper text | `text` and `muted` with 14 sp body, 12 sp status and 11 sp option labels |
| Outcomes | `success`, `warning`, `error` with a short text label |

- Use a minimum 48 dp touch target. Let text wrap and controls expand under Android font scaling.
- Tap or swipe up from the bubble to invoke its assigned action. Press and hold before dragging to reposition it. Restore the saved position within the available display area.
- A stationary long press opens the delete action. Tapping the scrim or pressing Back dismisses it. Deleting hides the bubble until the user restores it in settings or opens chat with a system shortcut.
- Keep the bubble clear of system bars, display cutouts and mandatory system gestures. The Android accessibility shortcut is available through the system navigation controls on supported devices. [Android gesture navigation](https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav#conflicts_with_home_or_quick-switch_gestures)
- Show `검색 실행` for search approval and `중지` for active requests. Disable duplicate submits while a request is running.
- Keep the text field and keyboard focus stable when the attachment menu toggles. Back or an outside touch dismisses the overlay when the composer is idle.
- Give icon buttons content descriptions. The bundled 24 dp vectors use Google's Material Icons. [Source](https://github.com/google/material-design-icons) · [License](../app/src/main/assets/licenses/google-material-icons.txt)
- Show the next useful action for permission, unsupported screen, timeout, or model-load failure. A result can be verified, empty, or unknown; do not express the difference only with color.
- Keep private app data out of screenshots and diagnostics. Check keyboard, rotation, font scaling, and obscured states on a real target; token checks alone do not verify usability.
