package dev.hanzicho.hyperchat.fixture;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.File;
import java.io.FileOutputStream;
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
                case "guide": guide(result); break;
                case "naver-search": search(result, MAP); break;
                case "fixture": search(result, FIXTURE); break;
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

    private void guide(Bundle result) throws Exception {
        String searchLabel = "Search on NAVER Maps";
        requireVisible(n -> MAP.equals(string(n.getPackageName())) &&
            (string(n.getText()).equals(searchLabel) || string(n.getContentDescription()).equals(searchLabel)),
            10000, "NAVER Maps home search control");
        openOverlay();
        send("Which button should I tap to search for a place?");
        requireVisible(n -> id(n, "hc_status") && string(n.getText()).startsWith("온디바이스 응답 완료"),
            95000, "real on-device response");
        AccessibilityNodeInfo reply = requireVisible(n -> {
            String text = string(n.getText());
            return id(n, "hc_reply") && text.contains(searchLabel) &&
                !text.contains("Visible controls:") && !text.contains("Question:");
        }, 4000, "reply citing the actual NAVER Maps control");
        requireVisible(n -> MAP.equals(string(n.getPackageName())), 5000, "NAVER Maps still beneath overlay");
        result.putString("reply", string(reply.getText()).substring(0, Math.min(320, string(reply.getText()).length())));
        result.putString("screenshot", screenshot("naver-model-guide"));
    }

    private void search(Bundle result, String target) throws Exception {
        if (FIXTURE.equals(target)) {
            Intent intent = new Intent(getTargetContext(), FixtureActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            getTargetContext().startActivity(intent);
        }
        requireVisible(n -> target.equals(string(n.getPackageName())) &&
            (FIXTURE.equals(target) || string(n.getText()).equals("Search on NAVER Maps") ||
                string(n.getContentDescription()).equals("Search on NAVER Maps")), 12000, "target home window");
        openOverlay();
        String query = args.getString("query", "Seoul Station");
        send("/search " + query);
        click(requireVisible(n -> id(n, "hc_execute"), 6000, "explicit search approval"));
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
        result.putString("target", target);
        result.putString("overlayStatus", string(status.getText()));
        result.putString("resultNode", string(proof.getViewIdResourceName()));
        result.putString("screenshot", screenshot(FIXTURE.equals(target) ? "fixture-search" : "naver-search"));
    }

    private void openOverlay() {
        if (find(n -> id(n, "hc_prompt")) == null) {
            click(requireVisible(n -> id(n, "hc_open"), 7000, "Hyperchat launcher"));
        }
        requireVisible(n -> id(n, "hc_prompt"), 16000, "Hyperchat chat overlay");
    }

    private void send(String text) {
        AccessibilityNodeInfo input = requireVisible(n -> id(n, "hc_prompt"), 3000, "chat field");
        Bundle value = new Bundle();
        value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, value)) {
            throw new AssertionError("Could not enter text through the actual chat field");
        }
        click(requireVisible(n -> id(n, "hc_send"), 5000, "send"));
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
