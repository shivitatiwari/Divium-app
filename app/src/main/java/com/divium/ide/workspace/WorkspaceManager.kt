package com.divium.ide.workspace

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.divium.ide.runtime.RuntimeEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class WorkspaceManager(private val context: Context) {
    private val runtime = RuntimeEnvironment(context)
    private val prefs = context.getSharedPreferences("divium_workspaces", Context.MODE_PRIVATE)

    fun list(): List<Workspace> {
        val managed = runtime.workspaces.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.map { Workspace("managed:${it.name}", it.name, it) }
            .orEmpty()
        val external = prefs.all.entries
            .filter { it.key.startsWith("external:") }
            .mapNotNull { (key, value) ->
                val raw = value as? String ?: return@mapNotNull null
                val split = raw.split("|", limit = 2)
                if (split.size != 2) return@mapNotNull null
                val id = key.removePrefix("external:")
                val mirror = File(runtime.workspaces, ".external/$id").apply { mkdirs() }
                Workspace("external:$id", split[0], mirror, split[1])
            }
        return (managed + external).sortedBy { it.name.lowercase() }
    }

    fun createManaged(name: String): Workspace {
        val safe = sanitize(name)
        require(safe.isNotBlank()) { "Project name cannot be empty" }
        val root = File(runtime.workspaces, safe)
        require(!root.exists()) { "A project named $safe already exists" }
        root.mkdirs()
        File(root, "README.md").writeText("# $safe\n")
        File(root, ".divium").mkdirs()
        return Workspace("managed:$safe", safe, root)
    }

    suspend fun attachExternal(treeUri: Uri): Workspace = withContext(Dispatchers.IO) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val doc = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Unable to open selected folder")
        val id = UUID.randomUUID().toString()
        val name = doc.name?.takeIf { it.isNotBlank() } ?: "External workspace"
        val mirror = File(runtime.workspaces, ".external/$id").apply { mkdirs() }
        prefs.edit().putString("external:$id", "$name|$treeUri").apply()
        mirrorFromDocument(doc, mirror)
        Workspace("external:$id", name, mirror, treeUri.toString())
    }

    suspend fun syncFromExternal(workspace: Workspace): List<SyncConflict> = withContext(Dispatchers.IO) {
        val uri = workspace.externalTreeUri ?: return@withContext emptyList()
        val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(uri)) ?: return@withContext emptyList()
        val conflicts = mutableListOf<SyncConflict>()
        syncDocToFile(rootDoc, workspace.root, "", conflicts)
        conflicts
    }

    suspend fun syncToExternal(workspace: Workspace) = withContext(Dispatchers.IO) {
        val uri = workspace.externalTreeUri ?: return@withContext
        val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(uri)) ?: return@withContext
        syncFileToDoc(workspace.root, rootDoc)
    }

    fun deleteWorkspace(workspace: Workspace) {
        workspace.root.deleteRecursively()
        if (workspace.externalTreeUri != null) {
            prefs.edit().remove("external:${workspace.id.removePrefix("external:")}").apply()
        }
    }

    private fun sanitize(value: String): String =
        value.trim().replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80)

    private fun mirrorFromDocument(source: DocumentFile, target: File) {
        target.mkdirs()
        source.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            if (name == ".git" && child.isDirectory) return@forEach
            val out = File(target, name)
            if (child.isDirectory) mirrorFromDocument(child, out)
            else context.contentResolver.openInputStream(child.uri)?.use { input ->
                out.parentFile?.mkdirs()
                out.outputStream().use(input::copyTo)
                if (child.lastModified() > 0) out.setLastModified(child.lastModified())
            }
        }
    }

    private fun syncDocToFile(
        source: DocumentFile,
        target: File,
        relative: String,
        conflicts: MutableList<SyncConflict>,
    ) {
        target.mkdirs()
        source.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            val rel = if (relative.isEmpty()) name else "$relative/$name"
            val out = File(target, name)
            if (child.isDirectory) {
                syncDocToFile(child, out, rel, conflicts)
            } else {
                val remoteModified = child.lastModified()
                if (out.exists() && remoteModified > 0 && out.lastModified() > remoteModified + 1500) {
                    conflicts += SyncConflict(rel, out.lastModified(), remoteModified)
                    return@forEach
                }
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    out.parentFile?.mkdirs()
                    out.outputStream().use(input::copyTo)
                    if (remoteModified > 0) out.setLastModified(remoteModified)
                }
            }
        }
    }

    private fun syncFileToDoc(source: File, target: DocumentFile) {
        source.listFiles()?.forEach { child ->
            if (child.name == ".divium") return@forEach
            val existing = target.findFile(child.name)
            if (child.isDirectory) {
                val dir = existing?.takeIf { it.isDirectory } ?: target.createDirectory(child.name)
                if (dir != null) syncFileToDoc(child, dir)
            } else {
                val mime = guessMime(child)
                val doc = existing?.takeIf { it.isFile } ?: target.createFile(mime, child.name)
                if (doc != null && (existing == null || child.lastModified() >= doc.lastModified())) {
                    context.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
                        child.inputStream().use { it.copyTo(output) }
                    }
                }
            }
        }
    }

    private fun guessMime(file: File): String = when (file.extension.lowercase()) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs", "cjs" -> "text/javascript"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "text/plain"
    }
}
