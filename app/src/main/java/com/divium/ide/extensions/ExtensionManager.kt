package com.divium.ide.extensions

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

data class ExtensionManifest(
    val id: String,
    val name: String,
    val displayName: String,
    val version: String,
    val publisher: String,
    val main: String?,
    val browser: String?,
    val categories: List<String>,
    val permissions: Set<String>,
    val directory: File,
)

class ExtensionManager(private val context: Context) {
    private val root = File(context.filesDir, "extensions").apply { mkdirs() }

    fun installed(): List<ExtensionManifest> =
        root.listFiles()?.filter { it.isDirectory }?.mapNotNull(::readManifest).orEmpty()

    fun installVsix(uri: Uri): ExtensionManifest {
        val temp = File(context.cacheDir, "extension-${System.nanoTime()}").apply { mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val normalized = entry.name.removePrefix("extension/")
                    if (normalized.isNotBlank()) {
                        val target = File(temp, normalized).canonicalFile
                        require(target.path.startsWith(temp.canonicalPath + File.separator)) { "Unsafe extension archive" }
                        if (entry.isDirectory) target.mkdirs() else {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Unable to read extension")

        val manifest = readManifest(temp) ?: error("Extension has no valid package.json")
        val destination = File(root, manifest.id)
        destination.deleteRecursively()
        check(temp.renameTo(destination)) { "Unable to install extension" }
        return readManifest(destination) ?: error("Unable to load installed extension")
    }

    fun uninstall(id: String) {
        val dir = File(root, id).canonicalFile
        require(dir.parentFile == root.canonicalFile)
        dir.deleteRecursively()
    }

    private fun readManifest(directory: File): ExtensionManifest? {
        val packageFile = File(directory, "package.json")
        if (!packageFile.isFile) return null
        return runCatching {
            val json = JSONObject(packageFile.readText())
            val name = json.getString("name")
            val publisher = json.optString("publisher", "unknown")
            val permissionsJson = json.optJSONArray("diviumPermissions")
            val permissions = buildSet {
                if (permissionsJson != null) {
                    for (i in 0 until permissionsJson.length()) add(permissionsJson.getString(i))
                }
            }
            val categoriesJson = json.optJSONArray("categories")
            val categories = buildList {
                if (categoriesJson != null) {
                    for (i in 0 until categoriesJson.length()) add(categoriesJson.getString(i))
                }
            }
            ExtensionManifest(
                id = "$publisher.$name",
                name = name,
                displayName = json.optString("displayName", name),
                version = json.optString("version", "0.0.0"),
                publisher = publisher,
                main = json.optString("main").takeIf(String::isNotBlank),
                browser = json.optString("browser").takeIf(String::isNotBlank),
                categories = categories,
                permissions = permissions,
                directory = directory,
            )
        }.getOrNull()
    }
}
