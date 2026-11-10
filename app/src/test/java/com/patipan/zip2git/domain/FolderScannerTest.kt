package com.patipan.zip2git.domain

import com.patipan.zip2git.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/** In-memory tree: keys are slash paths like "src/a.kt"; every directory is derived from them. */
private class FakeWalker(files: Map<String, String>, private val unreadable: Set<String> = emptySet()) : FolderWalker {
    private val content = files
    override val rootId = ""

    override fun children(dirId: String): List<FolderChild> {
        val prefix = if (dirId.isEmpty()) "" else "$dirId/"
        val names = content.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
        val result = linkedMapOf<String, FolderChild>()
        for (rest in names) {
            val first = rest.substringBefore('/')
            val id = prefix + first
            result[first] = FolderChild(id, first, isDir = rest.contains('/'))
        }
        return result.values.toList()
    }

    override fun open(id: String): InputStream {
        if (id in unreadable) throw IOException("denied")
        return ByteArrayInputStream(content.getValue(id).toByteArray())
    }
}

class FolderScannerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun scan(files: Map<String, String>, limits: ZipLimits = ZipLimits(), unreadable: Set<String> = emptySet()) =
        FolderScanner.scan(FakeWalker(files, unreadable), tmp.newFolder(), limits)

    private fun expectError(res: Int, block: () -> Unit) {
        try {
            block()
            fail("expected AppException")
        } catch (e: AppException) {
            assertEquals(res, e.resId)
        }
    }

    @Test fun walksNestedFolders() {
        val r = scan(mapOf("README.md" to "hello\n", "src/main/A.kt" to "a", "src/B.kt" to "b"))
        assertEquals(setOf("README.md", "src/main/A.kt", "src/B.kt"), r.entries.keys)
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", r.entries.getValue("README.md").gitSha)
        assertNull(r.commonRoot)
    }

    @Test fun prunesGitAndSystemDirectories() {
        val r = scan(mapOf("a.txt" to "1", ".git/config" to "x", "__MACOSX/._a" to "x", "sub/.DS_Store" to "x", "sub/ok.txt" to "2"))
        assertEquals(setOf("a.txt", "sub/ok.txt"), r.entries.keys)
        assertEquals(3, r.skipped)
    }

    @Test fun detectsSingleRootFolder() {
        assertEquals("proj", scan(mapOf("proj/a.txt" to "1", "proj/b/c.txt" to "2")).commonRoot)
    }

    @Test fun enforcesLimits() {
        expectError(R.string.err_file_too_large) { scan(mapOf("big" to "x".repeat(50)), ZipLimits(maxFileBytes = 10)) }
        expectError(R.string.err_source_too_large) {
            scan(mapOf("a" to "x".repeat(8), "b" to "y".repeat(8)), ZipLimits(maxTotalBytes = 12))
        }
    }

    @Test fun caseInsensitiveDuplicatesRejected() {
        expectError(R.string.err_duplicate_path) { scan(mapOf("Foo.txt" to "1", "foo.txt" to "2")) }
    }

    @Test fun unreadableFileGivesReadableError() {
        expectError(R.string.err_read_file) { scan(mapOf("a.txt" to "1", "b.txt" to "2"), unreadable = setOf("b.txt")) }
    }

    @Test fun emptyFolderRejected() {
        expectError(R.string.err_source_empty) { scan(emptyMap()) }
    }
}
