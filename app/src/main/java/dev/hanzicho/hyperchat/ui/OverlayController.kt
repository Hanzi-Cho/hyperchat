package dev.hanzicho.hyperchat.ui

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.hanzicho.hyperchat.HyperchatApp
import dev.hanzicho.hyperchat.R
import dev.hanzicho.hyperchat.attachment.AttachmentKind
import dev.hanzicho.hyperchat.attachment.AttachmentPicker
import dev.hanzicho.hyperchat.attachment.AttachmentResult
import dev.hanzicho.hyperchat.attachment.DraftAttachment
import dev.hanzicho.hyperchat.core.ChatRequest
import dev.hanzicho.hyperchat.core.attachmentPrompt
import dev.hanzicho.hyperchat.core.guidePrompt
import dev.hanzicho.hyperchat.core.parseRequest
import dev.hanzicho.hyperchat.platform.ScreenBridge
import dev.hanzicho.hyperchat.platform.ScreenSnapshot
import dev.hanzicho.hyperchat.platform.SearchOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
    private val keyguard = service.getSystemService(KeyguardManager::class.java)
    private val shortcuts = ShortcutPreferences(service)
    private val launcher = FloatingLauncher(service, wm, theme)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val model get() = (service.application as HyperchatApp).model
    private var overlay: View? = null
    private var deleteOverlay: View? = null
    private var panel: PanelViews? = null
    private var closed = false
    private var active: Job? = null
    private var pending: PendingSearch? = null
    private var draft = ""
    private var reply: String? = null
    private var status: String? = null
    private var menuExpanded = false
    private var attachment: DraftAttachment? = null
    private var pickerOpen = false
    private var picker: AutoCloseable? = null

    private data class PendingSearch(val query: String, val packageName: String, val windowId: Int)

    private data class PanelViews(
        val input: EditText,
        val send: ImageButton,
        val menuButton: ImageButton,
        val menu: View,
        val attachmentRow: View,
        val attachmentName: TextView,
        val removeAttachment: ImageButton,
        val reply: TextView,
        val replyScroll: View,
        val status: TextView,
        val actions: LinearLayout,
    )

    private enum class ComposerAction(val id: Int, val icon: Int, val label: Int) {
        GALLERY(R.id.hc_gallery, R.drawable.hc_ic_photo, R.string.composer_gallery),
        TEXT(R.id.hc_text_file, R.drawable.hc_ic_file, R.string.composer_text_file),
        MODEL(R.id.hc_model, R.drawable.hc_ic_model, R.string.composer_model),
    }

    fun showLauncher() {
        if (closed) return
        val running = active?.isActive == true
        if (!running && shortcuts.isBubbleHidden) {
            removeWindow()
            return
        }
        val button = theme.iconButton(
            if (running) R.drawable.hc_ic_stop else R.drawable.hc_ic_chat,
            service.getString(if (running) R.string.bubble_stop else R.string.bubble_open),
            primary = true,
        ) {
            if (running) active?.cancel() else runShortcut(ShortcutTrigger.TAP)
        }.apply {
            id = if (running) R.id.hc_stop else R.id.hc_open
        }
        val layout = params(false)
        launcher.configure(
            button, layout,
            swipeUp = { if (!running) runShortcut(ShortcutTrigger.SWIPE_UP) },
            hold = if (running) null else ::showDeleteMode,
            cancelHold = ::hideDeleteMode,
        )
        replaceWindow(button, layout)
    }

    internal fun restoreLauncher() {
        if (overlay == null && !pickerOpen && !keyguard.isKeyguardLocked) showLauncher()
    }

    fun onDisplayChanged() {
        overlay?.takeIf { panel == null && it is ImageButton }?.let(launcher::reposition)
    }

    internal fun runShortcut(trigger: ShortcutTrigger) {
        if (closed || pickerOpen || keyguard.isKeyguardLocked || active?.isActive == true) return
        when (shortcuts.action(trigger)) {
            ShortcutAction.OPEN_CHAT -> {
                openPanel()
                if (trigger == ShortcutTrigger.SYSTEM) shortcuts.isBubbleHidden = false
            }
            ShortcutAction.OPEN_SETTINGS -> openSettings()
            ShortcutAction.NONE -> Unit
        }
    }

    private fun showDeleteMode() {
        if (deleteOverlay != null || overlay?.id != R.id.hc_open || keyguard.isKeyguardLocked) return

        // Leave the bubble window attached so the initial pointer can still become a drag.
        val mode = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) hideDeleteMode()
                return true
            }
        }.apply {
            id = R.id.hc_delete_overlay
            isFocusableInTouchMode = true
            addView(View(service).apply {
                setBackgroundColor(service.getColor(R.color.hc_scrim))
                contentDescription = service.getString(R.string.bubble_cancel_delete)
                setOnClickListener { hideDeleteMode() }
            }, FrameLayout.LayoutParams(-1, -1))
            addView(theme.button(service.getString(R.string.bubble_delete)) {
                shortcuts.isBubbleHidden = true
                removeWindow()
            }.apply {
                id = R.id.hc_delete_button
                setTextColor(theme.error)
            }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = theme.dp(16)
            })
        }
        val layout = params(true).apply {
            width = -1
            height = -1
            flags = flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            if (Build.VERSION.SDK_INT >= 30) {
                fitInsetsTypes = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                isFitInsetsIgnoringVisibility = true
            }
        }
        wm.addView(mode, layout)
        deleteOverlay = mode
    }

    private fun hideDeleteMode() {
        val mode = deleteOverlay ?: return
        deleteOverlay = null
        wm.removeViewImmediate(mode)
    }

    private fun openPanel() {
        if (closed || pickerOpen || panel != null) return
        val container = ComposerWindow().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(theme.dp(12), 0, theme.dp(12), 0)
        }
        val response = theme.label("").apply {
            id = R.id.hc_reply
            setPadding(theme.dp(12), theme.dp(10), theme.dp(12), theme.dp(10))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val responseScroll = object : ScrollView(service) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val cap = theme.dp(156)
                val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) cap
                    else minOf(cap, MeasureSpec.getSize(heightMeasureSpec))
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
            }
        }.apply {
            background = theme.round(theme.surface, 16, border = true)
            addView(response)
        }
        container.addView(responseScroll, rowParams(0))
        val statusLabel = theme.label("", 12f, theme.muted).apply {
            id = R.id.hc_status
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(theme.dp(12), theme.dp(7), theme.dp(12), theme.dp(7))
            background = theme.round(theme.surface, 12)
        }
        container.addView(statusLabel, rowParams(4, -2))
        val actions = LinearLayout(service).apply { gravity = Gravity.END }
        container.addView(actions, rowParams(4))
        val menu = createMenu()
        container.addView(menu, rowParams(4, -2))

        val attachmentName = theme.label("").apply {
            id = R.id.hc_attachment
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(theme.dp(12), 0, theme.dp(4), 0)
        }
        val removeAttachment = theme.iconButton(R.drawable.hc_ic_close, service.getString(R.string.composer_remove_attachment)) {
            attachment = null
            reply = null
            status = null
            render()
        }.apply { id = R.id.hc_remove_attachment }
        val attachmentRow = LinearLayout(service).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = theme.round(theme.surface, 16)
            addView(attachmentName, LinearLayout.LayoutParams(0, -2, 1f))
            addView(removeAttachment, iconParams())
        }
        container.addView(attachmentRow, rowParams(4))

        val field = EditText(service).apply {
            id = R.id.hc_prompt
            setHint(R.string.composer_hint)
            setTextColor(theme.text)
            setHintTextColor(theme.muted)
            textSize = 14f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            minHeight = theme.dp(48)
            background = null
            setPadding(theme.dp(10), 0, theme.dp(10), 0)
            setText(draft)
            setSelection(length())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    draft = s?.toString().orEmpty()
                    updateButtons()
                }
            })
            setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_SEND) false else { submit(); true }
            }
        }
        val menuButton = theme.iconButton(R.drawable.hc_ic_add, service.getString(R.string.composer_attach)) {
            menuExpanded = !menuExpanded
            renderMenu()
        }.apply { id = R.id.hc_menu }
        val send = theme.iconButton(R.drawable.hc_ic_send, service.getString(R.string.composer_send), primary = true) {
            submit()
        }.apply { id = R.id.hc_send }
        val entryRow = LinearLayout(service).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(theme.dp(4), theme.dp(4), theme.dp(4), theme.dp(4))
            background = theme.round(theme.surface, 28, border = true)
            addView(menuButton, iconParams())
            addView(field, LinearLayout.LayoutParams(0, -2, 1f))
            addView(send, iconParams())
        }
        container.addView(entryRow, rowParams(4))
        container.isFocusableInTouchMode = true
        container.requestFocus()
        replaceWindow(container, params(true).apply {
            gravity = Gravity.BOTTOM
            width = WindowManager.LayoutParams.MATCH_PARENT
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            y = theme.dp(20)
        })
        panel = PanelViews(field, send, menuButton, menu, attachmentRow, attachmentName, removeAttachment,
            response, responseScroll, statusLabel, actions)
        render()
    }

    private fun createMenu(): View = HorizontalScrollView(service).apply {
        id = R.id.hc_menu_options
        isHorizontalScrollBarEnabled = false
        background = theme.round(theme.surface, 24, border = true)
        addView(LinearLayout(service).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(theme.dp(6), theme.dp(6), theme.dp(6), theme.dp(6))
            ComposerAction.entries.forEach { action ->
                val name = service.getString(action.label)
                addView(LinearLayout(service).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(theme.iconButton(action.icon, name) { selectAction(action) }.apply { id = action.id }, iconParams())
                    addView(theme.label(name, 11f, theme.muted).apply {
                        gravity = Gravity.CENTER
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    })
                }, LinearLayout.LayoutParams(theme.dp(68), -2))
            }
        })
    }

    private fun selectAction(action: ComposerAction) {
        if (active?.isActive == true || pickerOpen) return
        menuExpanded = false
        pending = null
        hideKeyboard()
        when (action) {
            ComposerAction.GALLERY -> pickAttachment(AttachmentKind.IMAGE)
            ComposerAction.TEXT -> pickAttachment(AttachmentKind.TEXT)
            ComposerAction.MODEL -> openSettings()
        }
    }

    private fun openSettings() {
        hideDeleteMode()
        menuExpanded = false
        pending = null
        reply = null
        status = null
        hideKeyboard()
        if (panel != null) showLauncher()
        service.startActivity(Intent(service, SettingsActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        ))
    }

    private fun pickAttachment(kind: AttachmentKind) {
        removeWindow()
        pickerOpen = true
        val selection = AttachmentPicker.launch(service, kind) { result ->
            if (closed || !pickerOpen) return@launch
            pickerOpen = false
            picker = null
            when (result) {
                is AttachmentResult.Selected -> {
                    attachment = result.attachment
                    reply = null
                    status = if (result.attachment.text.isBlank()) "사진에서 읽을 수 있는 글자를 찾지 못했어요." else null
                }
                is AttachmentResult.Failed -> status = result.message
                AttachmentResult.Cancelled -> Unit
            }
            openPanel()
        }
        if (pickerOpen) picker = selection else selection.close()
    }

    private fun submit() {
        if (active?.isActive == true || pickerOpen) return
        val file = attachment
        val raw = draft.trim()
        val request = parseRequest(if (raw.isEmpty() && file != null) "첨부한 글을 짧게 요약해 줘." else raw)
        pending = null
        menuExpanded = false
        reply = null
        if (request is ChatRequest.Invalid) {
            updateStatus(request.reason)
            return
        }
        if (file != null && request is ChatRequest.Search) {
            updateStatus("지도 검색을 실행하려면 첨부를 제거해 주세요.")
            return
        }
        if (file?.text?.isBlank() == true) {
            updateStatus("사진에서 읽을 수 있는 글자를 찾지 못했어요.")
            return
        }
        val snapshot = if (file == null) bridge.capture()?.takeIf(::supported) else null
        if (file == null && snapshot == null) {
            updateStatus("네이버지도 화면을 연 뒤 다시 보내 주세요.")
            return
        }
        if (request is ChatRequest.Guide) {
            if (!model.status().installed) {
                updateStatus("첨부 메뉴의 ‘로컬 모델’에서 .litertlm 파일을 가져와 주세요.")
                return
            }
            if (file == null && snapshot?.nodes.isNullOrEmpty()) {
                updateStatus("이 화면에서 읽을 수 있는 요소가 없어요.")
                return
            }
        }
        panel?.input?.text?.clear()
        hideKeyboard()
        when (request) {
            is ChatRequest.Search -> {
                val target = requireNotNull(snapshot)
                pending = PendingSearch(request.query, target.packageName, target.windowId)
                updateStatus("검색 실행을 누르면 현재 앱에 검색어를 입력합니다.")
            }
            is ChatRequest.Guide -> askModel(request.question, snapshot, file)
            is ChatRequest.Invalid -> Unit
        }
    }

    private fun askModel(question: String, snapshot: ScreenSnapshot?, file: DraftAttachment?) {
        active = scope.launch(start = CoroutineStart.LAZY) {
            updateStatus("기기에서 응답을 만드는 중…")
            try {
                val answer = if (file != null) {
                    model.respondToAttachment(attachmentPrompt(question, file.text, file.truncated))
                } else {
                    model.respond(guidePrompt(question, requireNotNull(snapshot)))
                }
                val current = snapshot?.let { bridge.capture() }
                if (snapshot != null && (current?.packageName != snapshot.packageName || current.windowId != snapshot.windowId)) {
                    updateStatus("화면이 바뀌었어요. 현재 화면에서 다시 질문해 주세요.")
                    return@launch
                }
                reply = answer.take(1500)
                updateStatus(if (snapshot != null && current?.nodes != snapshot.nodes) {
                    "온디바이스 응답 완료 · 질문 시점 화면 기준"
                } else "온디바이스 응답 완료")
            } catch (cancelled: CancellationException) {
                updateStatus("응답을 중지했어요.")
                throw cancelled
            } catch (_: Exception) {
                updateStatus("모델을 실행하지 못했어요. 모델 파일과 기기 메모리를 확인해 주세요.")
            } finally {
                active = null
                render()
            }
        }
        active?.start()
    }

    private fun executeSearch() {
        val plan = pending ?: return
        if (active?.isActive == true) return
        pending = null
        val current = bridge.capture()
        if (current?.packageName != plan.packageName || current.windowId != plan.windowId) {
            updateStatus("화면이 바뀌어 실행하지 않았어요. 다시 요청해 주세요.")
            return
        }
        hideKeyboard()
        active = scope.launch(start = CoroutineStart.LAZY) {
            showLauncher()
            try {
                val result = bridge.search(plan.query, current)
                reply = result.message
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
        val row = panel?.actions ?: return
        row.removeAllViews()
        if (active?.isActive == true) {
            row.addView(theme.button("중지") { active?.cancel() }.apply { id = R.id.hc_stop })
        } else if (pending != null) {
            row.addView(theme.button("검색 실행", primary = true) { executeSearch() }.apply { id = R.id.hc_execute })
        }
        row.visibility = if (active?.isActive == true || pending != null) View.VISIBLE else View.GONE
    }

    private fun supported(s: ScreenSnapshot?) =
        s?.packageName == "com.nhn.android.nmap" || s?.packageName == "dev.hanzicho.hyperchat.fixture"

    private fun render() {
        val views = panel ?: return
        views.reply.text = reply.orEmpty()
        views.replyScroll.visibility = if (reply == null) View.GONE else View.VISIBLE
        views.replyScroll.scrollTo(0, 0)
        views.status.text = status.orEmpty()
        views.status.visibility = if (status == null) View.GONE else View.VISIBLE
        views.attachmentName.text = attachment?.let {
            if (it.truncated) it.name + " · 일부 텍스트" else it.name
        }.orEmpty()
        views.attachmentRow.visibility = if (attachment == null) View.GONE else View.VISIBLE
        drawActions()
        renderMenu()
        updateButtons()
    }

    private fun renderMenu() {
        val views = panel ?: return
        views.menu.visibility = if (menuExpanded) View.VISIBLE else View.GONE
        views.menuButton.setImageResource(if (menuExpanded) R.drawable.hc_ic_close else R.drawable.hc_ic_add)
        views.menuButton.contentDescription = service.getString(
            if (menuExpanded) R.string.composer_close_menu else R.string.composer_attach,
        )
    }

    private fun updateButtons() {
        val views = panel ?: return
        val idle = active?.isActive != true
        val canSend = idle && (draft.isNotBlank() || !attachment?.text.isNullOrBlank())
        views.send.isEnabled = canSend
        views.send.alpha = if (canSend) 1f else .55f
        views.menuButton.isEnabled = idle
        views.removeAttachment.isEnabled = idle
    }

    private fun updateStatus(text: String) {
        status = text
        render()
    }

    private fun dismissPanel() {
        if (panel == null || pickerOpen) return
        if (menuExpanded) {
            menuExpanded = false
            renderMenu()
            return
        }
        if (active?.isActive == true) return
        pending = null
        reply = null
        status = null
        hideKeyboard()
        showLauncher()
    }

    private inner class ComposerWindow : LinearLayout(service) {
        @SuppressLint("ClickableViewAccessibility") // Outside touches dismiss the window through ACTION_DISMISS.
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_OUTSIDE) return super.onTouchEvent(event)
            // Keyboard taps can also be outside the overlay while the editor has focus.
            if (panel?.input?.hasFocus() != true) dismissPanel()
            return true
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) dismissPanel()
            return true
        }

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS)
        }

        override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
            if (action != AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id) {
                return super.performAccessibilityAction(action, arguments)
            }
            dismissPanel()
            return true
        }
    }

    private fun rowParams(top: Int, width: Int = -1) =
        LinearLayout.LayoutParams(width, -2).apply { topMargin = theme.dp(top) }

    private fun iconParams() = LinearLayout.LayoutParams(theme.dp(48), theme.dp(48))

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
        panel?.input?.let {
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
        hideDeleteMode()
        val view = overlay
        overlay = null
        panel = null
        // WindowManager owns the view as soon as addView returns, even before its first traversal.
        if (view != null) wm.removeViewImmediate(view)
    }

    fun close() {
        closed = true
        picker?.close()
        picker = null
        attachment = null
        scope.cancel()
        removeWindow()
    }
}
