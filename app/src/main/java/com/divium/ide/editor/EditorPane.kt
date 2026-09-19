package com.divium.ide.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

@Composable
fun EditorPane(
    file: File,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dirty by remember(file.absolutePath) { mutableStateOf(false) }
    val initial = remember(file.absolutePath, file.lastModified()) { runCatching { file.readText() }.getOrDefault("") }
    val editor = remember(file.absolutePath) { mutableStateOf<CodeEditor?>(null) }

    DisposableEffect(file.absolutePath) {
        onDispose {
            editor.value?.release()
            editor.value = null
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(file.name, modifier = Modifier.weight(1f))
            TextButton(onClick = { editor.value?.undo() }) { Text("Undo") }
            TextButton(onClick = { editor.value?.redo() }) { Text("Redo") }
            Button(
                enabled = dirty,
                onClick = {
                    val value = editor.value?.text?.toString() ?: return@Button
                    onSave(value)
                    dirty = false
                },
            ) { Text(if (dirty) "Save *" else "Saved") }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                CodeEditor(context).apply {
                    setText(initial)
                    setTextSize(14f)
                    isWordwrap = false
                    props.stickyScroll = true
                    subscribeAlways(ContentChangeEvent::class.java) { dirty = true }
                    editor.value = this
                }
            },
            update = { view ->
                if (editor.value !== view) editor.value = view
            },
        )
    }
}
