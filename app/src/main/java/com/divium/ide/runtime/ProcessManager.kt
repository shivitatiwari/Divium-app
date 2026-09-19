package com.divium.ide.runtime

import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

enum class ProcessState { STARTING, RUNNING, EXITED, FAILED, STOPPED }

data class ProcessSnapshot(
    val id: String,
    val command: String,
    val workingDirectory: String,
    val state: ProcessState,
    val exitCode: Int?,
    val pid: Long?,
    val ports: Set<Int>,
    val output: List<String>,
)

class ManagedProcess internal constructor(
    val id: String,
    val command: String,
    val workingDirectory: File,
) {
    @Volatile internal var process: Process? = null
    @Volatile internal var state: ProcessState = ProcessState.STARTING
    @Volatile internal var exitCode: Int? = null
    internal val outputLines = CopyOnWriteArrayList<String>()
    internal val detectedPorts = ConcurrentHashMap.newKeySet<Int>()

    fun snapshot() = ProcessSnapshot(
        id = id,
        command = command,
        workingDirectory = workingDirectory.absolutePath,
        state = state,
        exitCode = exitCode,
        pid = runCatching { process?.pid() }.getOrNull(),
        ports = detectedPorts.toSet(),
        output = outputLines.takeLast(2_000),
    )

    fun stop() {
        state = ProcessState.STOPPED
        process?.destroy()
        runCatching {
            if (process?.isAlive == true) process?.destroyForcibly()
        }
    }
}

class ProcessManager(private val runtime: RuntimeEnvironment) {
    private val records = ConcurrentHashMap<String, ManagedProcess>()
    private val pool = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "divium-process").apply { isDaemon = true }
    }

    fun start(
        command: String,
        workingDirectory: File,
        extraEnvironment: Map<String, String> = emptyMap(),
        onUpdate: (ProcessSnapshot) -> Unit = {},
    ): ManagedProcess {
        val record = ManagedProcess(UUID.randomUUID().toString(), command, workingDirectory)
        records[record.id] = record
        pool.execute {
            try {
                workingDirectory.mkdirs()
                val process = ProcessBuilder(runtime.shellPath, "-c", command)
                    .directory(workingDirectory)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["HOME"] = runtime.home.absolutePath
                        environment()["TMPDIR"] = runtime.tmp.absolutePath
                        environment()["PREFIX"] = runtime.prefix.absolutePath
                        environment()["PATH"] = "${runtime.bin.absolutePath}:/system/bin:/system/xbin"
                        environment()["TERM"] = "xterm-256color"
                        environment()["COLORTERM"] = "truecolor"
                        environment().putAll(extraEnvironment)
                    }
                    .start()
                record.process = process
                record.state = ProcessState.RUNNING
                onUpdate(record.snapshot())

                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        record.outputLines += line
                        findPorts(line).forEach(record.detectedPorts::add)
                        onUpdate(record.snapshot())
                    }
                }
                val code = process.waitFor()
                record.exitCode = code
                if (record.state != ProcessState.STOPPED) {
                    record.state = if (code == 0) ProcessState.EXITED else ProcessState.FAILED
                }
                onUpdate(record.snapshot())
            } catch (error: Throwable) {
                record.outputLines += "${error::class.java.simpleName}: ${error.message ?: "Process failed"}"
                record.state = ProcessState.FAILED
                onUpdate(record.snapshot())
            }
        }
        return record
    }

    fun stop(id: String) {
        records[id]?.stop()
    }

    fun restart(id: String, onUpdate: (ProcessSnapshot) -> Unit = {}): ManagedProcess? {
        val old = records[id] ?: return null
        old.stop()
        return start(old.command, old.workingDirectory, onUpdate = onUpdate)
    }

    fun snapshots(): List<ProcessSnapshot> =
        records.values.map(ManagedProcess::snapshot).sortedByDescending { it.state == ProcessState.RUNNING }

    fun clearFinished() {
        records.entries.removeIf { (_, process) ->
            process.state == ProcessState.EXITED || process.state == ProcessState.FAILED || process.state == ProcessState.STOPPED
        }
    }

    private fun findPorts(line: String): Set<Int> {
        val found = mutableSetOf<Int>()
        Regex("""(?:localhost|127\.0\.0\.1|0\.0\.0\.0|\[::\])[: ](\d{2,5})""", RegexOption.IGNORE_CASE)
            .findAll(line)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .filter { it in 1..65535 }
            .forEach(found::add)
        Regex("""\bport\s*[:=]?\s*(\d{2,5})\b""", RegexOption.IGNORE_CASE)
            .findAll(line)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .filter { it in 1..65535 }
            .forEach(found::add)
        return found
    }
}
