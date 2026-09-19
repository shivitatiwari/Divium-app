package com.divium.ide.files

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class FileNode(
    val file: File,
    val relativePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
)

data class SearchHit(val file: File, val line: Int, val preview: String)

class FileService(private val root: File) {
    private val canonicalRoot = root.canonicalFile

    fun resolve(relative: String): File {
        val candidate = File(root, relative).canonicalFile
        require(candidate.path == canonicalRoot.path || candidate.path.startsWith(canonicalRoot.path + File.separator)) {
            "Path escapes workspace"
        }
        return candidate
    }

    fun list(relative: String = "", showHidden: Boolean = false): List<FileNode> {
        val dir = resolve(relative)
        return dir.listFiles()
            ?.filter { showHidden || !it.name.startsWith(".") }
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map {
                FileNode(it, it.relativeTo(root).path, it.isDirectory, if (it.isFile) it.length() else 0, it.lastModified())
            }.orEmpty()
    }

    fun allFiles(showHidden: Boolean = false, limit: Int = 20_000): List<FileNode> =
        root.walkTopDown()
            .onEnter { dir -> showHidden || dir == root || !dir.name.startsWith(".") }
            .filter { it.isFile && (showHidden || !it.name.startsWith(".")) }
            .take(limit)
            .map { FileNode(it, it.relativeTo(root).path, false, it.length(), it.lastModified()) }
            .toList()

    fun read(relative: String): String = resolve(relative).readText()

    fun write(relative: String, text: String) {
        val target = resolve(relative)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.divium-tmp")
        temp.writeText(text)
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Unable to save $relative" }
    }

    fun createFile(relative: String): File = resolve(relative).also {
        it.parentFile?.mkdirs()
        require(!it.exists()) { "File already exists" }
        it.createNewFile()
    }

    fun createDirectory(relative: String): File = resolve(relative).also {
        require(!it.exists()) { "Directory already exists" }
        it.mkdirs()
    }

    fun rename(relative: String, newName: String): File {
        require('/' !in newName && '\\' !in newName) { "Invalid name" }
        val source = resolve(relative)
        val target = File(source.parentFile, newName)
        require(target.canonicalPath.startsWith(canonicalRoot.path))
        check(source.renameTo(target)) { "Unable to rename" }
        return target
    }

    fun delete(relative: String) {
        val target = resolve(relative)
        require(target.canonicalFile != canonicalRoot) { "Workspace root cannot be deleted" }
        check(target.deleteRecursively()) { "Unable to delete" }
    }

    fun duplicate(relative: String): File {
        val source = resolve(relative)
        val target = uniqueSibling(source)
        if (source.isDirectory) source.copyRecursively(target) else source.copyTo(target)
        return target
    }

    fun move(from: String, toDirectory: String): File {
        val source = resolve(from)
        val dir = resolve(toDirectory)
        require(dir.isDirectory)
        val target = File(dir, source.name)
        require(!target.exists())
        if (!source.renameTo(target)) {
            if (source.isDirectory) source.copyRecursively(target) else source.copyTo(target)
            source.deleteRecursively()
        }
        return target
    }

    fun copy(from: String, toDirectory: String): File {
        val source = resolve(from)
        val dir = resolve(toDirectory)
        require(dir.isDirectory)
        val target = uniqueFile(File(dir, source.name))
        if (source.isDirectory) source.copyRecursively(target) else source.copyTo(target)
        return target
    }

    fun search(query: String, caseSensitive: Boolean = false, regex: Boolean = false, limit: Int = 500): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val matcher = if (regex) Regex(query, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)) else null
        val hits = mutableListOf<SearchHit>()
        for (node in allFiles(limit = 10_000)) {
            if (node.size > 2_000_000) continue
            runCatching {
                node.file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        val found = matcher?.containsMatchIn(line)
                            ?: if (caseSensitive) line.contains(query) else line.contains(query, ignoreCase = true)
                        if (found && hits.size < limit) hits += SearchHit(node.file, index + 1, line.trim().take(180))
                    }
                }
            }
            if (hits.size >= limit) break
        }
        return hits
    }

    fun zip(relative: String, outputRelative: String) {
        val source = resolve(relative)
        val output = resolve(outputRelative)
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            val base = if (source.isDirectory) source else source.parentFile
            val entries = if (source.isDirectory) source.walkTopDown() else sequenceOf(source)
            entries.filter { it.isFile }.forEach { file ->
                val name = file.relativeTo(base).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun unzip(relativeZip: String, outputDirectory: String) {
        val zipFile = resolve(relativeZip)
        val output = resolve(outputDirectory).apply { mkdirs() }
        val outputCanonical = output.canonicalFile
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = File(output, entry.name).canonicalFile
                require(target.path == outputCanonical.path || target.path.startsWith(outputCanonical.path + File.separator)) {
                    "Unsafe ZIP entry"
                }
                if (entry.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun uniqueSibling(source: File): File =
        uniqueFile(File(source.parentFile, source.nameWithoutExtension + "-copy" + if (source.extension.isBlank()) "" else ".${source.extension}"))

    private fun uniqueFile(initial: File): File {
        if (!initial.exists()) return initial
        val ext = initial.extension
        val base = initial.nameWithoutExtension
        var index = 2
        while (true) {
            val candidate = File(initial.parentFile, "$base-$index" + if (ext.isBlank()) "" else ".$ext")
            if (!candidate.exists()) return candidate
            index++
        }
    }
}
