package dev.hanzicho.hyperchat.attachment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ProgressBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** A separate, short-lived task returns the user to the app beneath the overlay. */
class AttachmentPickerActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var token: String? = null
    private var reading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = intent.getStringExtra(AttachmentPicker.REQUEST_TOKEN)
        val kind = token?.let(AttachmentPicker::kind)
        if (kind == null) {
            finishAndRemoveTask()
        } else if (savedInstanceState == null) {
            showPicker(kind)
        }
    }

    @Suppress("DEPRECATION")
    private fun showPicker(kind: AttachmentKind) {
        try {
            if (kind == AttachmentKind.IMAGE && hasPhotoPicker() && launchPhotoPicker()) return
            val mimeType = if (kind == AttachmentKind.IMAGE) "image/*" else "text/plain"
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mimeType).putExtra(Intent.EXTRA_LOCAL_ONLY, true), PICK)
        } catch (_: ActivityNotFoundException) {
            finishWith(AttachmentResult.Failed("Android 파일 선택기를 찾지 못했어요."))
        } catch (_: SecurityException) {
            finishWith(AttachmentResult.Failed("이 기기에서 파일 선택기를 열 수 없어요."))
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("InlinedApi") // The caller checks the platform and R extension versions.
    private fun launchPhotoPicker(): Boolean = try {
        startActivityForResult(Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*"), PICK)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun hasPhotoPicker(): Boolean = Build.VERSION.SDK_INT >= 33 ||
        (Build.VERSION.SDK_INT >= 30 && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2)

    @Deprecated("Handles Android picker results for a single transient URI.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK) return
        val kind = token?.let(AttachmentPicker::kind)
        val uri = data?.data
        if (kind == null || resultCode != RESULT_OK || uri == null) {
            finishWith(AttachmentResult.Cancelled)
            return
        }
        readSelection(kind, uri)
    }

    private fun readSelection(kind: AttachmentKind, uri: Uri) {
        reading = true
        setContentView(FrameLayout(this).apply {
            addView(ProgressBar(context).apply { contentDescription = "선택한 첨부 파일을 읽는 중" },
                FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        })
        scope.launch {
            val result = try {
                AttachmentResult.Selected(readAttachment(contentResolver, uri, kind))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: AttachmentReadException) {
                AttachmentResult.Failed(failure.message.orEmpty())
            } catch (_: Exception) {
                AttachmentResult.Failed("선택한 파일을 읽지 못했어요.")
            }
            finishWith(result)
        }
    }

    private fun finishWith(result: AttachmentResult) {
        finishAndRemoveTask()
        token?.let { AttachmentPicker.complete(it, result) }
    }

    override fun onDestroy() {
        scope.cancel()
        if (isFinishing || reading) token?.let { AttachmentPicker.complete(it, AttachmentResult.Cancelled) }
        super.onDestroy()
    }

    private companion object { const val PICK = 201 }
}
