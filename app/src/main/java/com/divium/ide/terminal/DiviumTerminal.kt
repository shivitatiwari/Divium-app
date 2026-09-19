package com.divium.ide.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.divium.ide.runtime.RuntimeEnvironment
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

private class SessionClient(private val context: Context) : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) = Unit
    override fun onTitleChanged(changedSession: TerminalSession) = Unit
    override fun onSessionFinished(finishedSession: TerminalSession) = Unit
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { text ->
            session?.write(text)
        }
    }

    override fun onBell(session: TerminalSession) = Unit
    override fun onColorsChanged(session: TerminalSession) = Unit
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, e.message, e) }
}

private class ViewClient(private val view: TerminalView) : TerminalViewClient {
    var control = false
    var alt = false
    var shift = false
    private var fontSize = 22f

    override fun onScale(scale: Float): Float {
        fontSize = (fontSize * scale).coerceIn(12f, 42f)
        view.setTextSize(fontSize.toInt())
        return fontSize
    }

    override fun onSingleTapUp(e: MotionEvent) {
        view.requestFocus()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = view.hasFocus()
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession) = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent) = false
    override fun onLongPress(event: MotionEvent) = false
    override fun readControlKey() = control.also { control = false }
    override fun readAltKey() = alt.also { alt = false }
    override fun readShiftKey() = shift.also { shift = false }
    override fun readFnKey() = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession) = false
    override fun onEmulatorSet() = Unit

    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, e.message, e) }
}

private class TerminalHolder(context: Context, cwd: File) {
    private val runtime = RuntimeEnvironment(context)
    val session = TerminalSession(
        runtime.shellPath,
        cwd.absolutePath,
        emptyArray(),
        runtime.environment(),
        4000,
        SessionClient(context),
    )

    lateinit var client: ViewClient

    fun finish() = session.finishIfRunning()
}

@Composable
fun DiviumTerminal(
    workingDirectory: File,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var generation by remember { mutableStateOf(0) }
    val holder = remember(workingDirectory.absolutePath, generation) { TerminalHolder(context, workingDirectory) }

    DisposableEffect(holder) {
        onDispose { holder.finish() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextButton(onClick = { holder.client.control = true }) { Text("CTRL") }
            TextButton(onClick = { holder.session.write("\u001B") }) { Text("ESC") }
            TextButton(onClick = { holder.session.write("\t") }) { Text("TAB") }
            TextButton(onClick = { holder.session.write("\u0003") }) { Text("C-C") }
            TextButton(onClick = { holder.session.write("\u001B[A") }) { Text("↑") }
            TextButton(onClick = { holder.session.write("\u001B[B") }) { Text("↓") }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    setTextSize(22)
                    setBackgroundColor(android.graphics.Color.rgb(20, 20, 19))
                    holder.client = ViewClient(this)
                    setTerminalViewClient(holder.client)
                    attachSession(holder.session)
                    isFocusableInTouchMode = true
                    requestFocus()
                }
            },
        )

        Button(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
            onClick = {
                holder.finish()
                generation += 1
            },
        ) {
            Text("New terminal", style = MaterialTheme.typography.labelLarge)
        }
    }
}
