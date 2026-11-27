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
import java.util.Arrays;
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
                case "generic-guide": genericGuide(result); break;
                case "generic-action": genericAction(result); break;
                case "generic-plan": genericPlan(result); break;
                case "generic-settings": genericSettings(result); break;
                case "legacy": legacy(result, false); break;
                case "legacy-negative": legacy(result, true); break;
                case "tc-type": typeInCommanderForm(result); break;
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
        answerGuide(result, target, fixture ? "장소 검색" : "Search on NAVER Maps",
            fixture ? "Which button should I tap to search for a place? Quote its Korean label." :
                "Which button should I tap to search for a place?",
            fixture ? "fixture-model-guide" : "naver-model-guide");
    }

    private void genericGuide(Bundle result) throws Exception {
        launchCatalog();
        answerGuide(result, FIXTURE, "도서 검색",
            "책을 찾으려면 어떤 버튼을 눌러야 해? 버튼 이름을 화면에 나온 그대로 알려줘.", "catalog-model-guide");
    }

    private void legacy(Bundle result, boolean negative) throws Exception {
        LegacyScreen screen = LegacyScreen.forApp(args.getString("app", ""));
        Predicate<AccessibilityNodeInfo> entry = n -> screen.matches(n, screen.entry);
        Predicate<AccessibilityNodeInfo> destination = n -> screen.matches(n, screen.destination);
        requireVisible(entry, 10000, "original app entry " + screen.entry);
        if (find(destination) != null) throw new AssertionError("Destination is already visible before the request");
        openOverlay();
        if (negative) {
            send("Open " + screen.entry);
            requireVisible(n -> id(n, "hc_execute") && n.isEnabled(), 6000, "approval before editing");
            setDraft("계산기 앱 열어줘");
            requireVisible(n -> id(n, "hc_status") && string(n.getText()).contains("이전 동작을 취소"), 5000, "edited draft cancelled approval");
            if (find(n -> id(n, "hc_execute") && n.isEnabled()) != null) throw new AssertionError("Edited draft retained an earlier approval");
            result.putString("editedDraft", "previous approval cancelled");
            send("계산기 앱 열어줘");
            requireVisible(n -> id(n, "hc_status") && string(n.getText()).contains("현재 화면 안에서만"), 5000, "unsupported app launch explained");
            if (find(n -> id(n, "hc_execute") && n.isEnabled()) != null) throw new AssertionError("Unsupported request offered an action");
            requireVisible(entry, 3000, "original app still in its initial state");
            if (find(destination) != null) throw new AssertionError("Unsupported request moved the source screen");
            result.putString("screenshot", screenshot("legacy-" + screen.key + "-unsupported"));
            return;
        }
        if (screen.readControls) {
            send("/controls");
            AccessibilityNodeInfo answer = requireVisible(n -> id(n, "hc_reply") && !string(n.getText()).isEmpty(),
                6000, "read-only control list");
            String observed = null;
            for (String line : string(answer.getText()).split("\n")) {
                String label = line.replaceFirst("^•\\s*", "").trim();
                if (!label.isEmpty() && find(n -> n.isEnabled() && n.isClickable() && screen.matches(n, label)) != null) {
                    observed = label;
                    break;
                }
            }
            if (observed == null) throw new AssertionError("No listed control is visible in the original app");
            if (find(n -> id(n, "hc_execute") && n.isEnabled()) != null) throw new AssertionError("Reading offered a write action");
            requireVisible(entry, 3000, "entry still visible after listing controls");
            if (find(destination) != null) throw new AssertionError("Reading changed the source screen");
            result.putString("controlProof", observed);
            result.putString("controlsScreenshot", screenshot("legacy-" + screen.key + "-controls"));
        }
        send("Open " + screen.entry);
        AccessibilityNodeInfo approve = requireVisible(n -> id(n, "hc_execute") && n.isEnabled(), 8000, "user approval");
        requireVisible(n -> id(n, "hc_status") && string(n.getText()).contains(screen.entry), 4000, "preview names the requested control");
        requireVisible(entry, 4000, "original entry before approval");
        if (find(destination) != null) throw new AssertionError("Destination reached before approval");
        result.putString("approvalScreenshot", screenshot("legacy-" + screen.key + "-approval"));
        click(approve);
        AccessibilityNodeInfo reached = requireVisible(destination, 18000, "new label in original app " + screen.destination);
        result.putString("targetPackage", string(reached.getPackageName()));
        result.putString("destinationProof", screen.destination);
        requireVisible(n -> id(n, "hc_status") && "요청한 결과 화면을 열었습니다.".equals(string(n.getText())),
            10000, "verified status after the original app reached its destination");
        result.putString("resultScreenshot", screenshot("legacy-" + screen.key + "-result"));
    }

    private static final class LegacyScreen {
        final String key, pkg, entry, destination;
        final boolean readControls;

        LegacyScreen(String key, String pkg, String entry, String destination, boolean readControls) {
            this.key = key;
            this.pkg = pkg;
            this.entry = entry;
            this.destination = destination;
            this.readControls = readControls;
        }

        static LegacyScreen forApp(String app) {
            switch (app) {
                case "vlc": return new LegacyScreen(app, "org.videolan.vlc", "Audio", "ARTISTS", true);
                case "tc": return new LegacyScreen(app, "com.ghisler.android.TotalCommander", "Search...", "Find files", false);
                case "aimp": return new LegacyScreen(app, "com.aimp.player", "Main menu", "Managing playlists", true);
                case "anki": return new LegacyScreen(app, "com.ichi2.anki", "Card browser", "0 cards shown", false);
                default: throw new IllegalArgumentException("Supply -e app vlc, tc, aimp or anki");
            }
        }

        boolean matches(AccessibilityNodeInfo node, String label) {
            return pkg.equals(string(node.getPackageName())) && (label.equalsIgnoreCase(string(node.getText()).trim()) ||
                label.equalsIgnoreCase(string(node.getContentDescription()).trim()));
        }
    }

    private void typeInCommanderForm(Bundle result) throws Exception {
        String target = "com.ghisler.android.TotalCommander";
        Predicate<AccessibilityNodeInfo> nameField = n -> (target + ":id/name").equals(string(n.getViewIdResourceName())) && n.isEditable();
        AccessibilityNodeInfo field = requireVisible(nameField, 6000, "Find files name field");
        String initial = string(field.getText());
        String draft = "hyperchat-demo.txt";
        if (draft.equals(initial)) throw new AssertionError("Start with an unchanged search form");
        click(field);
        requireVisible(n -> nameField.test(n) && n.isFocused(), 5000, "focused search field");
        openOverlay();
        send("Type " + draft);
        AccessibilityNodeInfo approve = requireVisible(n -> id(n, "hc_execute") && n.isEnabled(), 6000, "explicit input approval");
        requireVisible(n -> id(n, "hc_status") && string(n.getText()).contains(draft), 4000, "input preview");
        requireVisible(n -> nameField.test(n) && initial.equals(string(n.getText())), 4000, "field unchanged before approval");
        click(approve);
        requireVisible(n -> nameField.test(n) && draft.equals(string(n.getText())), 10000, "exact value in original app after approval");
        requireVisible(n -> (target + ":id/okbtn").equals(string(n.getViewIdResourceName())), 3000, "search still unsubmitted");
        result.putString("fieldProof", target + ":id/name = " + draft);
        result.putString("screenshot", screenshot("legacy-tc-unsubmitted-input"));
    }

    private void answerGuide(Bundle result, String target, String searchLabel, String question, String screenshotName)
            throws Exception {
        requireVisible(n -> target.equals(string(n.getPackageName())) &&
            (string(n.getText()).equals(searchLabel) || string(n.getContentDescription()).equals(searchLabel)),
            10000, "target home search control");
        if (find(n -> id(n, "hc_prompt")) != null) {
            pressBack();
            requireIcon("hc_open");
        }
        openOverlay();
        send(question);
        requireVisible(n -> id(n, "hc_status") && string(n.getText()).startsWith("온디바이스 응답 완료"),
            95000, "real on-device response");
        AccessibilityNodeInfo reply = requireVisible(n -> {
            String text = string(n.getText());
            return id(n, "hc_reply") && text.contains(searchLabel) &&
                !text.contains("Visible controls:") && !text.contains("Question:");
        }, 4000, "reply citing the actual target control");
        requireVisible(n -> target.equals(string(n.getPackageName())), 5000, "target still beneath overlay");
        result.putString("reply", string(reply.getText()).substring(0, Math.min(320, string(reply.getText()).length())));
        result.putString("screenshot", screenshot(screenshotName));
    }

    private void genericAction(Bundle result) throws Exception {
        runCatalogSearch(result, "first", "달빛", "첫 번째", "달빛 정원");
        if (Boolean.parseBoolean(args.getString("repeat", "false"))) {
            runCatalogSearch(result, "repeat", "별빛 도서관", "일치하는", "별빛 도서관");
            requireVisible(n -> id(n, "hc_reply") && string(n.getText()).contains("저장된 검색 경로를 재사용했어요."),
                5000, "saved search path reused with the new query");
        }
        result.putString("screenshot", screenshot("catalog-search-and-open"));
    }

    private void genericSettings(Bundle result) throws Exception {
        String settingsPackage = "com.android.settings";
        String display = args.getString("label", "Display & touch");
        Intent settings = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (find(n -> id(n, "hc_prompt")) != null) {
            pressBack();
            requireIcon("hc_open");
        }
        getTargetContext().startActivity(settings);
        requireVisible(n -> settingsPackage.equals(string(n.getPackageName())) && display.equals(string(n.getText())),
            15000, "Display row in the real Android Settings app");
        openOverlay();
        if ("true".equals(args.getString("guide"))) {
            send("Which visible setting opens display options? Quote its exact title.");
            requireVisible(n -> id(n, "hc_status") && string(n.getText()).startsWith("온디바이스 응답 완료"),
                95000, "on-device response over real Android Settings");
            AccessibilityNodeInfo answer = requireVisible(n -> id(n, "hc_reply") &&
                string(n.getText()).contains(display), 5000, "local model names the real display entry");
            result.putString("reply", string(answer.getText()));
            result.putString("screenshot", screenshot("android-settings-model-guide"));
            return;
        }
        send("Open " + display);
        click(requireVisible(n -> id(n, "hc_execute") && n.isEnabled(), 6000, "explicit navigation approval"));
        AccessibilityNodeInfo destination = requireVisible(n -> {
            if (!settingsPackage.equals(string(n.getPackageName()))) return false;
            String label = string(n.getText());
            return label.equals("Display size and text") || label.equals("Brightness level") ||
                label.equals("Adaptive brightness");
        }, 12000, "real Android Display settings control");
        result.putString("destinationProof", string(destination.getText()));
        result.putString("screenshot", screenshot("android-settings-display"));
    }

    private void genericPlan(Bundle result) throws Exception {
        openCatalogComposer();
        String prompt = args.getString("prompt", "도서 검색 버튼으로 이동해 줘");
        long requested = SystemClock.elapsedRealtime();
        send(prompt);
        AccessibilityNodeInfo execute = requireVisible(n -> id(n, "hc_execute") && n.isEnabled(),
            95000, "explicit approval for the proposed catalog action");
        AccessibilityNodeInfo preview = requireVisible(n -> id(n, "hc_status") &&
            string(n.getText()).contains("‘도서 검색’ 열기"), 5000, "preview names the exact catalog action");
        requireCatalogEntry();
        Predicate<AccessibilityNodeInfo> titleField = n -> n.isEditable() &&
            (FIXTURE + ":id/catalog_title_field").equals(string(n.getViewIdResourceName()));
        if (find(titleField) != null) throw new AssertionError("Catalog search opened before approval");
        long approved = SystemClock.elapsedRealtime();
        click(execute);
        AccessibilityNodeInfo destination = requireVisible(titleField, 12000, "independent catalog search screen");
        result.putString("planApprovalMs", Long.toString(approved - requested));
        result.putString("planPreview", string(preview.getText()));
        result.putString("destinationProof", string(destination.getViewIdResourceName()));
        result.putString("screenshot", screenshot("catalog-model-plan"));
    }

    private void runCatalogSearch(Bundle result, String run, String query, String selection, String book) throws Exception {
        openCatalogComposer();
        long requested = SystemClock.elapsedRealtime();
        send(query + " 검색하고 " + selection + " 결과 열어줘");
        AccessibilityNodeInfo execute = requireVisible(n -> id(n, "hc_execute") && n.isEnabled(),
            6000, "explicit approval for search and open");
        if (find(n -> (FIXTURE + ":id/catalog_detail").equals(string(n.getViewIdResourceName()))) != null) {
            throw new AssertionError("Catalog detail was opened before approval");
        }
        long approved = SystemClock.elapsedRealtime();
        click(execute);
        AccessibilityNodeInfo detail = requireVisible(n ->
            (FIXTURE + ":id/catalog_detail").equals(string(n.getViewIdResourceName())) &&
            ("도서 상세: " + book).equals(string(n.getText())), 45000, "independent catalog detail after opening requested result");
        long opened = SystemClock.elapsedRealtime();
        requireVisible(n -> (FIXTURE + ":id/catalog_detail_query").equals(string(n.getViewIdResourceName())) &&
            ("검색어: " + query).equals(string(n.getText())), 5000, "exact query submitted to the catalog");
        requireVisible(n -> id(n, "hc_status") && "요청한 결과 화면을 열었습니다.".equals(string(n.getText())),
            5000, "verified search-and-open status");
        result.putString(run + "CatalogDetail", string(detail.getText()));
        result.putString(run + "ConfirmationMs", Long.toString(approved - requested));
        result.putString(run + "ApprovedToDetailMs", Long.toString(opened - approved));
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
        send("계산기 앱 열어줘");
        AccessibilityNodeInfo unsupported = requireVisible(n -> id(n, "hc_status") &&
            "현재 화면 안에서만 조작할 수 있어요. 먼저 대상 앱을 열어 주세요.".equals(string(n.getText())),
            5000, "unsupported app-open request");
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

    private void launchCatalog() {
        Intent intent = new Intent(getTargetContext(), CatalogActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        getTargetContext().startActivity(intent);
    }

    private void openCatalogComposer() throws Exception {
        launchCatalog();
        requireCatalogEntry();
        openOverlay();
    }

    private void requireCatalogEntry() {
        requireVisible(n -> (FIXTURE + ":id/catalog_entry").equals(string(n.getViewIdResourceName())) &&
            n.isClickable() && "도서 검색".equals(string(n.getText())), 10000, "catalog home search control");
    }

    private void composer(Bundle result) throws Exception {
        AccessibilityNodeInfo menu = openCleanFixtureComposer();
        result.putString("idleScreenshot", screenshot("composer-idle"));

        String closedLabel = string(menu.getContentDescription());
        click(menu);
        requireVisible(n -> id(n, "hc_menu") &&
            !string(n.getContentDescription()).equals(closedLabel), 3000, "expanded menu accessibility label");
        requireIcon("hc_gallery");
        requireIcon("hc_text_file");
        requireIcon("hc_model");
        requireStableComposerRow();
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

    private void requireStableComposerRow() {
        String[] ids = {"hc_gallery", "hc_text_file", "hc_model", "hc_prompt"};
        long end = SystemClock.elapsedRealtime() + 4000;
        Rect[] previous = null;
        do {
            Rect[] current = new Rect[ids.length];
            for (int i = 0; i < ids.length; i++) {
                String name = ids[i];
                AccessibilityNodeInfo node = find(n -> id(n, name));
                current[i] = node == null ? new Rect() : bounds(node);
            }
            Rect gallery = current[0];
            Rect text = current[1];
            Rect model = current[2];
            Rect prompt = current[3];
            if (Arrays.equals(previous, current) && !gallery.isEmpty() && !text.isEmpty() && !model.isEmpty() &&
                !prompt.isEmpty() && gallery.centerY() == text.centerY() && text.centerY() == model.centerY() &&
                gallery.right <= text.left && text.right <= model.left &&
                Math.max(gallery.bottom, Math.max(text.bottom, model.bottom)) <= prompt.top) return;
            previous = current;
            SystemClock.sleep(120);
        } while (SystemClock.elapsedRealtime() < end);
        throw new AssertionError("Menu icons must share one stable horizontal row above the prompt: " +
            Arrays.toString(previous));
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

    private AccessibilityNodeInfo openCleanFixtureComposer() throws Exception {
        if (find(n -> id(n, "hc_prompt")) != null) {
            pressBack();
            requireIcon("hc_open");
        }
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
