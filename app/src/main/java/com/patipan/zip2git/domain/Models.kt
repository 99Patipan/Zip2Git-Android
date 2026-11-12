package com.patipan.zip2git.domain

import androidx.annotation.StringRes
import java.io.File

/** Error whose message is a string resource, so it can be shown localized. */
class AppException(
    @StringRes val resId: Int,
    val args: List<Any> = emptyList(),
    cause: Throwable? = null,
) : Exception("AppException($resId)", cause)

/** A file extracted from the ZIP into the app cache (not held in RAM). */
data class ZipFileEntry(val path: String, val file: File, val size: Long, val gitSha: String)

data class ScanResult(
    val entries: Map<String, ZipFileEntry>,
    val skipped: Int,
    /** Single top-level folder shared by every file (typical for GitHub "Download ZIP"), or null. */
    val commonRoot: String?,
)

data class FileChange(
    val path: String,
    val kind: Kind,
    val size: Long = 0,
    /** Git file mode. MODIFY/DELETE keep the mode that already exists on the branch. */
    val mode: String = DEFAULT_MODE,
) {
    enum class Kind { ADD, MODIFY, DELETE, UNCHANGED }

    companion object {
        const val DEFAULT_MODE = "100644"
    }
}

data class RemoteEntry(val path: String, val mode: String, val sha: String)

/** Branch state captured at compare time; the head is verified again before pushing. */
/** [headSha] is null for an empty repository (no commits yet). */
data class RemoteSnapshot(val headSha: String?, val entries: Map<String, RemoteEntry>)

data class RepoInfo(val fullName: String, val defaultBranch: String) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

data class PushFile(val path: String, val mode: String, val file: File, val size: Long)
