package com.divium.ide.project

import org.json.JSONObject
import java.io.File

data class ProjectAction(
    val id: String,
    val label: String,
    val command: String,
    val background: Boolean = false,
    val localhost: Boolean = false,
)

data class ProjectProfile(
    val kind: String,
    val actions: List<ProjectAction>,
)

object ProjectIntelligence {
    fun detect(root: File): ProjectProfile {
        val packageJson = File(root, "package.json")
        if (packageJson.isFile) return detectNode(packageJson)

        if (File(root, "build.gradle.kts").exists() || File(root, "build.gradle").exists()) {
            val gradlew = if (File(root, "gradlew").exists()) "./gradlew" else "gradle"
            return ProjectProfile(
                "Gradle",
                listOf(
                    ProjectAction("run", "Run", "$gradlew run"),
                    ProjectAction("build", "Build", "$gradlew build"),
                    ProjectAction("test", "Test", "$gradlew test"),
                    ProjectAction("clean", "Clean", "$gradlew clean"),
                ),
            )
        }

        if (File(root, "Cargo.toml").exists()) return ProjectProfile(
            "Rust",
            listOf(
                ProjectAction("run", "Run", "cargo run"),
                ProjectAction("build", "Build", "cargo build"),
                ProjectAction("test", "Test", "cargo test"),
                ProjectAction("format", "Format", "cargo fmt"),
            ),
        )

        if (File(root, "go.mod").exists()) return ProjectProfile(
            "Go",
            listOf(
                ProjectAction("run", "Run", "go run ."),
                ProjectAction("build", "Build", "go build ./..."),
                ProjectAction("test", "Test", "go test ./..."),
                ProjectAction("format", "Format", "gofmt -w ."),
            ),
        )

        if (File(root, "pyproject.toml").exists() || File(root, "requirements.txt").exists() || root.listFiles()?.any { it.extension == "py" } == true) {
            val entry = listOf("main.py", "app.py", "server.py").firstOrNull { File(root, it).exists() }
            return ProjectProfile(
                "Python",
                buildList {
                    if (File(root, "requirements.txt").exists()) add(ProjectAction("install", "Install", "python -m pip install -r requirements.txt"))
                    if (entry != null) add(ProjectAction("run", "Run", "python $entry"))
                    add(ProjectAction("test", "Test", "python -m pytest"))
                },
            )
        }

        if (File(root, "CMakeLists.txt").exists()) return ProjectProfile(
            "CMake",
            listOf(
                ProjectAction("configure", "Configure", "cmake -S . -B build"),
                ProjectAction("build", "Build", "cmake --build build"),
                ProjectAction("test", "Test", "ctest --test-dir build"),
            ),
        )

        return ProjectProfile("Generic", emptyList())
    }

    private fun detectNode(packageJson: File): ProjectProfile {
        val json = runCatching { JSONObject(packageJson.readText()) }.getOrNull()
        val scripts = json?.optJSONObject("scripts")
        fun has(name: String) = scripts?.has(name) == true
        val manager = when {
            File(packageJson.parentFile, "pnpm-lock.yaml").exists() -> "pnpm"
            File(packageJson.parentFile, "yarn.lock").exists() -> "yarn"
            else -> "npm"
        }
        fun script(name: String) = if (manager == "yarn") "yarn $name" else "$manager run $name"
        return ProjectProfile(
            "Node.js",
            buildList {
                add(ProjectAction("install", "Install", "$manager install"))
                val dev = listOf("dev", "start", "serve").firstOrNull(::has)
                if (dev != null) add(ProjectAction("localhost", "Run localhost", script(dev), background = true, localhost = true))
                if (has("build")) add(ProjectAction("build", "Build", script("build")))
                if (has("test")) add(ProjectAction("test", "Test", script("test")))
                if (has("lint")) add(ProjectAction("lint", "Lint", script("lint")))
                if (has("format")) add(ProjectAction("format", "Format", script("format")))
            },
        )
    }
}
