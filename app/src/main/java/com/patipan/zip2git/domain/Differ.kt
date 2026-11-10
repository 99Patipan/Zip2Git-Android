package com.patipan.zip2git.domain

import com.patipan.zip2git.domain.FileChange.Kind

object Differ {
    fun diff(local: Map<String, ZipFileEntry>, remote: Map<String, RemoteEntry>): List<FileChange> =
        (local.keys + remote.keys).toSortedSet().map { path ->
            val l = local[path]
            val r = remote[path]
            when {
                l != null && r == null -> FileChange(path, Kind.ADD, l.size)
                l != null && r != null ->
                    FileChange(path, if (l.gitSha == r.sha) Kind.UNCHANGED else Kind.MODIFY, l.size, r.mode)
                else -> FileChange(path, Kind.DELETE, 0, r?.mode ?: FileChange.DEFAULT_MODE)
            }
        }

    /** True when the ZIP would wipe a large part of the branch (usually a wrong root folder). */
    fun massDeleteSuspected(changes: List<FileChange>, remoteCount: Int): Boolean {
        val deletes = changes.count { it.kind == Kind.DELETE }
        return deletes >= 5 && deletes * 100 >= remoteCount * 30
    }
}
