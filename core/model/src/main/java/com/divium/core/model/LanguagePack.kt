package com.divium.core.model

enum class LanguagePack(
    val id: String,
    val title: String,
    val description: String,
) {
    JAVASCRIPT("javascript", "JavaScript / TypeScript", "Node.js, npm and TypeScript tooling"),
    PYTHON("python", "Python", "Python runtime and language tooling"),
    KOTLIN_JAVA("kotlin-java", "Kotlin / Java", "JDK, Kotlin and JVM tooling"),
    CPP("cpp", "C / C++", "Clang-based native development"),
    RUST("rust", "Rust", "Rust toolchain and analyzer"),
    GO("go", "Go", "Go toolchain and language server"),
    PHP("php", "PHP", "PHP runtime and tooling");

    companion object {
        fun fromId(id: String): LanguagePack? = entries.firstOrNull { it.id == id }
    }
}
