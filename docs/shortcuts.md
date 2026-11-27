# Hyperchat shortcuts

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
