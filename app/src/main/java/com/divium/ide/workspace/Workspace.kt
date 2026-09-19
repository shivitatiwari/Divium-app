package com.divium.ide.workspace

import java.io.File

data class Workspace(
    val id: String,
    val name: String,
    val root: File,
    val externalTreeUri: String? = null,
)

data class SyncConflict(
    val relativePath: String,
    val localModified: Long,
    val remoteModified: Long,
)
