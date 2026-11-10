package dev.hanzicho.hyperchat.ui

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.hanzicho.hyperchat.HyperchatApp
import dev.hanzicho.hyperchat.R
import dev.hanzicho.hyperchat.core.ChatRequest
import dev.hanzicho.hyperchat.core.guidePrompt
import dev.hanzicho.hyperchat.core.parseRequest
import dev.hanzicho.hyperchat.platform.ScreenBridge
import dev.hanzicho.hyperchat.platform.ScreenSnapshot
import dev.hanzicho.hyperchat.platform.SearchOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayController(
    private val service: AccessibilityService,
    private val bridge: ScreenBridge,
) {
    private val theme = Theme(service)
    private val wm = service.getSystemService(WindowManager::class.java)
    private val ime = service.getSystemService(InputMethodManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val model get() = (service.application as HyperchatApp).model
    private var overlay: View? = null
    private var statusView: TextView? = null
    private var chat: LinearLayout? = null
    private var scroll: ScrollView? = null
    private var input: EditText? = null
    private var actionRow: LinearLayout? = null
    private var closed = false
    private var active: Job? = null
    private var pending: PendingSearch? = null
    private val transcript = ArrayDeque<Turn>()
    private var status = "지도 화면을 보면서 질문하거나 장소를 검색하세요."

    private data class Turn(val text: String, val reply: Boolean)
    private data class PendingSearch(val query: String, val packageName: String, val windowId: Int)

    fun showLauncher() {
        if (closed) return
        val running = active?.isActive == true
        val button = theme.button(if (running) "중지" else "Hyperchat", primary = true) {
            if (running) active?.cancel() else openPanel()
        }.apply {
            id = if (running) R.id.hc_stop else R.id.hc_open
            contentDescription = if (running) "Hyperchat 실행 중지" else "Hyperchat 열기"
        }
        replaceWindow(button, params(false).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = theme.dp(16)
            y = theme.dp(44)
        })
    }

    private fun openPanel() {
        if (closed) return
        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(theme.dp(16), theme.dp(12), theme.dp(16), theme.dp(12))
            background = theme.round(theme.surface, 24, border = true)
        }
        val titleRow = LinearLayout(service).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(theme.label("Hyperchat", 18f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(theme.button("모델") {
            pending = null
            showLauncher()
            service.startActivity(Intent(service, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        titleRow.addView(theme.button("닫기") {
            active?.cancel()
            pending = null
            showLauncher()
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = theme.dp(8) })
        container.addView(titleRow, LinearLayout.LayoutParams(-1, -2))
        container.addView(theme.label(
            if (model.status().installed) "온디바이스 모델 파일 준비됨" else "모델 없음 · 검색 명령 사용 가능",
            12f, theme.muted,
        ), rowParams(4))
        val replyList = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        val chatScroll = ScrollView(service).apply { addView(replyList); isFillViewport = true }
        container.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = theme.dp(8) })
        val field = EditText(service).apply {
            id = R.id.hc_prompt
            hint = "화면에 대해 질문하거나 /search 서울역"
            setTextColor(theme.text)
            setHintTextColor(theme.muted)
            textSize = 14f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            minHeight = theme.dp(48)
            background = theme.round(theme.elevated, 16, border = true)
            setPadding(theme.dp(12), 0, theme.dp(12), 0)
            setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_SEND) false else { submit(); true }
            }
        }
        val entryRow = LinearLayout(service).apply { gravity = Gravity.CENTER_VERTICAL }
        entryRow.addView(field, LinearLayout.LayoutParams(0, -2, 1f))
        entryRow.addView(theme.button("보내기", primary = true) { submit() }.apply { id = R.id.hc_send },
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = theme.dp(8) })
        container.addView(entryRow, rowParams(8))
        val actions = LinearLayout(service).apply { gravity = Gravity.END }
        container.addView(actions, rowParams(4))
        val statusLabel = theme.label(status, 12f, theme.muted).apply {
            id = R.id.hc_status
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        container.addView(statusLabel, rowParams(4))
        // Start unfocused: typing should not steal the observed app until requested.
        container.isFocusableInTouchMode = true
        container.requestFocus()
        replaceWindow(container, params(true).apply {
            gravity = Gravity.BOTTOM
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = (service.resources.displayMetrics.heightPixels * 0.38f).toInt()
                .coerceIn(theme.dp(220), theme.dp(384))
            y = theme.dp(20)
        })
        chat = replyList
        scroll = chatScroll
        statusView = statusLabel
        input = field
        actionRow = actions
        drawTranscript()
        drawActions()
    }

    private fun submit() {
        if (active?.isActive == true) return
        val raw = input?.text?.toString()?.trim().orEmpty()
        val request = parseRequest(raw)
        if (request is ChatRequest.Invalid) {
            updateStatus(request.reason)
            return
        }
        pending = null
        drawActions()
        val snapshot = bridge.capture()
        if (!supported(snapshot)) {
            updateStatus("네이버지도 화면을 연 뒤 다시 보내 주세요.")
            return
        }
        checkNotNull(snapshot)
        input?.text?.clear()
        hideKeyboard()
        turn(raw, reply = false)
        when (request) {
            is ChatRequest.Search -> {
                pending = PendingSearch(request.query, snapshot.packageName, snapshot.windowId)
                updateStatus("검색 실행을 누르면 현재 앱에 검색어를 입력합니다.")
                drawActions()
            }
            is ChatRequest.Guide -> askModel(request.question, snapshot)
            is ChatRequest.Invalid -> Unit
        }
    }

    private fun askModel(question: String, snapshot: ScreenSnapshot) {
        if (!model.status().installed) {
            turn("모델이 없어요. 위의 ‘모델’에서 .litertlm 파일을 가져와 주세요.", true)
            return
        }
        if (snapshot.nodes.isEmpty()) {
            updateStatus("이 화면에서 읽을 수 있는 요소가 없어요.")
            return
        }
        active = scope.launch {
            updateStatus("화면 정보를 바탕으로 기기에서 응답을 만드는 중…")
            try {
                val reply = model.respond(guidePrompt(question, snapshot))
                val current = bridge.capture()
                if (current?.packageName != snapshot.packageName || current.windowId != snapshot.windowId) {
                    updateStatus("화면이 바뀌었어요. 현재 화면에서 다시 질문해 주세요.")
                } else if (reply.isNotBlank()) {
                    turn(reply.take(1500), true)
                    updateStatus(if (current.nodes == snapshot.nodes) "온디바이스 응답 완료" else "온디바이스 응답 완료 · 질문 시점 화면 기준")
                } else {
                    updateStatus("모델 응답이 비어 있어요.")
                }
            } catch (cancelled: CancellationException) {
                updateStatus("응답을 중지했어요.")
                throw cancelled
            } catch (_: Exception) {
                updateStatus("모델을 실행하지 못했어요. 모델 파일과 기기 메모리를 확인해 주세요.")
            } finally {
                active = null
                drawActions()
            }
        }
        drawActions()
    }

    private fun executeSearch() {
        val plan = pending ?: return
        if (active?.isActive == true) return
        pending = null
        drawActions()
        val current = bridge.capture()
        if (current?.packageName != plan.packageName || current.windowId != plan.windowId) {
            updateStatus("화면이 바뀌어 실행하지 않았어요. 다시 요청해 주세요.")
            return
        }
        hideKeyboard()
        active = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            showLauncher()
            try {
                val result = bridge.search(plan.query, current)
                turn(result.message, true)
                updateStatus(when (result.outcome) {
                    SearchOutcome.VERIFIED -> "앱 화면에서 결과 확인"
                    SearchOutcome.SUBMITTED -> "검색어 제출 완료 · 결과 확인 필요"
                    SearchOutcome.STOPPED -> "검색을 완료하지 못했어요."
                })
            } catch (cancelled: CancellationException) {
                updateStatus("검색을 중지했어요.")
                throw cancelled
            } catch (_: Exception) {
                updateStatus("앱 화면을 확인할 수 없어 검색을 중단했어요.")
            } finally {
                active = null
                if (!closed) openPanel()
            }
        }
        active?.start()
    }

    private fun drawActions() {
        val row = actionRow ?: return
        row.removeAllViews()
        if (active?.isActive == true) {
            row.addView(theme.button("중지") { active?.cancel() }.apply { id = R.id.hc_stop })
        } else if (pending != null) {
            row.addView(theme.button("검색 실행", primary = true) { executeSearch() }.apply { id = R.id.hc_execute })
        }
        row.visibility = if (row.childCount == 0) View.GONE else View.VISIBLE
    }

    private fun supported(s: ScreenSnapshot?) =
        s?.packageName == "com.nhn.android.nmap" || s?.packageName == "dev.hanzicho.hyperchat.fixture"

    private fun turn(message: String, reply: Boolean) {
        transcript.addLast(Turn(message, reply))
        while (transcript.size > 12) transcript.removeFirst()
        drawTranscript()
    }

    private fun drawTranscript() {
        val list = chat ?: return
        list.removeAllViews()
        if (transcript.isEmpty()) {
            list.addView(theme.label("지금 열린 화면에서 무엇을 하고 싶나요?", color = theme.muted), rowParams(4))
        }
        transcript.forEach {
            list.addView(theme.label(
                (if (it.reply) "Hyperchat · " else "나 · ") + it.text,
                color = if (it.reply) theme.text else theme.muted,
            ).apply {
                if (it.reply) id = R.id.hc_reply
                setPadding(theme.dp(12), theme.dp(8), theme.dp(12), theme.dp(8))
                background = theme.round(theme.elevated, 12)
            }, rowParams(8))
        }
        scroll?.let { it.post { it.scrollTo(0, list.height) } }
    }

    private fun updateStatus(text: String) {
        status = text
        statusView?.text = text
    }

    private fun rowParams(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = theme.dp(top) }

    private fun params(focusable: Boolean) = WindowManager.LayoutParams(
        -2, -2,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
    }

    private fun hideKeyboard() {
        input?.let {
            ime.hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
    }

    private fun replaceWindow(view: View, layout: WindowManager.LayoutParams) {
        removeWindow()
        wm.addView(view, layout)
        overlay = view
    }

    private fun removeWindow() {
        val view = overlay
        if (view?.isAttachedToWindow == true) wm.removeViewImmediate(view)
        overlay = null
        statusView = null
        chat = null
        scroll = null
        input = null
        actionRow = null
    }

    fun close() {
        closed = true
        scope.cancel()
        removeWindow()
    }
}
