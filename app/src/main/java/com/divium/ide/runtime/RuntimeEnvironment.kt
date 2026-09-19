package com.divium.ide.runtime

import android.content.Context
import java.io.File

/**
 * Owns Divium's private execution layout. Toolchain packs are installed below [prefix]
 * and never need broad device-storage access.
 */
class RuntimeEnvironment(context: Context) {
    val root = File(context.filesDir, "runtime")
    val prefix = File(root, "usr")
    val bin = File(prefix, "bin")
    val lib = File(prefix, "lib")
    val home = File(root, "home")
    val tmp = File(root, "tmp")
    val workspaces = File(context.filesDir, "workspaces")

    init {
        listOf(root, prefix, bin, lib, home, tmp, workspaces).forEach { it.mkdirs() }
    }

    val shellPath: String
        get() = File(bin, "bash").takeIf { it.isFile }?.absolutePath ?: "/system/bin/sh"

    fun environment(extra: Map<String, String> = emptyMap()): Array<String> {
        val values = linkedMapOf(
            "HOME" to home.absolutePath,
            "TMPDIR" to tmp.absolutePath,
            "PREFIX" to prefix.absolutePath,
            "PATH" to "${bin.absolutePath}:/system/bin:/system/xbin",
            "LANG" to "en_US.UTF-8",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
        )
        values.putAll(extra)
        return values.map { (key, value) -> "$key=$value" }.toTypedArray()
    }
}
