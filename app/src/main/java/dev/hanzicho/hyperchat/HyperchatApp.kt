package dev.hanzicho.hyperchat

import android.app.Application
import dev.hanzicho.hyperchat.model.ModelManager

class HyperchatApp : Application() {
    val model by lazy { ModelManager(this) }
}
