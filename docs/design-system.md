# Hyperchat design system

The overlay helps users perform one action and return to the app they were using. Keep the visible chat compact and preserve the app's context. The first design is a dark surface with a mint action color and text labels for state and recovery.

The [design token file](design-tokens.json) defines color, spacing, radii, and type. The Android palette is in [`colors.xml`](../app/src/main/res/values/colors.xml). Android uses dp for layout and sp for text.

| Use | Tokens |
| --- | --- |
| Overlay and input | `surface`, `elevated`, `outline`; 24 dp overlay and 16 dp input radii |
| Primary action | `accent` with `onAccent`; 12 dp radius |
| Text and helper text | `text` and `muted`; 18 sp title, 14 sp body, 12 sp status |
| Outcomes | `success`, `warning`, `error` with a short text label |

- Use a minimum 48 dp touch target. Let text wrap and controls expand under Android font scaling.
- Offer a clear `검색` action; show `중지` when a request can be cancelled and keep `닫기` reachable. Disable duplicate submit while an action is running.
- Put focus on the chat field on explicit open. Before interacting with the target app, ensure overlay focus cannot receive the target's input.
- Show the next useful action for permission, unsupported screen, timeout, or model-load failure. A result can be verified, empty, or unknown; do not express the difference only with color.
- Keep private app data out of screenshots and diagnostics. Check keyboard, rotation, font scaling, and obscured states on a real target; token checks alone do not verify usability.
