package com.divium.ide.codex

import com.divium.ide.language.JsonRpcProcessClient
import com.divium.ide.runtime.RuntimeEnvironment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CodexService(runtime: RuntimeEnvironment, workspace: File) {
    private val rpc = JsonRpcProcessClient(runtime, "codex app-server", workspace)

    fun start(onEvent: (JSONObject) -> Unit) {
        rpc.start(onEvent)
    }

    fun initialize(callback: (JSONObject) -> Unit) {
        rpc.request(
            "initialize",
            JSONObject()
                .put("clientInfo", JSONObject().put("name", "Divium").put("version", "0.2.0"))
                .put("capabilities", JSONObject()),
            callback,
        )
    }

    fun send(method: String, params: JSONObject, callback: (JSONObject) -> Unit = {}) =
        rpc.request(method, params, callback)

    fun notify(method: String, params: JSONObject = JSONObject()) = rpc.notify(method, params)

    fun stop() = rpc.stop()

    companion object {
        fun contextPayload(openFile: File?, selectedText: String?, extraFiles: List<File>): JSONObject =
            JSONObject()
                .put("openFile", openFile?.absolutePath)
                .put("selection", selectedText)
                .put("files", JSONArray(extraFiles.map(File::getAbsolutePath)))
    }
}
