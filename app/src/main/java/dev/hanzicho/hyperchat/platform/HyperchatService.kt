package dev.hanzicho.hyperchat.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.EditorInfo
import dev.hanzicho.hyperchat.ui.OverlayController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class HyperchatService : AccessibilityService(), ScreenBridge {
    @Volatile private var connected = false
    private var overlay: OverlayController? = null
    private var running: SearchRun? = null
    private val events = ArrayDeque<String>(EVENT_LIMIT)

    @android.annotation.TargetApi(33)
    override fun onCreateInputMethod(): InputMethod = object : InputMethod(this) {
        // This service does not change composing text when switching editors.
        override fun onFinishInput() = Unit
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        record("LIFECYCLE", "서비스 연결")
        overlay?.close()
        overlay = OverlayController(this, this).also { it.showLauncher() }
    }

    override fun onDestroy() {
        connected = false
        running?.interrupted = true
        overlay?.close()
        overlay = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        running?.interrupted = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val run = running ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.packageName?.toString() != run.target.packageName &&
            isOtherApplicationWindow(event.windowId, run.target.packageName)
        ) {
            run.interrupted = true
        }
        readForegroundRoot()?.use { foreground ->
            if (foreground.target.packageName != run.target.packageName) run.interrupted = true
        }
    }

    override fun capture(): ScreenSnapshot? {
        if (!connected) return null
        return readForegroundRoot()?.use { foreground ->
            val target = foreground.target
            if (!isSearchTarget(target.packageName)) {
                record("OBSERVE", "지원 목록 밖 앱의 창 정보 확인")
                return@use ScreenSnapshot(target.packageName, target.windowId, emptyList(), eventSnapshot())
            }

            val nodes = readTree(foreground.root, target.packageName).use { tree ->
                tree.nodes.filter(SearchNode::visible).map(SearchNode::screen)
            }
            record("OBSERVE", "지원 앱의 접근성 화면 정보 확인")
            ScreenSnapshot(target.packageName, target.windowId, nodes, eventSnapshot())
        }
    }

    override suspend fun search(query: String, expected: ScreenSnapshot): SearchResult =
        withContext(Dispatchers.Main.immediate) { searchOnMain(query, expected) }

    private suspend fun searchOnMain(query: String, expected: ScreenSnapshot): SearchResult {
        if (query.length > QUERY_LIMIT || query.any(Char::isISOControl)) {
            return SearchResult(SearchOutcome.STOPPED, "검색어는 한 줄에 80자 이내로 입력해 주세요.")
        }
        val term = SearchSemantics.displayText(query)
        if (term.isEmpty()) return SearchResult(SearchOutcome.STOPPED, "검색어를 입력해 주세요.")
        if (!connected) return SearchResult(SearchOutcome.STOPPED, "접근성 서비스 연결을 확인해 주세요.")
        if (running != null) return SearchResult(SearchOutcome.STOPPED, "이미 검색을 실행 중입니다.")

        val target = readForegroundRoot()?.use { it.target }
        if (target == null || !isSearchTarget(target.packageName)) {
            record("STOP", "지원 앱의 전면 창을 확인하지 못함")
            return SearchResult(SearchOutcome.STOPPED, "네이버지도 또는 Hyperchat 테스트 앱을 열어 주세요.")
        }
        if (target != AppTarget(expected.packageName, expected.windowId)) {
            record("STOP", "사용자가 확인한 앱 또는 창과 전면 창이 다름")
            return SearchResult(SearchOutcome.STOPPED, "확인한 앱이나 창이 바뀌었습니다. 화면을 다시 확인해 주세요.")
        }
        val run = SearchRun(target)
        running = run
        return try {
            executeSearch(run, term)
        } catch (stop: SearchStopped) {
            record("STOP", stop.message.orEmpty())
            SearchResult(SearchOutcome.STOPPED, stop.message.orEmpty())
        } catch (cancelled: CancellationException) {
            record("STOP", "검색 취소")
            throw cancelled
        } finally {
            running = null
        }
    }

    private suspend fun executeSearch(run: SearchRun, query: String): SearchResult {
        val hasInput = inspect(run) { tree ->
            SearchSemantics.findInput(run.target.packageName, tree.nodes, false) != null
        }
        if (!hasInput) {
            inspect(run) { tree ->
                val entry = SearchSemantics.findEntry(run.target.packageName, tree.nodes)
                    ?: stop("화면에서 검색을 여는 버튼을 하나로 확인하지 못했습니다.")
                act(run, tree, entry, AccessibilityNodeInfo.ACTION_CLICK)
            }
            record("ACTION", "검색 화면 열기")
            await(run, INPUT_TIMEOUT, "검색 입력란을 확인하지 못했습니다.", allowWindowChange = true) { tree ->
                SearchSemantics.findInput(run.target.packageName, tree.nodes, true) != null
            }
        }

        inspect(run) { tree ->
            val field = SearchSemantics.findInput(run.target.packageName, tree.nodes, !hasInput)
                ?: stop("검색 입력란이 바뀌었습니다.")
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
            }
            act(run, tree, field, AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
        record("ACTION", "검색 입력란에 검색어 입력")

        await(run, INPUT_TIMEOUT, "검색어가 입력란에 반영되었는지 확인하지 못했습니다.") { tree ->
            inputWithQuery(run, tree, query, !hasInput) != null
        }

        val (previousResult, needsEditor) = inspect(run) { tree ->
            val field = inputWithQuery(run, tree, query, !hasInput)
                ?: stop("제출 전에 검색어가 바뀌었습니다.")
            val baseline = SearchSemantics.resultSignature(run.target.packageName, tree.nodes, query)
            val submit = SearchSemantics.findSubmit(run.target.packageName, tree.nodes)
            if (submit != null) {
                act(run, tree, submit, AccessibilityNodeInfo.ACTION_CLICK)
                baseline to false
            } else {
                if (Build.VERSION.SDK_INT < 33) stop("검색 제출 버튼을 확인하지 못했습니다.")
                if (!tree.handles[field].isFocused && tree.nodes[field].screen.clickable) {
                    act(run, tree, field, AccessibilityNodeInfo.ACTION_CLICK)
                }
                baseline to true
            }
        }
        if (needsEditor) {
            await(run, INPUT_TIMEOUT, "대상 앱의 키보드에서 검색 동작을 확인하지 못했습니다.") { tree ->
                val field = inputWithQuery(run, tree, query, !hasInput) ?: return@await false
                submitSearchEditor(run, tree.handles[field], query)
            }
        }
        record("ACTION", "검색 제출")
        val verified = await(run, RESULT_TIMEOUT, allowWindowChange = true) { tree ->
            val evidence = SearchSemantics.resultSignature(run.target.packageName, tree.nodes, query)
            evidence != null && evidence != previousResult
        }
        if (!verified) {
            record("VERIFY", "검색 제출 후 결과 확인 시간 초과")
            return SearchResult(SearchOutcome.SUBMITTED, "검색어를 보냈어요. 결과는 지도 앱 화면에서 확인해 주세요.")
        }
        record("VERIFY", "화면에서 검색 결과 확인")
        return SearchResult(SearchOutcome.VERIFIED, "검색 결과 화면을 열었습니다.")
    }

    private fun submitSearchEditor(run: SearchRun, field: AccessibilityNodeInfo, query: String): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        ensureRunning(run)
        val foreground = readForegroundRoot() ?: return false
        foreground.use { ensureTarget(run, it.target, false) }
        if (!field.refresh() || field.windowId != run.target.windowId ||
            field.packageName?.toString() != run.target.packageName || isSensitive(field) || !field.isEnabled ||
            !field.isFocused || !field.isEditable || !field.isVisibleToUser ||
            SearchSemantics.displayText(field.text?.toString().orEmpty()) != query
        ) return false
        val method = inputMethod ?: return false
        val editor = method.currentInputEditorInfo
        val connection = method.currentInputConnection
        if (!method.currentInputStarted || editor?.packageName != run.target.packageName ||
            (editor.imeOptions and EditorInfo.IME_MASK_ACTION) != EditorInfo.IME_ACTION_SEARCH || connection == null
        ) return false
        connection.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
        return true
    }

    private fun inputWithQuery(run: SearchRun, tree: ScreenTree, query: String, afterEntry: Boolean): Int? =
        SearchSemantics.findInput(run.target.packageName, tree.nodes, afterEntry)?.takeIf { index ->
            val field = tree.nodes[index]
            !field.showingHint && SearchSemantics.displayText(field.screen.text) == query
        }

    private suspend fun await(
        run: SearchRun,
        timeout: Long,
        message: String? = null,
        allowWindowChange: Boolean = false,
        predicate: (ScreenTree) -> Boolean,
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (true) {
            currentCoroutineContext().ensureActive()
            ensureRunning(run)
            val found = readForegroundRoot()?.use { foreground ->
                ensureTarget(run, foreground.target, allowWindowChange)
                readTree(foreground.root, foreground.target.packageName).use { tree ->
                    if (!predicate(tree)) false else {
                        run.target = run.target.copy(windowId = tree.windowId)
                        true
                    }
                }
            } ?: false
            if (found) return true
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) {
                if (message != null) stop(message)
                return false
            }
            delay(minOf(POLL_INTERVAL, remaining))
        }
    }

    private fun <T> inspect(
        run: SearchRun,
        allowWindowChange: Boolean = false,
        block: (ScreenTree) -> T,
    ): T {
        ensureRunning(run)
        val foreground = readForegroundRoot() ?: stop("현재 앱의 전면 창을 확인하지 못했습니다.")
        return foreground.use {
            ensureTarget(run, foreground.target, allowWindowChange)
            readTree(foreground.root, foreground.target.packageName).use(block)
        }
    }

    private fun act(run: SearchRun, tree: ScreenTree, index: Int, action: Int, arguments: Bundle? = null) {
        ensureRunning(run)
        val foreground = readForegroundRoot() ?: stop("실행 직전에 전면 창을 확인하지 못했습니다.")
        foreground.use { ensureTarget(run, it.target, false) }
        val node = tree.handles[index]
        val observed = tree.nodes[index].screen
        if (!node.refresh() || node.windowId != run.target.windowId ||
            node.packageName?.toString() != run.target.packageName ||
            !node.isEnabled || !node.isVisibleToUser || isSensitive(node) ||
            label(node.viewIdResourceName) != observed.viewId || node.isEditable != observed.editable ||
            label(node.text) != observed.text || label(node.contentDescription) != observed.description ||
            node.actionList.none { it.id == action } ||
            !node.performAction(action, arguments)
        ) {
            stop("앱이 요청한 동작을 처리하지 않았습니다.")
        }
    }

    private fun ensureRunning(run: SearchRun) {
        if (!connected || run.interrupted) stop("검색이 중단되었습니다. 대상 앱을 다시 확인해 주세요.")
    }

    private fun ensureTarget(run: SearchRun, target: AppTarget, allowWindowChange: Boolean) {
        if (target.packageName != run.target.packageName) {
            stop("다른 앱으로 전환되어 검색을 중단했습니다.")
        }
        if (!allowWindowChange && target.windowId != run.target.windowId) {
            stop("대상 창이 바뀌어 검색을 중단했습니다.")
        }
    }

    private fun readForegroundRoot(): AppRoot? {
        val available = windows
        try {
            val application = available.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                ?: return null
            if (!hasApplicationFocus(application, available)) return null
            val appRoot = application.root ?: return null
            val target = appRoot.packageName?.toString()
            if (target == null || target == packageName || appRoot.windowId != application.id) {
                release(appRoot)
                return null
            }
            return AppRoot(AppTarget(target, appRoot.windowId), appRoot)
        } finally {
            available.forEach(::release)
        }
    }

    private fun hasApplicationFocus(application: AccessibilityWindowInfo, available: List<AccessibilityWindowInfo>): Boolean {
        val active = rootInActiveWindow
        try {
            val activeId = active?.windowId ?: available.firstOrNull { it.isActive || it.isFocused }?.id
                ?: return false
            if (activeId == application.id) return true
            val activeWindow = available.firstOrNull { it.id == activeId } ?: return false
            if (activeWindow.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) return true
            if (activeWindow.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return false
            if (active != null) return active.packageName?.toString() == packageName
            val overlayRoot = activeWindow.root ?: return false
            return try {
                overlayRoot.packageName?.toString() == packageName
            } finally {
                release(overlayRoot)
            }
        } finally {
            active?.let(::release)
        }
    }

    private fun isOtherApplicationWindow(windowId: Int, expectedPackage: String): Boolean {
        val available = windows
        try {
            val window = available.firstOrNull { it.id == windowId && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                ?: return false
            val root = window.root ?: return false
            try {
                val candidate = root.packageName?.toString() ?: return false
                return candidate != expectedPackage
            } finally {
                release(root)
            }
        } finally {
            available.forEach(::release)
        }
    }

    private fun readTree(root: AccessibilityNodeInfo, targetPackage: String): ScreenTree {
        val tree = ScreenTree(root.windowId)
        data class PendingNode(val node: AccessibilityNodeInfo, val parent: Int, val depth: Int)
        val pending = ArrayDeque<PendingNode>().apply { add(PendingNode(root, -1, 0)) }
        try {
            var visited = 0
            while (pending.isNotEmpty()) {
                val (node, parent, depth) = pending.removeFirst()
                visited++
                if (isSensitive(node) || node.packageName?.toString() != targetPackage) {
                    if (node !== root) release(node)
                    continue
                }
                val index = tree.nodes.size
                tree.handles.add(node)
                val actions = node.actionList
                tree.nodes.add(
                    SearchNode(
                        screen = ScreenNode(
                            text = label(node.text),
                            description = label(node.contentDescription),
                            viewId = label(node.viewIdResourceName),
                            clickable = node.isClickable && actions.any { it.id == AccessibilityNodeInfo.ACTION_CLICK },
                            editable = node.isEditable,
                        ),
                        parent = parent,
                        hint = label(node.hintText),
                        enabled = node.isEnabled,
                        visible = node.isVisibleToUser,
                        showingHint = node.isShowingHintText,
                        canSetText = actions.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT },
                    )
                )
                if (depth == TREE_DEPTH) continue
                for (childIndex in 0 until minOf(node.childCount, TREE_LIMIT)) {
                    if (visited + pending.size >= TREE_LIMIT) break
                    node.getChild(childIndex)?.let { pending.add(PendingNode(it, index, depth + 1)) }
                }
            }
            return tree
        } catch (failure: RuntimeException) {
            tree.close()
            throw failure
        } finally {
            pending.forEach { if (it.node !== root) release(it.node) }
        }
    }

    private fun record(category: String, message: String) = synchronized(events) {
        if (events.size == EVENT_LIMIT) events.removeFirst()
        events.addLast("[$category] $message")
    }

    private fun eventSnapshot(): List<String> = synchronized(events) { events.toList() }

    private data class AppTarget(val packageName: String, val windowId: Int)
    private class AppRoot(val target: AppTarget, val root: AccessibilityNodeInfo) : AutoCloseable {
        override fun close() = release(root)
    }
    private class SearchRun(var target: AppTarget, var interrupted: Boolean = false)
    private class SearchStopped(message: String) : Exception(message)

    private class ScreenTree(val windowId: Int) : AutoCloseable {
        val handles = mutableListOf<AccessibilityNodeInfo>()
        val nodes = mutableListOf<SearchNode>()

        override fun close() {
            // The root is owned by AppRoot.
            for (index in 1 until handles.size) release(handles[index])
        }
    }

    companion object {
        private const val QUERY_LIMIT = 80
        private const val TREE_LIMIT = 200
        private const val TREE_DEPTH = 24
        private const val LABEL_LIMIT = 240
        private const val EVENT_LIMIT = 30
        private const val INPUT_TIMEOUT = 2_000L
        private const val RESULT_TIMEOUT = 20_000L
        private const val POLL_INTERVAL = 160L

        private fun label(value: CharSequence?): String = value?.take(LABEL_LIMIT)?.toString().orEmpty()
        private fun isSensitive(node: AccessibilityNodeInfo): Boolean =
            node.isPassword || (Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive)

        private fun stop(message: String): Nothing = throw SearchStopped(message)

        @Suppress("DEPRECATION")
        private fun release(node: AccessibilityNodeInfo) = node.recycle()

        @Suppress("DEPRECATION")
        private fun release(window: AccessibilityWindowInfo) = window.recycle()
    }
}
