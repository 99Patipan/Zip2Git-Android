package com.patipan.zip2git.domain

import com.patipan.zip2git.R
import java.io.File
import java.io.IOException
import java.io.InputStream

data class FolderChild(val id: String, val name: String, val isDir: Boolean)

/** Abstraction over a directory tree (Android's Storage Access Framework in the app, a fake in tests). */
interface FolderWalker {
    val rootId: String
    fun children(dirId: String): List<FolderChild>
    fun open(id: String): InputStream
}

/**
 * Copies a picked folder into the app cache with the same validation, limits and hashing as
 * [ZipScanner], so the rest of the app treats ZIP and folder sources identically.
 * Paths are relative to the picked folder. `.git`, `__MACOSX` etc. are pruned, not descended into.
 */
object FolderScanner {
    private const val MAX_DEPTH = 64

    fun scan(walker: FolderWalker, workDir: File, limits: ZipLimits = ZipLimits()): ScanResult {
        workDir.deleteRecursively()
        workDir.mkdirs()
        try {
            val session = ScanSession(workDir, limits)
            val stack = ArrayDeque<Triple<String, String, Int>>() // dirId, path prefix ("" or "a/b/"), depth
            stack.add(Triple(walker.rootId, "", 0))
            while (stack.isNotEmpty()) {
                val (dirId, prefix, depth) = stack.removeLast()
                if (depth > MAX_DEPTH) throw AppException(R.string.err_folder_too_deep)
                val children = guarded(prefix.ifEmpty { "/" }) { walker.children(dirId) }
                for (child in children.sortedBy { it.name }) {
                    val path = prefix + child.name
                    if (child.isDir) {
                        ZipScanner.normalize(path, limits)
                        if (ZipScanner.shouldSkip(path)) session.countSkipped() else stack.add(Triple(child.id, "$path/", depth + 1))
                    } else {
                        val clean = session.prepare(path) ?: continue
                        guarded(path) { walker.open(child.id).use { session.store(clean, it) } }
                    }
                }
            }
            return session.finish()
        } catch (t: Throwable) {
            workDir.deleteRecursively()
            throw t
        }
    }

    /** I/O and permission failures while reading the folder become a readable message. */
    private inline fun <T> guarded(path: String, block: () -> T): T =
        try {
            block()
        } catch (e: IOException) {
            throw AppException(R.string.err_read_file, listOf(path), e)
        } catch (e: SecurityException) {
            throw AppException(R.string.err_read_file, listOf(path), e)
        }
}
