package dev.hanzicho.hyperchat.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.hanzicho.hyperchat.HyperchatApp
import dev.hanzicho.hyperchat.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SettingsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val model get() = (application as HyperchatApp).model
    private lateinit var modelLabel: TextView
    private lateinit var importButton: View
    private lateinit var diagnosticButton: View
    private lateinit var gpuDiagnosticButton: View
    private lateinit var diagnosticLabel: TextView
    private var modelBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val theme = Theme(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val inset = theme.dp(24)
            setPadding(inset, inset * 2, inset, inset * 2)
            setBackgroundColor(theme.background)
        }
        fun add(view: View, top: Int = 12) = column.addView(view,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = theme.dp(top) })
        add(theme.label("HYPERCHAT", 12f, theme.accent, true), 0)
        add(theme.label("앱 위에서 바로 도움받기", 27f, bold = true))
        add(theme.label("네이버지도 화면을 보면서 사용법을 묻거나 장소를 검색할 수 있어요.", color = theme.muted))
        add(theme.label("1  오버레이 사용", 18f, bold = true), 32)
        add(theme.label("접근성 설정에서 Hyperchat을 켜고, 네이버지도를 열어 오른쪽 아래 버튼을 누르세요.", color = theme.muted))
        add(theme.button("접근성 설정 열기", primary = true) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        add(theme.label("2  온디바이스 모델", 18f, bold = true), 32)
        modelLabel = theme.label(model.status().message, color = theme.muted)
        add(modelLabel)
        importButton = theme.button(".litertlm 모델 가져오기") {
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(picker, PICK_MODEL)
        }.apply { id = R.id.hc_import }
        add(importButton)
        add(theme.label("파일을 가져오면 기기 안에서 채팅합니다. 검색 명령은 모델이 없어도 사용할 수 있어요.", color = theme.muted))
        diagnosticButton = theme.button("온디바이스 모델 진단 실행") { runDiagnostic(gpuFirst = false) }.apply {
            id = R.id.hc_diagnostic
            isEnabled = model.status().installed
        }
        add(diagnosticButton)
        gpuDiagnosticButton = theme.button("GPU 우선 진단 실행") { runDiagnostic(gpuFirst = true) }.apply {
            id = R.id.hc_gpu_diagnostic
            isEnabled = model.status().installed
        }
        add(gpuDiagnosticButton)
        diagnosticLabel = theme.label("Profiler 수집을 시작한 뒤 진단을 실행하세요.", color = theme.muted).apply {
            id = R.id.hc_diagnostic_status
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        add(diagnosticLabel)
        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        if (!modelBusy) {
            val status = model.status()
            modelLabel.text = status.message
            diagnosticButton.isEnabled = status.installed
            gpuDiagnosticButton.isEnabled = status.installed
        }
    }

    @Deprecated("Handles the system document picker result.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_MODEL || resultCode != RESULT_OK || modelBusy) return
        val uri = data?.data ?: return
        scope.launch {
            setModelBusy(true)
            modelLabel.text = "모델 파일을 기기에 복사하는 중…"
            try {
                model.importModel(uri)
                modelLabel.text = model.status().message
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                modelLabel.text = "모델을 가져오지 못했어요. .litertlm 파일과 저장 공간을 확인해 주세요."
            } finally {
                setModelBusy(false)
            }
        }
    }

    private fun runDiagnostic(gpuFirst: Boolean) {
        if (modelBusy || !model.status().installed) return
        scope.launch {
            setModelBusy(true)
            diagnosticLabel.text = if (gpuFirst) "GPU 우선으로 진단하는 중…" else "CPU에서 진단하는 중…"
            try {
                val answer = model.runDiagnostic(gpuFirst)
                diagnosticLabel.text = "온디바이스 응답 생성 완료 · ${answer.take(120)}"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                diagnosticLabel.text = "온디바이스 진단에 실패했어요. Profiler에서 실행 기록을 확인해 주세요."
            } finally {
                setModelBusy(false)
            }
        }
    }

    private fun setModelBusy(busy: Boolean) {
        modelBusy = busy
        importButton.isEnabled = !busy
        val canRun = !busy && model.status().installed
        diagnosticButton.isEnabled = canRun
        gpuDiagnosticButton.isEnabled = canRun
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object { const val PICK_MODEL = 104 }
}
