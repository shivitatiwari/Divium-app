package com.divium.ide.settings

import android.content.Context

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("divium_preferences", Context.MODE_PRIVATE)

    var setupComplete: Boolean
        get() = prefs.getBoolean("setup_complete", false)
        set(value) = prefs.edit().putBoolean("setup_complete", value).apply()

    var selectedLanguages: Set<String>
        get() = prefs.getStringSet("languages", setOf("javascript", "python"))?.toSet().orEmpty()
        set(value) = prefs.edit().putStringSet("languages", value).apply()

    var workspaceMode: String
        get() = prefs.getString("workspace_mode", "managed") ?: "managed"
        set(value) = prefs.edit().putString("workspace_mode", value).apply()

    var themeId: String
        get() = prefs.getString("theme_id", "paper") ?: "paper"
        set(value) = prefs.edit().putString("theme_id", value).apply()

    var gitAuthorName: String
        get() = prefs.getString("git_author_name", "Divium User") ?: "Divium User"
        set(value) = prefs.edit().putString("git_author_name", value).apply()

    var gitAuthorEmail: String
        get() = prefs.getString("git_author_email", "user@divium.local") ?: "user@divium.local"
        set(value) = prefs.edit().putString("git_author_email", value).apply()
}
