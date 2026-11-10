package com.patipan.zip2git.domain

import com.patipan.zip2git.R
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ZipLimits(
    val maxTotalBytes: Long = 100L * MB,
    val maxFileBytes: Long = 25L * MB,
    val maxPathLength: Int = 240,
    val maxEntries: Int = 20_000,
) {
    companion object {
        const val MB = 1024L * 1024L
    }
}

/**
 * Extracts a ZIP stream to [workDir] one file at a time (nothing is kept in memory),
 * enforcing size limits *while* reading, and computes each file's Git blob SHA-1.
 */
object ZipScanner {
    fun scan(input: InputStream, workDir: File, limits: ZipLimits = ZipLimits()): ScanResult {
        workDir.deleteRecursively()
        workDir.mkdirs()
        try {
            return doScan(input, workDir, limits)
        } catch (t: Throwable) {
            workDir.deleteRecursively()
            throw t
        }
    }

    private fun doScan(input: InputStream, workDir: File, limits: ZipLimits): ScanResult {
        val result = linkedMapOf<String, ZipFileEntry>()
        val seenLower = HashSet<String>()
        var total = 0L
        var skipped = 0
        var counter = 0
        val buf = ByteArray(16 * 1024)

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val path = normalize(entry.name, limits)
                if (shouldSkip(path)) {
                    skipped++
                    zip.closeEntry()
                    continue
                }
                if (result.size >= limits.maxEntries) {
                    throw AppException(R.string.err_too_many_files, listOf(limits.maxEntries))
                }
                if (!seenLower.add(path.lowercase())) {
                    throw AppException(R.string.err_duplicate_path, listOf(path))
                }

                val tmp = File(workDir, "f${counter++}.bin")
                var size = 0L
                tmp.outputStream().buffered().use { out ->
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        size += n
                        if (size > limits.maxFileBytes) {
                            throw AppException(
                                R.string.err_file_too_large,
                                listOf(limits.maxFileBytes / ZipLimits.MB, path),
                            )
                        }
                        if (total + size > limits.maxTotalBytes) {
                            throw AppException(R.string.err_zip_too_large, listOf(limits.maxTotalBytes / ZipLimits.MB))
                        }
                        out.write(buf, 0, n)
                    }
                }
                total += size
                result[path] = ZipFileEntry(path, tmp, size, GitBlob.sha1(tmp, size))
                zip.closeEntry()
            }
        }

        if (result.isEmpty()) throw AppException(R.string.err_zip_empty)
        return ScanResult(result, skipped, commonRoot(result.keys))
    }

    /** Rejects absolute paths, `..`, drive letters and control characters. */
    internal fun normalize(rawName: String, limits: ZipLimits): String {
        val p = rawName.replace('\\', '/')
        val segments = p.split('/')
        val unsafe = p.startsWith("/") ||
            p.any { it.isISOControl() } ||
            segments.any { it.isEmpty() || it == "." || it == ".." } ||
            segments.first().contains(':')
        if (unsafe) throw AppException(R.string.err_unsafe_path, listOf(rawName))
        if (p.length > limits.maxPathLength) throw AppException(R.string.err_path_too_long, listOf(rawName))
        return p
    }

    /** macOS / Windows metadata and any `.git` directory are never uploaded. */
    internal fun shouldSkip(path: String): Boolean {
        val segments = path.split('/')
        val name = segments.last()
        return segments.first() == "__MACOSX" ||
            segments.any { it == ".git" } ||
            name == ".DS_Store" || name == "Thumbs.db"
    }

    internal fun commonRoot(paths: Collection<String>): String? {
        if (paths.isEmpty() || paths.any { !it.contains('/') }) return null
        val roots = paths.map { it.substringBefore('/') }.toSet()
        return roots.singleOrNull()
    }

    fun stripRoot(entries: Map<String, ZipFileEntry>, root: String): Map<String, ZipFileEntry> {
        val prefix = "$root/"
        val out = linkedMapOf<String, ZipFileEntry>()
        for (e in entries.values) {
            val p = e.path.removePrefix(prefix)
            out[p] = e.copy(path = p)
        }
        return out
    }
}
