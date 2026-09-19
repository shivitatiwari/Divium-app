package com.divium.ide.theme

import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.io.File

data class DiviumThemeTokens(
    val id: String,
    val name: String,
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val foreground: Color,
    val accent: Color,
    val muted: Color,
    val border: Color,
    val editorBackground: Color,
    val editorForeground: Color,
    val terminalBackground: Color,
    val terminalForeground: Color,
)

object DiviumThemes {
    val Paper = DiviumThemeTokens(
        "paper", "Divium Paper", false,
        Color(0xFFF7F4EC), Color(0xFFFFFCF5), Color(0xFF181715), Color(0xFFD96547),
        Color(0xFF726D64), Color(0xFFD9D2C4), Color(0xFFFFFCF5), Color(0xFF181715),
        Color(0xFF141413), Color(0xFFF4F0E8),
    )

    val Midnight = DiviumThemeTokens(
        "midnight", "Divium Midnight", true,
        Color(0xFF111214), Color(0xFF191A1D), Color(0xFFE9E7E2), Color(0xFFE07A5F),
        Color(0xFF9A9994), Color(0xFF313238), Color(0xFF111214), Color(0xFFE9E7E2),
        Color(0xFF0C0D0E), Color(0xFFF0EFEA),
    )

    val builtIns = listOf(Paper, Midnight)

    fun parseVsCodeTheme(file: File): DiviumThemeTokens {
        val json = JSONObject(file.readText())
        val colors = json.optJSONObject("colors") ?: JSONObject()
        val type = json.optString("type", "dark")
        val fallback = if (type == "light") Paper else Midnight
        fun color(key: String, default: Color): Color =
            parseColor(colors.optString(key, "")).getOrDefault(default)
        return fallback.copy(
            id = "imported:${file.absolutePath.hashCode()}",
            name = json.optString("name", file.nameWithoutExtension),
            dark = type != "light",
            background = color("sideBar.background", fallback.background),
            surface = color("editorGroupHeader.tabsBackground", fallback.surface),
            foreground = color("foreground", fallback.foreground),
            accent = color("focusBorder", fallback.accent),
            border = color("panel.border", fallback.border),
            editorBackground = color("editor.background", fallback.editorBackground),
            editorForeground = color("editor.foreground", fallback.editorForeground),
            terminalBackground = color("terminal.background", fallback.terminalBackground),
            terminalForeground = color("terminal.foreground", fallback.terminalForeground),
        )
    }

    private fun parseColor(value: String): Result<Color> = runCatching {
        val raw = value.removePrefix("#")
        val parsed = raw.toLong(16)
        when (raw.length) {
            6 -> Color(0xFF000000 or parsed)
            8 -> Color(parsed)
            else -> error("Invalid color")
        }
    }
}
