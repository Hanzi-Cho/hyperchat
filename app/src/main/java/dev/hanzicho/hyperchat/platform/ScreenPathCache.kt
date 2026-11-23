package dev.hanzicho.hyperchat.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal enum class PathIntent { SEARCH }

internal enum class CachedScreenAction { CLICK, SET_TEXT, SUBMIT }

internal data class CachedScreenStep(
    val action: CachedScreenAction,
    val resourceId: String,
    val screenFingerprint: String,
)

internal data class PathKey(
    val packageName: String,
    val appVersion: Long,
    val lastUpdateTime: Long,
    val screenFingerprint: String,
    val intent: PathIntent,
) {
    internal val storageKey get() = "$packageName|$appVersion|$lastUpdateTime|$screenFingerprint|$intent"
}

/** Stores successful, reusable UI steps. The executor must validate each step against the live app. */
internal class ScreenPathCache(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("screen_paths_v1", Context.MODE_PRIVATE)

    fun key(packageName: String, screenFingerprint: String?, intent: PathIntent): PathKey? {
        if (screenFingerprint == null || !fingerprintFormat.matches(screenFingerprint)) return null
        val info = try {
            if (Build.VERSION.SDK_INT >= 33) {
                app.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                app.packageManager.getPackageInfo(packageName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        @Suppress("DEPRECATION")
        val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return PathKey(packageName, version, info.lastUpdateTime, screenFingerprint, intent)
    }

    fun read(key: PathKey): List<CachedScreenStep>? {
        val encoded = preferences.getString(key.storageKey, null) ?: return null
        val payload = decode(encoded) ?: return null
        val steps = payload.optJSONArray("steps") ?: return null
        if (steps.length() !in 1..MAX_STEPS) return null
        val decoded = buildList {
            for (index in 0 until steps.length()) {
                val step = steps.optJSONObject(index) ?: return null
                val action = CachedScreenAction.entries.firstOrNull { it.name == step.optString("action") }
                    ?: return null
                add(CachedScreenStep(action, step.optString("resource"), step.optString("screen")))
            }
        }
        return decoded.takeIf { valid(key, it) }
    }

    @Synchronized
    fun remember(key: PathKey, steps: List<CachedScreenStep>): Boolean {
        if (!valid(key, steps)) return false
        val storedAt = System.currentTimeMillis()
        val encodedSteps = JSONArray()
        for (step in steps) {
            encodedSteps.put(JSONObject().put("action", step.action.name)
                .put("resource", step.resourceId).put("screen", step.screenFingerprint))
        }
        val payload = JSONObject().put("stored_at", storedAt).put("steps", encodedSteps)
        val editor = preferences.edit().putString(key.storageKey, payload.toString())
        val otherEntries = preferences.all.filterKeys { it != key.storageKey }
        val excess = (otherEntries.size + 1 - MAX_ENTRIES).coerceAtLeast(0)
        if (excess > 0) {
            otherEntries.entries.sortedBy { entry ->
                (entry.value as? String)?.let(::decode)?.optLong("stored_at") ?: 0L
            }.take(excess).forEach { editor.remove(it.key) }
        }
        editor.apply()
        return true
    }

    @Synchronized
    fun forget(key: PathKey) {
        preferences.edit().remove(key.storageKey).apply()
    }

    private fun valid(key: PathKey, steps: List<CachedScreenStep>): Boolean =
        steps.size in 1..MAX_STEPS && steps.first().screenFingerprint == key.screenFingerprint &&
            steps.all { isStableResource(key.packageName, it.resourceId) && fingerprintFormat.matches(it.screenFingerprint) }

    private fun decode(encoded: String): JSONObject? = try {
        JSONObject(encoded)
    } catch (_: JSONException) {
        null
    }

    companion object {
        private const val MAX_ENTRIES = 128
        private const val MAX_STEPS = 8
        private val fingerprintFormat = Regex("[a-f0-9]{64}")
        private val resourceEntryFormat = Regex("[A-Za-z_][A-Za-z0-9_.]{0,127}")

        fun isStableResource(packageName: String, resourceId: String): Boolean {
            val owner = resourceId.substringBefore(":id/", missingDelimiterValue = "")
            if (owner.isEmpty() || (owner != packageName && owner != "android")) return false
            return resourceEntryFormat.matches(resourceId.substringAfter(":id/"))
        }

        /** Labels and field values may be private or change with every request. Hash only the UI structure. */
        fun fingerprint(packageName: String, nodes: List<ScreenNode>): String? {
            val structure = nodes.asSequence()
                .filter { isStableResource(packageName, it.viewId) }
                .map { "${it.viewId}:${it.clickable}:${it.editable}" }
                .distinct().take(96).joinToString("\n")
            if (structure.isEmpty()) return null
            return MessageDigest.getInstance("SHA-256").digest(structure.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    }
}
