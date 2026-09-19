package com.divium.ide.language

import com.divium.ide.runtime.RuntimeEnvironment
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class JsonRpcProcessClient(
    private val runtime: RuntimeEnvironment,
    private val command: String,
    private val cwd: File,
) {
    private var process: Process? = null
    private val ids = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, (JSONObject) -> Unit>()
    @Volatile private var running = false

    fun start(onNotification: (JSONObject) -> Unit = {}) {
        if (running) return
        process = ProcessBuilder(runtime.shellPath, "-c", command)
            .directory(cwd)
            .apply {
                environment()["HOME"] = runtime.home.absolutePath
                environment()["PATH"] = "${runtime.bin.absolutePath}:/system/bin:/system/xbin"
                environment()["TERM"] = "xterm-256color"
            }
            .start()
        running = true
        Thread {
            val input = BufferedInputStream(process!!.inputStream)
            try {
                while (running) {
                    var contentLength = -1
                    while (true) {
                        val line = readAsciiLine(input) ?: return@Thread
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", true)) {
                            contentLength = line.substringAfter(":").trim().toInt()
                        }
                    }
                    if (contentLength <= 0) continue
                    val body = readExactly(input, contentLength).toString(Charsets.UTF_8)
                    val json = JSONObject(body)
                    if (json.has("id")) pending.remove(json.getLong("id"))?.invoke(json) else onNotification(json)
                }
            } finally {
                running = false
            }
        }.apply {
            name = "divium-jsonrpc"
            isDaemon = true
        }.start()
    }

    @Synchronized
    fun notify(method: String, params: Any? = null) {
        write(JSONObject().put("jsonrpc", "2.0").put("method", method).apply {
            if (params != null) put("params", params)
        })
    }

    @Synchronized
    fun request(method: String, params: Any? = null, callback: (JSONObject) -> Unit): Long {
        val id = ids.getAndIncrement()
        pending[id] = callback
        write(JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).apply {
            if (params != null) put("params", params)
        })
        return id
    }

    fun stop() {
        running = false
        process?.destroy()
        process = null
        pending.clear()
    }

    private fun write(json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val header = "Content-Length: ${bytes.size}\r\n\r\n".toByteArray(Charsets.US_ASCII)
        val output = process?.outputStream ?: error("JSON-RPC process not started")
        output.write(header)
        output.write(bytes)
        output.flush()
    }

    private fun readExactly(input: BufferedInputStream, size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(bytes, offset, size - offset)
            if (read < 0) error("Unexpected end of JSON-RPC stream")
            offset += read
        }
        return bytes
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val out = StringBuilder()
        while (true) {
            val next = input.read()
            if (next == -1) return if (out.isEmpty()) null else out.toString()
            if (next == '\n'.code) return out.toString().removeSuffix("\r")
            out.append(next.toChar())
        }
    }
}
