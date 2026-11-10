package com.patipan.zip2git.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patipan.zip2git.R
import com.patipan.zip2git.data.GitHubApi
import com.patipan.zip2git.data.TokenStore
import com.patipan.zip2git.data.ZipImporter
import com.patipan.zip2git.domain.AppException
import com.patipan.zip2git.domain.Differ
import com.patipan.zip2git.domain.FileChange
import com.patipan.zip2git.domain.FileChange.Kind
import com.patipan.zip2git.domain.PushFile
import com.patipan.zip2git.domain.RemoteSnapshot
import com.patipan.zip2git.domain.RepoInfo
import com.patipan.zip2git.domain.ScanResult
import com.patipan.zip2git.domain.ZipFileEntry
import com.patipan.zip2git.domain.ZipScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class UiState(
    val token: String = "",
    val tokenInput: String = "",
    val showToken: Boolean = false,
    val username: String = "",
    val repos: List<RepoInfo> = emptyList(),
    val branches: List<String> = emptyList(),
    val selectedRepo: RepoInfo? = null,
    val selectedBranch: String = "",
    val zipName: String? = null,
    val zipFileCount: Int = 0,
    val zipTotalBytes: Long = 0,
    val commonRoot: String? = null,
    val stripRoot: Boolean = false,
    val changes: List<FileChange> = emptyList(),
    val showUnchanged: Boolean = false,
    val confirmDeletes: Boolean = false,
    val massDeleteWarning: String? = null,
    val commitMessage: String = "Update from Zip2Git",
    val status: String = "",
    val busy: Boolean = false,
    val progress: Float? = null,
)

