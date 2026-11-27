package dev.hanzicho.hyperchat

import android.app.Application
import android.content.Context
import dev.hanzicho.hyperchat.model.ModelManager

class HyperchatApp : Application() {
    val model by lazy { ModelManager(this) }
    private val screenAccess by lazy { getSharedPreferences("screen_access", Context.MODE_PRIVATE) }
    internal var screenAccessAccepted: Boolean
        get() = screenAccess.getBoolean("disclosure_v1", false)
        set(accepted) {
            screenAccess.edit().putBoolean("disclosure_v1", accepted).apply()
            if (!accepted) disconnectScreenAccess?.invoke()
        }
    internal var disconnectScreenAccess: (() -> Unit)? = null
    internal var systemShortcut: (() -> Unit)? = null
    internal var restoreBubble: (() -> Unit)? = null
}
