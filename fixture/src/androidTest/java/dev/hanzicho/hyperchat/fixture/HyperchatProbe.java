package dev.hanzicho.hyperchat.fixture;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class HyperchatProbe extends Instrumentation {
    private static final String HC = "dev.hanzicho.hyperchat";
    private static final String MAP = "com.nhn.android.nmap";
    private static final String FIXTURE = "dev.hanzicho.hyperchat.fixture";
    private Bundle args;
    private UiAutomation automation;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        args = arguments == null ? new Bundle() : arguments;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            // The test must observe Hyperchat without disconnecting its accessibility service.
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            automation.setServiceInfo(info);
            String mode = args.getString("mode", "fixture");
            switch (mode) {
                case "dump": result.putString("stream", dump()); break;
                case "guide": guide(result, MAP); break;
                case "fixture-guide": guide(result, FIXTURE); break;
                case "model-review": modelReview(result); break;
                case "naver-search": search(result, MAP); break;
                case "fixture": search(result, FIXTURE); break;
                case "composer": composer(result); break;
                case "bubble": bubble(result); break;
                case "bubble-delete": bubbleDelete(result); break;
                case "text-attachment": textAttachment(result); break;
                default: throw new AssertionError("Unsupported test mode");
            }
            result.putString("outcome", "PASS");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", failure.toString() + "\n" + safeDump());
            result.putString("outcome", "FAIL");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void guide(Bundle result, String target) throws Exception {
        boolean fixture = FIXTURE.equals(target);
        if (fixture) launchFixture();
        String searchLabel = fixture ? "장소 검색" : "Search on NAVER Maps";
        requireVisible(n -> target.equals(string(n.getPackageName())) &&
            (string(n.getText()).equals(searchLabel) || string(n.getContentDescription()).equals(searchLabel)),
            10000, "target home search control");
        if (find(n -> id(n, "hc_prompt")) != null) {
            pressBack();
            requireIcon("hc_open");
        }
        openOverlay();
        send(fixture ? "Which button should I tap to search for a place? Quote its Korean label." :
            "Which button should I tap to search for a place?");
        requireVisible(n -> id(n, "hc_status") && string(n.getText()).startsWith("온디바이스 응답 완료"),
            95000, "real on-device response");
        AccessibilityNodeInfo reply = requireVisible(n -> {
            String text = string(n.getText());
            return id(n, "hc_reply") && text.contains(searchLabel) &&
                !text.contains("Visible controls:") && !text.contains("Question:");
        }, 4000, "reply citing the actual target control");
        requireVisible(n -> target.equals(string(n.getPackageName())), 5000, "target still beneath overlay");
        result.putString("reply", string(reply.getText()).substring(0, Math.min(320, string(reply.getText()).length())));
        result.putString("screenshot", screenshot(fixture ? "fixture-model-guide" : "naver-model-guide"));
    }

    private void search(Bundle result, String target) throws Exception {
        search(result, target, false);
    }

    private void search(Bundle result, String target, boolean natural) throws Exception {
        if (FIXTURE.equals(target)) launchFixture();
        requireVisible(n -> target.equals(string(n.getPackageName())) &&
            (FIXTURE.equals(target) || string(n.getText()).equals("Search on NAVER Maps") ||
                string(n.getContentDescription()).equals("Search on NAVER Maps")), 12000, "target home window");
        openOverlay();
        String query = args.getString("query", natural ? "홍대 카페" : "Seoul Station");
        long requested = SystemClock.elapsedRealtime();
        send(natural ? query + " 검색해줘" : "/search " + query);
        AccessibilityNodeInfo execute = requireVisible(n -> id(n, "hc_execute"), 6000, "explicit search approval");
        long approved = SystemClock.elapsedRealtime();
        click(execute);
        AccessibilityNodeInfo status = requireVisible(n -> id(n, "hc_status") &&
            (string(n.getText()).equals("앱 화면에서 결과 확인") ||
                (MAP.equals(target) && string(n.getText()).equals("검색어 제출 완료 · 결과 확인 필요"))),
            55000, "search completion or submission");
        String expected = FIXTURE.equals(target) ? "검색 결과: " + query : query;
        AccessibilityNodeInfo proof = requireVisible(n ->
            target.equals(string(n.getPackageName())) && !n.isEditable() &&
            (string(n.getText()).equalsIgnoreCase(expected) ||
                string(n.getContentDescription()).equalsIgnoreCase(expected)),
            5000, "independent result in the original app");
        if (MAP.equals(target)) {
            requireVisible(n -> (MAP + ":id/v_search_result_list_group_component").equals(
                string(n.getViewIdResourceName())), 5000, "NAVER Maps result container");
        }
        requireHidden("hc_open", "hc_stop");
        result.putString("target", target);
        result.putString("overlayStatus", string(status.getText()));
        result.putString("resultNode", string(proof.getViewIdResourceName()));
        if (natural) {
            result.putString("confirmationMs", Long.toString(approved - requested));
            result.putString("approvedToResultMs", Long.toString(SystemClock.elapsedRealtime() - approved));
        }
        result.putString("screenshot", screenshot(natural ? "fixture-natural-search" :
            (FIXTURE.equals(target) ? "fixture-search" : "naver-search")));
    }

    private void modelReview(Bundle result) throws Exception {
        String settingsPackage = "com.android.settings";
        Intent settings = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getTargetContext().startActivity(settings);
        requireVisible(n -> settingsPackage.equals(string(n.getPackageName())), 10000, "Android Settings foreground");
        openOverlay();
        send("계산기 열어줘");
        AccessibilityNodeInfo unsupported = requireVisible(n -> id(n, "hc_status") &&
            "네이버지도 화면을 연 뒤 다시 보내 주세요.".equals(string(n.getText())), 5000, "unsupported app-open request");
        requireVisible(n -> settingsPackage.equals(string(n.getPackageName())), 5000, "Android Settings stays open");
        requireHidden("hc_reply", "hc_execute");
        result.putString("appOpenStatus", string(unsupported.getText()));
        setDraft("");
        pressBack();
        search(result, FIXTURE, true);
    }

    private void launchFixture() {
        Intent intent = new Intent(getTargetContext(), FixtureActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        getTargetContext().startActivity(intent);
    }

    private void composer(Bundle result) throws Exception {
        AccessibilityNodeInfo menu = openCleanFixtureComposer();
        result.putString("idleScreenshot", screenshot("composer-idle"));

        String closedLabel = string(menu.getContentDescription());
        click(menu);
        Rect gallery = bounds(requireIcon("hc_gallery"));
        Rect text = bounds(requireIcon("hc_text_file"));
        Rect model = bounds(requireIcon("hc_model"));
        Rect prompt = bounds(requireVisible(n -> id(n, "hc_prompt"), 3000, "prompt beneath menu"));
        if (gallery.isEmpty() || text.isEmpty() || model.isEmpty() ||
            gallery.centerY() != text.centerY() || text.centerY() != model.centerY() ||
            gallery.right > text.left || text.right > model.left ||
            Math.max(gallery.bottom, Math.max(text.bottom, model.bottom)) > prompt.top) {
            throw new AssertionError("Menu icons must share one horizontal row above the prompt: " +
                gallery + " " + text + " " + model + " prompt=" + prompt);
        }
        requireVisible(n -> id(n, "hc_menu") &&
            !string(n.getContentDescription()).equals(closedLabel), 3000, "expanded menu accessibility label");
        result.putString("expandedScreenshot", screenshot("composer-expanded"));

        String draft = "Hyperchat composer test draft";
        setDraft(draft);
        click(requireIcon("hc_menu"));
        requireVisible(n -> id(n, "hc_menu") && string(n.getContentDescription()).equals(closedLabel), 3000, "collapsed menu");
        requireHidden("hc_menu_options", "hc_gallery", "hc_text_file", "hc_model");
        requireVisible(n -> id(n, "hc_prompt") && string(n.getText()).equals(draft), 3000, "draft retained after collapse");
        requireVisible(n -> id(n, "hc_send") && n.isEnabled(), 3000, "send enabled with a draft");
        click(requireIcon("hc_menu"));
        requireIcon("hc_gallery");
        requireVisible(n -> id(n, "hc_prompt") && string(n.getText()).equals(draft), 3000, "draft retained after expansion");
        click(requireIcon("hc_menu"));
        requireVisible(n -> id(n, "hc_menu") && string(n.getContentDescription()).equals(closedLabel), 3000, "menu closed after draft check");
        setDraft("");
        requireVisible(n -> id(n, "hc_send") && !n.isEnabled(), 3000, "send disabled again after clearing draft");
    }

    private void bubble(Bundle result) throws Exception {
        launchFixture();
        requireVisible(n -> FIXTURE.equals(string(n.getPackageName())) && "장소 검색".equals(string(n.getText())),
            10000, "fixture home beneath the bubble");
        if (find(n -> id(n, "hc_prompt")) != null) pressBack();
        Rect initial = compactBubble();
        requireHidden("hc_prompt", "hc_menu", "hc_send", "hc_stop");

        Rect frame = fixtureWindowBounds();
        int startX = initial.centerX();
        int startY = initial.centerY();
        int targetX = startX < frame.centerX() ? Math.min(frame.right - dp(36), startX + dp(100)) :
            Math.max(frame.left + dp(36), startX - dp(100));
        int targetY = Math.max(frame.top + dp(112), Math.min(frame.bottom - dp(36), startY - dp(46)));
        int dragDuration = ViewConfiguration.getLongPressTimeout() + 240;
        shellInput("draganddrop " + startX + " " + startY + " " + targetX + " " + targetY + " " + dragDuration);
        requireVisible(n -> id(n, "hc_open") && Math.abs(bounds(n).centerX() - targetX) <= dp(4) &&
                Math.abs(bounds(n).centerY() - targetY) <= dp(4),
            4000, "bubble moves after long hold and drag");
        Rect moved = compactBubble();
        requireHidden("hc_prompt");

        shellInput("tap " + moved.centerX() + " " + moved.centerY());
        requireVisible(n -> id(n, "hc_prompt") && n.isEditable(), 5000, "tap opens the composer");
        requireHidden("hc_open");
        String draft = "Hyperchat bubble test draft";
        setDraft(draft);
        requireVisible(n -> id(n, "hc_send") && n.isEnabled(), 3000, "draft enables sending");

        pressBack();
        Rect minimized = compactBubble();
        requireHidden("hc_prompt");
        if (Math.abs(moved.centerX() - minimized.centerX()) > dp(2) ||
            Math.abs(moved.centerY() - minimized.centerY()) > dp(2)) {
            throw new AssertionError("Minimizing moved the bubble from " + moved + " to " + minimized);
        }

        int swipeY = minimized.centerY() - dp(84);
        if (swipeY <= frame.top) throw new AssertionError("Insufficient room to swipe above " + minimized);
        shellInput("swipe " + minimized.centerX() + " " + minimized.centerY() + " " +
            minimized.centerX() + " " + swipeY + " 140");
        requireVisible(n -> id(n, "hc_prompt") && n.isEditable() && draft.equals(string(n.getText())),
            5000, "swipe opens the composer with the draft retained");
        requireHidden("hc_open");
        setDraft("");
        pressBack();
        compactBubble();
        requireHidden("hc_prompt");
        result.putString("initialBubble", initial.toShortString());
        result.putString("movedBubble", moved.toShortString());
        result.putString("minimizedBubble", minimized.toShortString());
        result.putString("screenshot", screenshot("floating-hyperchat-bubble"));
    }

    private void bubbleDelete(Bundle result) throws Exception {
        launchFixture();
        requireVisible(n -> FIXTURE.equals(string(n.getPackageName())) && "장소 검색".equals(string(n.getText())),
            10000, "fixture home beneath the bubble");
        if (find(n -> id(n, "hc_prompt")) != null) pressBack();
        Rect initial = compactBubble();
        Rect frame = fixtureWindowBounds();

        Rect delete = holdForDelete(initial);
        if (delete.isEmpty() || delete.centerY() >= frame.top + frame.height() / 4) {
            throw new AssertionError("Expected a Delete button near the top, got " + delete);
        }
        result.putString("deleteScreenshot", screenshot("floating-hyperchat-delete"));

        shellInput("tap " + frame.centerX() + " " + frame.centerY());
        Rect restored = compactBubble();
        requireHidden("hc_delete_overlay", "hc_delete_button", "hc_prompt");
        if (!initial.equals(restored)) {
            throw new AssertionError("Cancel moved the bubble from " + initial + " to " + restored);
        }

        delete = holdForDelete(restored);
        shellInput("tap " + delete.centerX() + " " + delete.centerY());
        requireVisible(n -> FIXTURE.equals(string(n.getPackageName())) && n.isClickable() &&
            "장소 검색".equals(string(n.getText())) && find(button -> id(button, "hc_delete_button")) == null,
            5000, "fixture accessible after deletion");
        requireHidden("hc_delete_overlay", "hc_delete_button", "hc_open", "hc_prompt");
        result.putString("hiddenScreenshot", screenshot("floating-hyperchat-deleted"));

        Intent shortcut = new Intent("dev.hanzicho.hyperchat.action.OPEN_SHORTCUT")
            .setClassName(HC, "dev.hanzicho.hyperchat.ui.ShortcutActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getTargetContext().startActivity(shortcut);
        requireVisible(n -> id(n, "hc_prompt") && n.isEditable(), 7000, "explicit shortcut restores the composer");
        pressBack();
        compactBubble();
        requireHidden("hc_delete_overlay", "hc_delete_button", "hc_prompt");
    }

    private Rect holdForDelete(Rect bubble) throws Exception {
        int x = bubble.centerX();
        int y = bubble.centerY();
        int duration = ViewConfiguration.getLongPressTimeout() + 240;
        shellInput("swipe " + x + " " + y + " " + x + " " + y + " " + duration);
        return bounds(requireVisible(n -> id(n, "hc_delete_button") && n.isClickable() && n.isEnabled(),
            5000, "Delete button after long press"));
    }

    private Rect compactBubble() {
        Rect bubble = bounds(requireIcon("hc_open"));
        if (bubble.isEmpty() || bubble.width() > dp(64) || bubble.height() > dp(64) ||
            Math.abs(bubble.width() - bubble.height()) > dp(2)) {
            throw new AssertionError("Expected a compact square bubble, got " + bubble);
        }
        return bubble;
    }

    private Rect fixtureWindowBounds() {
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null || !FIXTURE.equals(string(root.getPackageName()))) continue;
            Rect frame = new Rect();
            window.getBoundsInScreen(frame);
            if (!frame.isEmpty()) return frame;
        }
        throw new AssertionError("Fixture window bounds unavailable");
    }

    private int dp(int value) {
        return Math.round(value * getTargetContext().getResources().getDisplayMetrics().density);
    }

    private void pressBack() throws Exception {
        shellInput("keyevent KEYCODE_BACK");
    }

    private void shellInput(String arguments) throws Exception {
        // Keep each gesture in one shell command so window synchronization cannot stretch its duration.
        try (InputStream output = new ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("input " + arguments))) {
            if (output.read() != -1) throw new AssertionError("Unexpected output from input " + arguments);
        }
    }

    /** The caller seeds a nonempty test file and brings its folder into the system picker. */
    private void textAttachment(Bundle result) throws Exception {
        String fileName = args.getString("file", "");
        if (!fileName.matches("[A-Za-z0-9_.-]{1,120}\\.txt")) {
            throw new AssertionError("Supply -e file <seeded-test-name.txt>");
        }
        openCleanFixtureComposer();
        String draft = "Hyperchat attachment test draft";
        setDraft(draft);
        click(requireIcon("hc_menu"));
        click(requireIcon("hc_text_file"));
        AccessibilityNodeInfo file = requireVisible(n -> string(n.getPackageName()).endsWith(".documentsui") &&
            fileName.equals(string(n.getText())), 20000, "seeded text file in the Android document picker");
        click(clickableFileRow(file));
        requireVisible(n -> id(n, "hc_attachment") && fileName.equals(string(n.getText())), 15000, "selected text attachment chip");
        requireVisible(n -> id(n, "hc_prompt") && string(n.getText()).equals(draft), 3000, "draft retained through document picker");
        setDraft("");
        requireVisible(n -> id(n, "hc_send") && n.isEnabled(), 3000, "send enabled by extracted attachment text");
        result.putString("screenshot", screenshot("composer-text-attachment"));
        click(requireIcon("hc_remove_attachment"));
        requireVisible(n -> id(n, "hc_send") && !n.isEnabled(), 3000, "send disabled after attachment removal");
        requireHidden("hc_attachment");
    }

    private AccessibilityNodeInfo openCleanFixtureComposer() {
        launchFixture();
        requireVisible(n -> FIXTURE.equals(string(n.getPackageName())) && "장소 검색".equals(string(n.getText())),
            10000, "fixture home screen");
        openOverlay();
        AccessibilityNodeInfo menu = requireIcon("hc_menu");
        AccessibilityNodeInfo input = requireVisible(n -> id(n, "hc_prompt") && n.isEditable(), 3000, "editable composer");
        AccessibilityNodeInfo send = requireIcon("hc_send");
        if (!menu.isEnabled() || send.isEnabled() || (!input.isShowingHintText() && !string(input.getText()).isEmpty())) {
            throw new AssertionError("Expected a clean composer with an available menu and disabled send");
        }
        requireHidden("hc_menu_options", "hc_gallery", "hc_text_file", "hc_model", "hc_attachment",
            "hc_reply", "hc_status", "hc_execute", "hc_stop", "hc_open");
        return menu;
    }

    private AccessibilityNodeInfo requireIcon(String name) {
        return requireVisible(n -> id(n, name) && "android.widget.ImageButton".equals(string(n.getClassName())) &&
            !string(n.getContentDescription()).trim().isEmpty() && n.isClickable(), 4000, "named icon " + name);
    }

    private void requireHidden(String... names) {
        for (String name : names) {
            if (find(n -> id(n, name)) != null) throw new AssertionError("Unexpected visible node: " + name);
        }
    }

    private static Rect bounds(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        return bounds;
    }

    private static AccessibilityNodeInfo clickableFileRow(AccessibilityNodeInfo file) {
        AccessibilityNodeInfo node = file;
        for (int i = 0; node != null && i < 6; i++, node = node.getParent()) {
            if (!string(node.getPackageName()).endsWith(".documentsui")) break;
            if (node.isVisibleToUser() && node.isEnabled() && node.isClickable()) return node;
        }
        throw new AssertionError("Could not find the clickable row for the seeded document");
    }

    private void openOverlay() {
        if (find(n -> id(n, "hc_prompt")) == null) {
            click(requireVisible(n -> id(n, "hc_open"), 7000, "Hyperchat launcher"));
        }
        requireVisible(n -> id(n, "hc_prompt"), 16000, "Hyperchat chat overlay");
    }

    private void send(String text) {
        setDraft(text);
        click(requireVisible(n -> id(n, "hc_send") && n.isEnabled(), 5000, "send"));
    }

    private void setDraft(String text) {
        AccessibilityNodeInfo input = requireVisible(n -> id(n, "hc_prompt"), 3000, "chat field");
        Bundle value = new Bundle();
        value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, value)) {
            throw new AssertionError("Could not enter text through the actual chat field");
        }
    }

    private static void click(AccessibilityNodeInfo node) {
        // A button can replace its accessibility window before Android acknowledges the click.
        // Each caller verifies the next observable state without repeating the action.
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private AccessibilityNodeInfo requireVisible(Predicate<AccessibilityNodeInfo> match, long timeout, String what) {
        long end = SystemClock.elapsedRealtime() + timeout;
        do {
            AccessibilityNodeInfo node = find(match);
            if (node != null) return node;
            SystemClock.sleep(120);
        } while (SystemClock.elapsedRealtime() < end);
        throw new AssertionError("Missing " + what);
    }

    private AccessibilityNodeInfo find(Predicate<AccessibilityNodeInfo> match) {
        ArrayDeque<AccessibilityNodeInfo> pending = roots();
        for (int visited = 0; !pending.isEmpty() && visited < 380; visited++) {
            AccessibilityNodeInfo node = pending.removeFirst();
            if (node.isVisibleToUser() && match.test(node)) return node;
            addChildren(pending, node);
        }
        return null;
    }

    private ArrayDeque<AccessibilityNodeInfo> roots() {
        ArrayDeque<AccessibilityNodeInfo> pending = new ArrayDeque<>();
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION ||
                window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null) {
                    if (window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) pending.addFirst(root);
                    else pending.addLast(root);
                }
            }
        }
        return pending;
    }

    private void addChildren(ArrayDeque<AccessibilityNodeInfo> pending, AccessibilityNodeInfo node) {
        for (int index = 0; index < Math.min(node.getChildCount(), 120); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) pending.addLast(child);
        }
    }

    private List<AccessibilityNodeInfo> nodes() {
        ArrayDeque<AccessibilityNodeInfo> pending = roots();
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        while (!pending.isEmpty() && nodes.size() < 380) {
            AccessibilityNodeInfo node = pending.removeFirst();
            nodes.add(node);
            addChildren(pending, node);
        }
        return nodes;
    }

    private String dump() {
        StringBuilder text = new StringBuilder("\n");
        for (AccessibilityNodeInfo node : nodes()) {
            String pkg = string(node.getPackageName());
            if ((!MAP.equals(pkg) && !HC.equals(pkg) && !FIXTURE.equals(pkg)) ||
                !node.isVisibleToUser() || node.isPassword()) continue;
            String id = string(node.getViewIdResourceName());
            String label = string(node.getText()) + " " + string(node.getContentDescription());
            if (id.isEmpty() && label.trim().isEmpty()) continue;
            text.append(pkg).append(" ").append(id).append(" ").append(node.getClassName())
                .append(" editable=").append(node.isEditable()).append(" clickable=").append(node.isClickable())
                .append(" ").append(label.substring(0, Math.min(90, label.length())).replace('\n', ' '))
                .append("\n");
            if (text.length() > 10000) break;
        }
        return text.toString();
    }

    private String safeDump() {
        if (automation == null) return "";
        try { return dump(); } catch (Exception ignored) { return ""; }
    }

    private String screenshot(String name) throws Exception {
        File dir = getTargetContext().getExternalFilesDir(null);
        if (dir == null) throw new AssertionError("No test screenshot directory");
        File output = new File(dir, name + ".png");
        Bitmap bitmap = automation.takeScreenshot();
        if (bitmap == null) throw new AssertionError("Test screenshot unavailable");
        try (FileOutputStream file = new FileOutputStream(output)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, file)) throw new AssertionError("Cannot save screenshot");
        } finally {
            bitmap.recycle();
        }
        return output.getAbsolutePath();
    }

    private static String string(CharSequence value) { return value == null ? "" : value.toString(); }
    private static boolean id(AccessibilityNodeInfo node, String name) {
        return (HC + ":id/" + name).equals(node.getViewIdResourceName());
    }
}
