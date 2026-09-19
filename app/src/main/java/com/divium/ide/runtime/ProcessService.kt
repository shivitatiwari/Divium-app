package com.divium.ide.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DiviumProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

class ProcessService(private val runtime: RuntimeEnvironment) {
    suspend fun run(
        command: String,
        workingDirectory: File = runtime.home,
        extraEnvironment: Map<String, String> = emptyMap(),
    ): DiviumProcessResult = withContext(Dispatchers.IO) {
        workingDirectory.mkdirs()
        val process = ProcessBuilder(runtime.shellPath, "-c", command)
            .directory(workingDirectory)
            .apply {
                environment().apply {
                    put("HOME", runtime.home.absolutePath)
                    put("TMPDIR", runtime.tmp.absolutePath)
                    put("PREFIX", runtime.prefix.absolutePath)
                    put("PATH", "${runtime.bin.absolutePath}:/system/bin:/system/xbin")
                    put("TERM", "xterm-256color")
                    putAll(extraEnvironment)
                }
            }
            .start()

        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        DiviumProcessResult(process.waitFor(), stdout, stderr)
    }
}
