package dev.hanzicho.hyperchat.ui

import android.content.Context

internal enum class ShortcutTrigger { TAP, SWIPE_UP, SYSTEM }

internal enum class ShortcutAction { OPEN_CHAT, OPEN_SETTINGS, NONE }

internal class ShortcutPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("shortcut_actions", Context.MODE_PRIVATE)

    var isBubbleHidden: Boolean
        get() = preferences.getBoolean("bubble_hidden", false)
        set(hidden) { preferences.edit().putBoolean("bubble_hidden", hidden).apply() }

    fun action(trigger: ShortcutTrigger): ShortcutAction {
        val saved = preferences.getString(trigger.name, null)
        return ShortcutAction.entries.firstOrNull { it.name == saved } ?: ShortcutAction.OPEN_CHAT
    }

    fun setAction(trigger: ShortcutTrigger, action: ShortcutAction) {
        preferences.edit().putString(trigger.name, action.name).apply()
    }
}