/**
 * Holds all screen state and runs all work in [viewModelScope], so rotation no longer
 * cancels a running scan/compare/push and no state is lost.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    var state by mutableStateOf(UiState(status = app.getString(R.string.status_start)))
        private set

    private val tokenStore = TokenStore(app)
    private var api: GitHubApi? = null
    private var zip: ScanResult? = null
    private var remote: RemoteSnapshot? = null

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { tokenStore.load() }
            if (!saved.isNullOrBlank()) {
                update { copy(token = saved, tokenInput = saved) }
                launchBusy { connectWith(saved, persist = false) }
            }
        }
    }

    override fun onCleared() {
        ZipImporter.clear(getApplication<Application>())
    }

    // ---------- simple setters ----------

    fun onTokenInputChange(v: String) = update { copy(tokenInput = v) }
    fun toggleShowToken() = update { copy(showToken = !showToken) }
    fun setShowUnchanged(v: Boolean) = update { copy(showUnchanged = v) }
    fun setConfirmDeletes(v: Boolean) = update { copy(confirmDeletes = v) }
    fun setCommitMessage(v: String) = update { copy(commitMessage = v) }

    fun setStripRoot(v: Boolean) {
        clearComparison()
        update { copy(stripRoot = v) }
    }

    // ---------- connection ----------

    fun connect() {
        val t = state.tokenInput.trim()
        if (t.isBlank()) return
        launchBusy { connectWith(t, persist = true) }
    }

    fun disconnect() {
        if (state.busy) return
        api = null
        zip = null
        remote = null
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            tokenStore.clear()
            ZipImporter.clear(app)
        }
        state = UiState(status = str(R.string.status_token_removed))
    }

    private suspend fun connectWith(token: String, persist: Boolean) {
        val client = GitHubApi(token)
        val (login, repos) = safely { client.profile().optString("login") to client.repositories() }
            .getOrElse { fail(it); return }
        api = client
        var status = str(R.string.status_connected, login)
        if (persist && !withContext(Dispatchers.IO) { tokenStore.save(token) }) {
            status = str(R.string.err_token_not_saved)
        }
        update { copy(token = token, tokenInput = token, username = login, repos = repos, status = status) }
    }

    // ---------- destination ----------

    fun selectRepo(fullName: String) {
        if (state.busy) return
        val repo = state.repos.firstOrNull { it.fullName == fullName } ?: return
        val client = api ?: return
        clearComparison()
        update { copy(selectedRepo = repo, branches = emptyList(), selectedBranch = "") }
        refreshStripDefault()
        launchBusy {
            val list = safely { client.branches(repo.owner, repo.name) }.getOrElse { fail(it); return@launchBusy }
            if (list.isEmpty()) {
                update { copy(status = str(R.string.err_empty_repo)) }
                return@launchBusy
            }
            val chosen = list.firstOrNull { it == repo.defaultBranch } ?: list.first()
            update { copy(branches = list, selectedBranch = chosen, status = str(R.string.status_loaded_branches, list.size)) }
        }
    }

    fun selectBranch(branch: String) {
        clearComparison()
        update { copy(selectedBranch = branch) }
    }

    // ---------- ZIP ----------

    fun onZipPicked(uri: Uri) {
        launchBusy {
            val app = getApplication<Application>()
            // The scan wipes the previous extraction, so forget it first.
            zip = null
            clearComparison()
            update {
                copy(zipName = null, zipFileCount = 0, zipTotalBytes = 0, commonRoot = null, stripRoot = false,
                    status = str(R.string.status_scanning))
            }
            val scan = safely { withContext(Dispatchers.IO) { ZipImporter.import(app, uri) } }
                .getOrElse { fail(it); return@launchBusy }
            val name = withContext(Dispatchers.IO) { ZipImporter.displayName(app, uri) }
            zip = scan
            val status = if (scan.skipped > 0) {
                str(R.string.status_zip_ready_skipped, scan.entries.size, scan.skipped)
            } else {
                str(R.string.status_zip_ready, scan.entries.size)
            }
            update {
                copy(zipName = name, zipFileCount = scan.entries.size, zipTotalBytes = scan.entries.values.sumOf { it.size },
                    commonRoot = scan.commonRoot, status = status)
            }
            refreshStripDefault()
        }
    }

    /** GitHub "Download ZIP" wraps everything in `<repo>-<branch>/`; strip it by default in that case. */
    private fun refreshStripDefault() {
        val root = zip?.commonRoot
        val repo = state.selectedRepo
        val guess = root != null && repo != null && root.startsWith(repo.name + "-", ignoreCase = true)
        update { copy(stripRoot = guess) }
    }

    private fun effectiveEntries(): Map<String, ZipFileEntry> {
        val z = zip ?: return emptyMap()
        val root = z.commonRoot
        return if (state.stripRoot && root != null) ZipScanner.stripRoot(z.entries, root) else z.entries
    }

    // ---------- compare ----------

    fun compare() {
        launchBusy {
            val client = api
            val repo = state.selectedRepo
            val branch = state.selectedBranch
            val local = effectiveEntries()
            if (client == null || repo == null || branch.isBlank() || local.isEmpty()) {
                update { copy(status = str(R.string.status_choose_first)) }
                return@launchBusy
            }
            update { copy(status = str(R.string.status_comparing)) }
            val (snapshot, changes) = safely {
                val snap = client.snapshot(repo.owner, repo.name, branch)
                snap to withContext(Dispatchers.Default) { Differ.diff(local, snap.entries) }
            }.getOrElse { fail(it); return@launchBusy }

            remote = snapshot
            val pending = changes.count { it.kind != Kind.UNCHANGED }
            val warning = if (Differ.massDeleteSuspected(changes, snapshot.entries.size)) {
                str(R.string.warn_mass_delete, changes.count { it.kind == Kind.DELETE }, snapshot.entries.size)
            } else null
            update {
                copy(changes = changes, confirmDeletes = false, massDeleteWarning = warning,
                    status = str(R.string.status_compare_done, pending))
            }
        }
    }

    // ---------- push ----------

    fun push() {
        launchBusy {
            val client = api
            val repo = state.selectedRepo
            val branch = state.selectedBranch
            val head = remote?.headSha
            val pending = state.changes.filter { it.kind != Kind.UNCHANGED }
            val message = state.commitMessage.trim()

            if (client == null || repo == null || head == null || branch.isBlank()) {
                update { copy(status = str(R.string.status_choose_first)) }
                return@launchBusy
            }
            if (pending.isEmpty()) {
                update { copy(status = str(R.string.status_nothing_to_push)) }
                return@launchBusy
            }
            if (message.isBlank()) {
                update { copy(status = str(R.string.err_empty_message)) }
                return@launchBusy
            }
            if (pending.any { it.kind == Kind.DELETE } && !state.confirmDeletes) {
                update { copy(status = str(R.string.err_delete_confirm)) }
                return@launchBusy
            }

            val entries = effectiveEntries()
            val additions = pending.filter { it.kind == Kind.ADD || it.kind == Kind.MODIFY }
                .map { PushFile(it.path, it.mode, entries.getValue(it.path).file, it.size) }
            val deletions = pending.filter { it.kind == Kind.DELETE }.map { it.path }

            if (additions.isNotEmpty()) {
                update { copy(progress = 0f, status = str(R.string.status_pushing, 0, additions.size)) }
            }
            val sha = safely {
                client.push(repo.owner, repo.name, branch, message, head, additions, deletions) { done, total ->
                    update {
                        copy(
                            progress = done.toFloat() / total,
                            status = if (done >= total) str(R.string.status_committing)
                            else str(R.string.status_pushing, done, total),
                        )
                    }
                }
            }.getOrElse { fail(it); return@launchBusy }

            remote = null
            update {
                copy(changes = emptyList(), confirmDeletes = false, massDeleteWarning = null,
                    status = str(R.string.status_push_done, sha.take(7)))
            }
        }
    }

    // ---------- helpers ----------

    private fun clearComparison() {
        remote = null
        update { copy(changes = emptyList(), confirmDeletes = false, massDeleteWarning = null) }
    }

    private fun fail(t: Throwable) = update { copy(status = errorText(t)) }

    private fun errorText(t: Throwable): String = when (t) {
        is AppException -> str(t.resId, *t.args.toTypedArray())
        is IOException -> str(R.string.err_network, t.message ?: "")
        else -> t.message ?: str(R.string.err_unknown)
    }

    private fun str(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private inline fun update(block: UiState.() -> UiState) {
        state = state.block()
    }

    /** Runs one operation at a time; shows the busy indicator for its duration. */
    private fun launchBusy(block: suspend () -> Unit) {
        if (state.busy) return
        update { copy(busy = true, progress = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                update { copy(busy = false, progress = null) }
            }
        }
    }

    private suspend fun <T> safely(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
}
