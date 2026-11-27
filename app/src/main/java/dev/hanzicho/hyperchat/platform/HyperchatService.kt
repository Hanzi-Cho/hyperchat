package dev.hanzicho.hyperchat.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.InputMethod
import android.app.KeyguardManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.EditorInfo
import dev.hanzicho.hyperchat.HyperchatApp
import dev.hanzicho.hyperchat.core.Direction
import dev.hanzicho.hyperchat.core.ResultSelection
import dev.hanzicho.hyperchat.core.ScreenCommand
import dev.hanzicho.hyperchat.ui.OverlayController
import dev.hanzicho.hyperchat.ui.ShortcutTrigger
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
    private val paths by lazy { ScreenPathCache(this) }
    private val events = ArrayDeque<ScreenEvent>(EVENT_LIMIT)
    private var shortcutController: AccessibilityButtonController? = null
    private val openFromShortcut: () -> Unit = { overlay?.runShortcut(ShortcutTrigger.SYSTEM) }
    private val showFloatingBubble: () -> Unit = { overlay?.restoreLauncher() }
    private val disconnectService: () -> Unit = {
        connected = false
        running?.interrupted = true
        synchronized(events) { events.clear() }
        overlay?.close()
        overlay = null
        disableSelf()
    }
    private val shortcutCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) = openFromShortcut()
    }

    @android.annotation.TargetApi(33)
    override fun onCreateInputMethod(): InputMethod = object : InputMethod(this) {
        // This service does not change composing text when switching editors.
        override fun onFinishInput() = Unit
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as HyperchatApp
        if (!app.screenAccessAccepted) {
            android.widget.Toast.makeText(this, dev.hanzicho.hyperchat.R.string.consent_required,
                android.widget.Toast.LENGTH_LONG).show()
            disableSelf()
            return
        }
        app.disconnectScreenAccess = disconnectService
        connected = true
        record("LIFECYCLE", "서비스 연결")
        overlay?.close()
        overlay = OverlayController(this, this).also { it.showLauncher() }
        shortcutController?.unregisterAccessibilityButtonCallback(shortcutCallback)
        shortcutController = accessibilityButtonController.also {
            it.registerAccessibilityButtonCallback(shortcutCallback)
        }
        (application as HyperchatApp).apply {
            systemShortcut = openFromShortcut
            restoreBubble = showFloatingBubble
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onDisplayChanged()
    }

    override fun onDestroy() {
        connected = false
        running?.interrupted = true
        shortcutController?.unregisterAccessibilityButtonCallback(shortcutCallback)
        shortcutController = null
        val app = application as HyperchatApp
        if (app.disconnectScreenAccess === disconnectService) app.disconnectScreenAccess = null
        if (app.systemShortcut === openFromShortcut) app.systemShortcut = null
        if (app.restoreBubble === showFloatingBubble) app.restoreBubble = null
        overlay?.close()
        overlay = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        running?.interrupted = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val run = running ?: return
        if (!connected || run.interrupted) return
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
            val nodes = readTree(foreground.root, target.packageName).use { tree ->
                tree.nodes.filter(SearchNode::visible).map { node ->
                    if (node.screen.editable) node.screen.copy(text = node.hint) else node.screen
                }
            }
            record("OBSERVE", "전면 앱의 접근성 화면 정보 확인", target)
            ScreenSnapshot(target.packageName, target.windowId, nodes, eventSnapshot(target))
        }
    }

    override suspend fun execute(command: ScreenCommand, expected: ScreenSnapshot): SearchResult =
        withContext(Dispatchers.Main.immediate) { executeOnMain(command, expected) }

    private suspend fun executeOnMain(command: ScreenCommand, expected: ScreenSnapshot): SearchResult {
        if (!connected) return SearchResult(SearchOutcome.STOPPED, "접근성 서비스 연결을 확인해 주세요.")
        if (running != null) return SearchResult(SearchOutcome.STOPPED, "이미 화면 동작을 실행 중입니다.")

        val target = readForegroundRoot()?.use { it.target }
        if (target == null || target != AppTarget(expected.packageName, expected.windowId)) {
            record("STOP", "사용자가 확인한 앱 또는 창과 전면 창이 다름")
            return SearchResult(SearchOutcome.STOPPED, "확인한 앱이나 창이 바뀌었습니다. 화면을 다시 확인해 주세요.")
        }
        val run = SearchRun(target)
        running = run
        return try {
            when (command) {
                is ScreenCommand.Search -> executeSearch(run, SearchSemantics.displayText(command.query), command.result)
                is ScreenCommand.Open -> openInScreen(run, command.label)
                is ScreenCommand.Type -> typeInFocusedField(run, command.text)
                is ScreenCommand.Navigate -> navigate(run, command.direction)
                is ScreenCommand.Invalid -> stop(command.reason)
            }
        } catch (stop: SearchStopped) {
            record("STOP", "실행 조건 또는 결과를 확인하지 못함")
            SearchResult(SearchOutcome.STOPPED, stop.message.orEmpty())
        } catch (cancelled: CancellationException) {
            record("STOP", "화면 동작 취소")
            throw cancelled
        } finally {
            running = null
        }
    }

    private suspend fun executeSearch(run: SearchRun, query: String, selection: ResultSelection): SearchResult {
        if (query.isBlank() || query.length > QUERY_LIMIT || query.any(Char::isISOControl)) {
            stop("검색어는 한 줄에 80자 이내로 입력해 주세요.")
        }
        val path = inspect(run) { tree ->
            paths.key(run.target.packageName, fingerprint(run, tree), PathIntent.SEARCH)?.let(::SearchPath)
        }
        try {
            return searchWithPath(run, query, selection, path)
        } finally {
            if (path?.stale == true) paths.forget(path.key)
        }
    }

    private suspend fun searchWithPath(
        run: SearchRun, query: String, selection: ResultSelection, path: SearchPath?,
    ): SearchResult {
        val hasInput = inspect(run) { tree ->
            findInput(run, tree, false) != null
        }
        if (!hasInput) {
            inspect(run) { tree ->
                val action = CachedScreenAction.CLICK
                val live = findEntry(run, tree)
                val entry = path?.pick(tree, action, live) ?: live
                    ?: stop("화면에서 검색을 여는 버튼을 하나로 확인하지 못했습니다.")
                path?.remember(tree, action, entry)
                act(run, tree, entry, AccessibilityNodeInfo.ACTION_CLICK)
            }
            record("ACTION", "검색 화면 열기")
            await(run, INPUT_TIMEOUT, "검색 입력란을 확인하지 못했습니다.", allowWindowChange = true) { tree ->
                findInput(run, tree, true) != null
            }
        }

        inspect(run) { tree ->
            val action = CachedScreenAction.SET_TEXT
            val live = findInput(run, tree, !hasInput)
            val field = path?.pick(tree, action, live) ?: live
                ?: stop("검색 입력란이 바뀌었습니다.")
            path?.remember(tree, action, field)
            setText(run, tree, field, query)
        }
        record("ACTION", "검색 입력란에 검색어 입력")

        await(run, INPUT_TIMEOUT, "검색어가 입력란에 반영되었는지 확인하지 못했습니다.") { tree ->
            inputWithQuery(run, tree, query, !hasInput) != null
        }

        var previousDestination: String? = null
        val (previousResult, needsEditor) = inspect(run) { tree ->
            val field = inputWithQuery(run, tree, query, !hasInput)
                ?: stop("제출 전에 검색어가 바뀌었습니다.")
            previousDestination = ForegroundSemantics.resultDestination(run.target.packageName, tree.nodes, query, selection)?.title
            val baseline = resultSignature(run, tree, query, previousDestination)
            val action = CachedScreenAction.SUBMIT
            val live = findSubmit(run, tree)
            val submit = path?.pick(tree, action, live) ?: live
            if (submit != null) {
                path?.remember(tree, action, submit)
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
        val timeout = if (isSearchTarget(run.target.packageName)) RESULT_TIMEOUT else GENERAL_RESULT_TIMEOUT
        var observedTitle: String? = null
        val verified = await(run, timeout, allowWindowChange = true) { tree ->
            observedTitle = ForegroundSemantics.resultDestination(run.target.packageName, tree.nodes, query, selection)?.title
            val evidence = resultSignature(run, tree, query, observedTitle)
            evidence != null && evidence != previousResult && (observedTitle == null || observedTitle != previousDestination)
        }
        if (!verified) {
            record("VERIFY", "검색 제출 후 결과 확인 시간 초과")
            return SearchResult(SearchOutcome.SUBMITTED, "검색어를 보냈어요. 결과는 현재 앱 화면에서 확인해 주세요.")
        }
        val result = if (selection == ResultSelection.NONE) {
            SearchResult(SearchOutcome.VERIFIED, "검색 결과 화면을 열었습니다.")
        } else if (observedTitle == null) {
            SearchResult(SearchOutcome.SUBMITTED, "검색 결과가 표시됐어요. 안전하게 열 항목을 확인할 수 없어 화면에서 직접 선택해 주세요.")
        } else {
            openVisibleTarget(run, requireNotNull(observedTitle)) { nodes ->
                ForegroundSemantics.resultDestination(run.target.packageName, nodes, query, selection)
                    ?.takeIf { it.title == observedTitle }?.index
            }
        }
        if (result.outcome == SearchOutcome.VERIFIED) path?.save()
        record("VERIFY", "화면에서 검색 결과 확인")
        return result.copy(cacheHit = path?.reused == true)
    }

    private fun findEntry(run: SearchRun, tree: ScreenTree): Int? =
        legacyMatch(run.target.packageName) { SearchSemantics.findEntry(it, tree.nodes) }
            ?: ForegroundSemantics.searchEntry(tree.nodes)

    private fun findInput(run: SearchRun, tree: ScreenTree, afterEntry: Boolean): Int? =
        legacyMatch(run.target.packageName) { SearchSemantics.findInput(it, tree.nodes, afterEntry) }
            ?: ForegroundSemantics.searchField(tree.nodes, afterEntry)

    private fun findSubmit(run: SearchRun, tree: ScreenTree): Int? =
        legacyMatch(run.target.packageName) { SearchSemantics.findSubmit(it, tree.nodes) }
            ?: ForegroundSemantics.searchSubmit(tree.nodes)

    private inline fun legacyMatch(targetPackage: String, find: (String) -> Int?): Int? =
        if (isSearchTarget(targetPackage)) find(targetPackage) else null

    private fun resultSignature(run: SearchRun, tree: ScreenTree, query: String, destinationTitle: String?): String? {
        val target = run.target.packageName
        val legacy = if (isSearchTarget(target)) SearchSemantics.resultSignature(target, tree.nodes, query) else null
        return legacy ?: destinationTitle ?: ForegroundSemantics.resultSignature(tree.nodes, query)
    }

    private fun setText(run: SearchRun, tree: ScreenTree, field: Int, value: String) {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        act(run, tree, field, AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private suspend fun typeInFocusedField(run: SearchRun, value: String): SearchResult {
        if (value.isBlank() || value.length > 500 || value.any(Char::isISOControl)) stop("입력할 텍스트를 확인해 주세요.")
        val observed = inspect(run) { tree ->
            val field = tree.nodes.indices.singleOrNull { i ->
                tree.nodes[i].let { it.enabled && it.visible && it.screen.focused && it.screen.editable && it.canSetText }
            } ?: stop("현재 앱에서 선택된 편집 가능한 입력란을 하나로 확인하지 못했어요.")
            setText(run, tree, field, value)
            tree.nodes[field].screen.viewId
        }
        await(run, INPUT_TIMEOUT, "입력란에 텍스트가 들어갔는지 확인하지 못했어요.") { tree ->
            tree.nodes.singleOrNull { it.screen.focused && it.visible && it.screen.editable && it.screen.viewId == observed }
                ?.screen?.text == value
        }
        return SearchResult(SearchOutcome.VERIFIED, "선택된 입력란에 텍스트를 넣었습니다.")
    }

    private suspend fun openInScreen(run: SearchRun, label: String): SearchResult {
        if (inspect(run) { tree ->
                ForegroundSemantics.tabSelected(run.target.packageName, tree.nodes, label) ||
                    ForegroundSemantics.searchFormVisible(run.target.packageName, tree.nodes, label) ||
                    ForegroundSemantics.aimpMenuVisible(run.target.packageName, tree.nodes, label)
            }) return SearchResult(SearchOutcome.VERIFIED, "요청한 화면이 이미 열려 있어요.")
        fun find(nodes: List<SearchNode>): Int? {
            val index = ForegroundSemantics.openTarget(run.target.packageName, nodes, label)
            if (index == null && ForegroundSemantics.hasMatchingControl(nodes, label)) {
                stop("이 항목을 눌렀을 때 화면 이동만 일어나는지 확인할 수 없어요. 앱에서 직접 선택해 주세요.")
            }
            return index
        }
        val (visible, canSearch) = inspect(run) { tree ->
            (find(tree.nodes) != null) to (findEntry(run, tree) != null || findInput(run, tree, false) != null)
        }
        if (visible) return openVisibleTarget(run, label, ::find)
        if (ForegroundSemantics.requiresVisibleOpenTarget(run.target.packageName)) {
            stop("이 화면에서 안전하게 열 수 있는 메뉴를 확인하지 못했어요. 앱에서 직접 선택해 주세요.")
        }
        if (canSearch) return executeSearch(run, label, ResultSelection.EXACT)
        repeat(MAX_SCROLLS) {
            if (!scroll(run, true)) stop("화면에서 ‘$label’ 항목을 찾지 못했어요.")
            if (inspect(run) { find(it.nodes) != null }) {
                return openVisibleTarget(run, label, ::find)
            }
        }
        stop("화면을 확인했지만 ‘$label’ 항목을 찾지 못했어요.")
    }

    private suspend fun openVisibleTarget(run: SearchRun, label: String, find: (List<SearchNode>) -> Int?): SearchResult {
        var sourceId = ""
        val (previousTitles, previouslyAtDestination) = inspect(run) { tree ->
            val index = find(tree.nodes)
            if (index == null) stop("‘$label’과 일치하는 항목을 하나로 확인하지 못했어요.")
            sourceId = tree.nodes[index].screen.viewId
            val state = navigationState(run, tree, label, sourceId)
            act(run, tree, index, AccessibilityNodeInfo.ACTION_CLICK)
            state
        }
        val verified = await(run, GENERAL_RESULT_TIMEOUT, allowWindowChange = true) { tree ->
            val (titles, atDestination) = navigationState(run, tree, label, sourceId)
            titles.any { it !in previousTitles } || (atDestination && !previouslyAtDestination)
        }
        return if (verified) SearchResult(SearchOutcome.VERIFIED, "요청한 결과 화면을 열었습니다.")
        else SearchResult(SearchOutcome.SUBMITTED, "항목을 눌렀어요. 열린 화면을 확인해 주세요.")
    }

    private fun navigationState(run: SearchRun, tree: ScreenTree, label: String, sourceId: String): Pair<Set<ScreenNode>, Boolean> {
        if (ForegroundSemantics.isAimpMenuRequest(run.target.packageName, label)) {
            return emptySet<ScreenNode>() to ForegroundSemantics.aimpMenuVisible(run.target.packageName, tree.nodes, label)
        }
        return ForegroundSemantics.destinationEvidence(tree.nodes, label) to
            (ForegroundSemantics.tabSelected(run.target.packageName, tree.nodes, label) ||
                ForegroundSemantics.searchFormVisible(run.target.packageName, tree.nodes, label) ||
                ForegroundSemantics.cardBrowserVisible(run.target.packageName, tree.nodes, sourceId))
    }

    private suspend fun navigate(run: SearchRun, direction: Direction): SearchResult {
        if (direction != Direction.BACK) {
            if (!scroll(run, direction == Direction.SCROLL_DOWN)) stop("스크롤할 영역을 확인하지 못했어요.")
            return SearchResult(SearchOutcome.VERIFIED, "화면을 스크롤했습니다.")
        }
        val before = inspect(run) { tree ->
            val index = ForegroundSemantics.backButton(tree.nodes) ?: stop("현재 앱의 뒤로 가기 버튼을 확인하지 못했어요.")
            act(run, tree, index, AccessibilityNodeInfo.ACTION_CLICK)
            tree.nodes.toList()
        }
        await(run, INPUT_TIMEOUT, "이전 화면으로 이동했는지 확인하지 못했어요.", allowWindowChange = true) { it.nodes != before }
        return SearchResult(SearchOutcome.VERIFIED, "이전 화면으로 이동했습니다.")
    }

    private suspend fun scroll(run: SearchRun, forward: Boolean): Boolean {
        val before = inspect(run) { tree ->
            val index = tree.nodes.indices.singleOrNull { i ->
                tree.nodes[i].let { it.visible && it.enabled && if (forward) it.scrollForward else it.scrollBackward }
            } ?: return@inspect null
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            act(run, tree, index, action)
            tree.nodes.toList()
        } ?: return false
        return await(run, INPUT_TIMEOUT) { it.nodes != before }
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
        findInput(run, tree, afterEntry)?.takeIf { index ->
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
                    val matched = predicate(tree)
                    if (matched) run.target = run.target.copy(windowId = tree.windowId)
                    matched
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
            label(node.className) != tree.nodes[index].className || node.isCheckable != tree.nodes[index].checkable ||
            node.collectionItemInfo?.rowIndex != tree.nodes[index].collectionRow ||
            node.collectionItemInfo?.columnIndex != tree.nodes[index].collectionColumn ||
            label(node.text) != observed.text || label(node.contentDescription) != observed.description ||
            node.actionList.none { it.id == action } ||
            !node.performAction(action, arguments)
        ) {
            stop("앱이 요청한 동작을 처리하지 않았습니다.")
        }
    }

    private fun ensureRunning(run: SearchRun) {
        if (!connected || run.interrupted || getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            stop("화면 동작이 중단되었습니다. 대상 앱을 다시 확인해 주세요.")
        }
    }

    private fun ensureTarget(run: SearchRun, target: AppTarget, allowWindowChange: Boolean) {
        if (target.packageName != run.target.packageName) {
            stop("다른 앱으로 전환되어 화면 동작을 중단했습니다.")
        }
        if (!allowWindowChange && target.windowId != run.target.windowId) {
            stop("대상 창이 바뀌어 화면 동작을 중단했습니다.")
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
        val searchEditorId by lazy(LazyThreadSafetyMode.NONE) { activeSearchEditorId(targetPackage) }
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
                            clickable = node.isEnabled && node.isClickable && actions.any { it.id == AccessibilityNodeInfo.ACTION_CLICK },
                            editable = node.isEditable,
                            focused = node.isFocused,
                        ),
                        parent = parent,
                        hint = label(node.hintText),
                        enabled = node.isEnabled,
                        visible = node.isVisibleToUser,
                        showingHint = node.isShowingHintText,
                        canSetText = actions.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT },
                        scrollForward = actions.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD },
                        scrollBackward = actions.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD },
                        className = label(node.className),
                        checkable = node.isCheckable,
                        selected = node.isSelected,
                        checked = checked(node),
                        collectionRow = node.collectionItemInfo?.rowIndex,
                        collectionColumn = node.collectionItemInfo?.columnIndex,
                        searchEditor = node.isEditable && node.isFocused && node.viewIdResourceName != null &&
                            node.viewIdResourceName == searchEditorId,
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

    private fun activeSearchEditorId(targetPackage: String): String? {
        if (Build.VERSION.SDK_INT < 33) return null
        val method = inputMethod ?: return null
        val editor = method.currentInputEditorInfo ?: return null
        if (!method.currentInputStarted || editor.packageName != targetPackage || editor.fieldId <= 0 ||
            (editor.imeOptions and EditorInfo.IME_MASK_ACTION) != EditorInfo.IME_ACTION_SEARCH) return null
        return try {
            packageManager.getResourcesForApplication(targetPackage).getResourceName(editor.fieldId)
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            null
        } catch (_: android.content.res.Resources.NotFoundException) {
            null
        }
    }

    private fun record(category: String, message: String, target: AppTarget? = running?.target) = synchronized(events) {
        if (!connected) return@synchronized
        if (events.size == EVENT_LIMIT) events.removeFirst()
        events.addLast(ScreenEvent(target, "[$category] $message"))
    }

    private fun eventSnapshot(target: AppTarget): List<String> = synchronized(events) {
        events.filter { it.target == target }.map { it.message }
    }

    private data class AppTarget(val packageName: String, val windowId: Int)
    private data class ScreenEvent(val target: AppTarget?, val message: String)
    private class AppRoot(val target: AppTarget, val root: AccessibilityNodeInfo) : AutoCloseable {
        override fun close() = release(root)
    }
    private class SearchRun(var target: AppTarget, var interrupted: Boolean = false)
    private class SearchStopped(message: String) : Exception(message)

    private fun fingerprint(run: SearchRun, tree: ScreenTree): String? = fingerprint(run.target.packageName, tree)

    private fun fingerprint(targetPackage: String, tree: ScreenTree): String? =
        ScreenPathCache.fingerprint(targetPackage, tree.nodes.filter(SearchNode::visible).map(SearchNode::screen))

    private inner class SearchPath(val key: PathKey) {
        private val previous = paths.read(key).orEmpty()
        private val observed = mutableListOf<CachedScreenStep>()
        private var consumed = 0
        private var recordable = true
        var stale = false
            private set
        val reused get() = previous.isNotEmpty() && !stale && observed == previous && consumed == previous.size

        fun pick(tree: ScreenTree, action: CachedScreenAction, live: Int?): Int? {
            if (stale) return null
            val step = previous.getOrNull(consumed) ?: return null
            val candidate = tree.nodes.indices.singleOrNull { index ->
                val node = tree.nodes[index]
                node.visible && node.enabled && node.screen.viewId == step.resourceId
            }
            if (step.action != action || step.screenFingerprint != fingerprint(key.packageName, tree) ||
                candidate == null || candidate != live) {
                stale = true
                return null
            }
            consumed++
            return candidate
        }

        fun remember(tree: ScreenTree, action: CachedScreenAction, index: Int) {
            val id = tree.nodes[index].screen.viewId
            val screen = fingerprint(key.packageName, tree)
            if (screen == null || !ScreenPathCache.isStableResource(key.packageName, id)) {
                recordable = false
            } else {
                observed.add(CachedScreenStep(action, id, screen))
            }
        }

        fun save() {
            if (!recordable) return
            if (paths.remember(key, observed)) stale = false
        }
    }

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
        private const val GENERAL_RESULT_TIMEOUT = 5_000L
        private const val MAX_SCROLLS = 3
        private const val POLL_INTERVAL = 160L

        private fun label(value: CharSequence?): String = value?.take(LABEL_LIMIT)?.toString().orEmpty()
        private fun isSensitive(node: AccessibilityNodeInfo): Boolean =
            node.isPassword || (Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive)

        @Suppress("DEPRECATION")
        private fun checked(node: AccessibilityNodeInfo): Boolean =
            if (Build.VERSION.SDK_INT >= 36) node.checked == AccessibilityNodeInfo.CHECKED_STATE_TRUE else node.isChecked

        private fun stop(message: String): Nothing = throw SearchStopped(message)

        @Suppress("DEPRECATION")
        private fun release(node: AccessibilityNodeInfo) = node.recycle()

        @Suppress("DEPRECATION")
        private fun release(window: AccessibilityWindowInfo) = window.recycle()
    }
}
