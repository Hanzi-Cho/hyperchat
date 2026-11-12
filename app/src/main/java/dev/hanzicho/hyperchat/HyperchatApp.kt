package dev.hanzicho.hyperchat

import android.app.Application
import dev.hanzicho.hyperchat.model.ModelManager

class HyperchatApp : Application() {
    val model by lazy { ModelManager(this) }
    internal var systemShortcut: (() -> Unit)? = null
    internal var restoreBubble: (() -> Unit)? = null
}
