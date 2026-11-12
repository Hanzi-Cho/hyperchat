package dev.hanzicho.hyperchat.ui

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import android.widget.Toast
import dev.hanzicho.hyperchat.HyperchatApp
import dev.hanzicho.hyperchat.R

/** Handles the launcher and managed-device shortcut without staying in the foreground. */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
        if (savedInstanceState != null || intent.action != OPEN_SHORTCUT) return
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        val launch = (application as HyperchatApp).systemShortcut
        if (launch != null) {
            launch()
        } else {
            Toast.makeText(this, R.string.shortcut_requires_service, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val OPEN_SHORTCUT = "dev.hanzicho.hyperchat.action.OPEN_SHORTCUT"
    }
}
