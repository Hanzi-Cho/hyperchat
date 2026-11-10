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
    private var importing = false

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
        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        if (!importing) modelLabel.text = model.status().message
    }

    @Deprecated("Handles the system document picker result.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_MODEL || resultCode != RESULT_OK || importing) return
        val uri = data?.data ?: return
        scope.launch {
            importing = true
            importButton.isEnabled = false
            modelLabel.text = "모델 파일을 기기에 복사하는 중…"
            try {
                model.importModel(uri)
                modelLabel.text = model.status().message
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                modelLabel.text = "모델을 가져오지 못했어요. .litertlm 파일과 저장 공간을 확인해 주세요."
            } finally {
                importing = false
                importButton.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object { const val PICK_MODEL = 104 }
}
