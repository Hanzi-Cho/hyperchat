package dev.hanzicho.hyperchat.attachment

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal enum class AttachmentKind { IMAGE, TEXT }

internal data class DraftAttachment(
    val kind: AttachmentKind,
    val name: String,
    val text: String,
    val truncated: Boolean,
)

internal sealed interface AttachmentResult {
    data class Selected(val attachment: DraftAttachment) : AttachmentResult
    data class Failed(val message: String) : AttachmentResult
    data object Cancelled : AttachmentResult
}

/** The picker Activity and overlay share only a single-use result channel in this process. */
internal object AttachmentPicker {
    const val REQUEST_TOKEN = "dev.hanzicho.hyperchat.attachment.REQUEST_TOKEN"

    private data class Pending(val kind: AttachmentKind, val onResult: (AttachmentResult) -> Unit)
    private val pending = ConcurrentHashMap<String, Pending>()

    fun launch(context: Context, kind: AttachmentKind, onResult: (AttachmentResult) -> Unit): AutoCloseable {
        val token = UUID.randomUUID().toString()
        pending[token] = Pending(kind, onResult)
        val intent = Intent(context, AttachmentPickerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(REQUEST_TOKEN, token)
        try {
            context.startActivity(intent)
        } catch (_: RuntimeException) {
            Handler(Looper.getMainLooper()).post {
                complete(token, AttachmentResult.Failed("Android 파일 선택기를 열지 못했어요."))
            }
        }
        return AutoCloseable { pending.remove(token) }
    }

    fun kind(token: String): AttachmentKind? = pending[token]?.kind

    fun complete(token: String, result: AttachmentResult) {
        pending.remove(token)?.onResult?.invoke(result)
    }
}
