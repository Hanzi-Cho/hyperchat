package dev.hanzicho.hyperchat.model

import android.os.SystemClock
import android.util.Log
import dev.hanzicho.hyperchat.BuildConfig
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

internal data class ModelObservation(val bytes: Long, val pinnedQwen: Boolean = false) {
    companion object {
        const val PINNED_BYTES = 343_490_560L
        private val pinnedSha256 = "bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        fun fromDigest(bytes: Long, digest: ByteArray): ModelObservation =
            ModelObservation(bytes, bytes == PINNED_BYTES && MessageDigest.isEqual(digest, pinnedSha256))
    }
}

internal fun traceId(): String = UUID.randomUUID().toString()

/** Versioned, content-free events for an ADB collector. Identifiers live for one app process. */
internal class RuntimeTrace(
    private val event: String,
    var model: ModelObservation? = null,
    private val backendRequested: String = "cpu",
    private val requestId: String? = null,
    private val operationId: String = traceId(),
    private val engineId: String? = null,
    private val fallback: String = "unobserved",
    private val scenario: String? = null,
) {
    private val started = SystemClock.elapsedRealtime()
    var backendEffective: String? = null
    var backendEvidence: String? = null
    @Volatile var firstOutputMs: Long? = null

    fun ageMs(): Long = SystemClock.elapsedRealtime() - started

    inline fun <T> measure(emitStart: Boolean = true, block: () -> T): T {
        if (emitStart) emit("start")
        var status = "ok"
        try {
            return block()
        } catch (failure: Throwable) {
            status = if (failure is CancellationException) "cancelled" else "error"
            throw failure
        } finally {
            emit("end", status)
        }
    }

    private fun emit(phase: String, status: String? = null) {
        val now = SystemClock.elapsedRealtime()
        val pinned = model?.pinnedQwen == true
        val payload = JSONObject().apply {
            put("v", 1)
            put("runtime", "litert-lm")
            put("runtime_version", BuildConfig.LITERT_LM_VERSION)
            put("event", event)
            put("phase", phase)
            put("scenario", scenario)
            put("operation_id", operationId)
            put("request_id", requestId)
            put("engine_id", engineId)
            put("t_ms", now)
            put("model_format", "litertlm")
            put("model_bytes", model?.bytes)
            put("model_id", if (pinned) "qwen3_0_6b_pinned" else null)
            put("quantization", if (pinned) "dynamic_int4_block32_fp32_activations" else "unknown")
            put("quantization_evidence", if (pinned) "pinned_sha256" else null)
            put("optimization", if (pinned) "offline_gpu_composites_external_weights" else "unknown")
            put("optimization_evidence", if (pinned) "pinned_sha256" else null)
            put("backend_requested", backendRequested)
            put("backend_effective", backendEffective)
            put("backend_evidence", backendEvidence)
            put("fallback", fallback)
            if (status != null) {
                put("status", status)
                put("elapsed_ms", now - started)
                put("first_output_ms", firstOutputMs)
            }
        }
        Log.i("HyperchatOnDeviceAI", payload.toString())
    }
}
