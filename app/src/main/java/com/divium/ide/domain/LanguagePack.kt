package com.divium.ide.domain

data class LanguagePack(
    val id: String,
    val name: String,
    val description: String,
    val estimatedDownload: String,
)

object LanguageCatalog {
    val starterPacks = listOf(
        LanguagePack("javascript", "JavaScript & TypeScript", "Node, npm, TypeScript and editor tooling", "74 MB"),
        LanguagePack("python", "Python", "Python, pip and editor tooling", "42 MB"),
        LanguagePack("kotlin", "Kotlin & Java", "JDK, Gradle and Android tooling", "188 MB"),
        LanguagePack("cpp", "C & C++", "Clang, CMake and debugger tooling", "126 MB"),
        LanguagePack("rust", "Rust", "Cargo, rustc and analyzer tooling", "204 MB"),
        LanguagePack("go", "Go", "Go toolchain and language server", "91 MB"),
    )

    fun totalSizeFor(ids: Set<String>): String =
        if (ids.isEmpty()) "No packs selected" else ids.size.toString() + " selected"
}
