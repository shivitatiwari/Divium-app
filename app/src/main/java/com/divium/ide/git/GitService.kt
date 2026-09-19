package com.divium.ide.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit
import java.io.File

data class GitStatusSnapshot(
    val added: Set<String>,
    val changed: Set<String>,
    val modified: Set<String>,
    val missing: Set<String>,
    val removed: Set<String>,
    val untracked: Set<String>,
    val conflicting: Set<String>,
    val clean: Boolean,
)

data class GitCommitInfo(
    val id: String,
    val shortId: String,
    val message: String,
    val author: String,
    val timeSeconds: Int,
)

class GitService(private val root: File) {
    fun isRepository(): Boolean = File(root, ".git").isDirectory

    fun init() {
        Git.init().setDirectory(root).call().close()
    }

    private fun open(): Git = Git.open(root)

    fun status(): GitStatusSnapshot = open().use { git ->
        val s = git.status().call()
        GitStatusSnapshot(
            added = s.added,
            changed = s.changed,
            modified = s.modified,
            missing = s.missing,
            removed = s.removed,
            untracked = s.untracked,
            conflicting = s.conflicting,
            clean = s.isClean,
        )
    }

    fun stage(path: String) {
        open().use { it.add().addFilepattern(path).call() }
    }

    fun stageAll() {
        open().use {
            it.add().addFilepattern(".").call()
            it.add().setUpdate(true).addFilepattern(".").call()
        }
    }

    fun unstage(path: String) {
        open().use { it.reset().addPath(path).call() }
    }

    fun commit(message: String, authorName: String, authorEmail: String): GitCommitInfo =
        open().use { git ->
            git.commit()
                .setMessage(message)
                .setAuthor(authorName, authorEmail)
                .call()
                .toInfo()
        }

    fun branches(): List<String> = open().use { git ->
        git.branchList().call().map(Ref::getName).map(::shortBranch)
    }

    fun currentBranch(): String = open().use { it.repository.branch }

    fun createBranch(name: String, checkout: Boolean = true) {
        open().use { git ->
            git.branchCreate().setName(name).call()
            if (checkout) git.checkout().setName(name).call()
        }
    }

    fun checkout(name: String) {
        open().use { it.checkout().setName(name).call() }
    }

    fun deleteBranch(name: String, force: Boolean = false) {
        open().use { it.branchDelete().setBranchNames(name).setForce(force).call() }
    }

    fun merge(branchName: String): String = open().use { git ->
        val ref = git.repository.findRef(branchName) ?: error("Branch not found: $branchName")
        git.merge().include(ref).call().mergeStatus.toString()
    }

    fun log(limit: Int = 100): List<GitCommitInfo> = open().use { git ->
        git.log().setMaxCount(limit).call().map { it.toInfo() }
    }

    fun stash(): String? = open().use { it.stashCreate().call()?.name }

    fun stashes(): List<GitCommitInfo> = open().use { git ->
        git.stashList().call().map { it.toInfo() }
    }

    fun addRemote(url: String, name: String = "origin") {
        open().use { git ->
            val config = git.repository.config
            config.setString("remote", name, "url", url)
            config.setString("remote", name, "fetch", "+refs/heads/*:refs/remotes/$name/*")
            config.save()
        }
    }

    fun fetch() {
        open().use { it.fetch().setRemote("origin").call() }
    }

    fun pull() {
        open().use { it.pull().call() }
    }

    fun push() {
        open().use { it.push().setPushAll().call() }
    }

    companion object {
        fun clonePublic(url: String, directory: File): GitService {
            directory.parentFile?.mkdirs()
            Git.cloneRepository().setURI(url).setDirectory(directory).call().close()
            return GitService(directory)
        }
    }
}

private fun RevCommit.toInfo() = GitCommitInfo(
    id = name,
    shortId = name.take(7),
    message = shortMessage,
    author = authorIdent?.name ?: "Unknown",
    timeSeconds = commitTime,
)

private fun shortBranch(full: String): String =
    full.removePrefix(Constants.R_HEADS).removePrefix(Constants.R_REMOTES)
