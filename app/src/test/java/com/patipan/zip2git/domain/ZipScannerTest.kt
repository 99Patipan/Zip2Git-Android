package com.patipan.zip2git.domain

import com.patipan.zip2git.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipScannerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zip(vararg files: Pair<String, String>): ByteArrayInputStream {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, content) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray())
                z.closeEntry()
            }
        }
        return ByteArrayInputStream(bos.toByteArray())
    }

    private fun scan(input: ByteArrayInputStream, limits: ZipLimits = ZipLimits()) =
        ZipScanner.scan(input, tmp.newFolder(), limits)

    private fun expectError(res: Int, block: () -> Unit) {
        try {
            block()
            fail("expected AppException")
        } catch (e: AppException) {
            assertEquals(res, e.resId)
        }
    }

    @Test fun readsFilesAndComputesGitSha() {
        val r = scan(zip("a.txt" to "hello\n", "dir/b.txt" to "x"))
        assertEquals(setOf("a.txt", "dir/b.txt"), r.entries.keys)
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", r.entries.getValue("a.txt").gitSha)
        assertNull(r.commonRoot)
    }

    @Test fun detectsAndStripsCommonRoot() {
        val r = scan(zip("repo-main/a.txt" to "1", "repo-main/src/b.txt" to "2"))
        assertEquals("repo-main", r.commonRoot)
        assertEquals(setOf("a.txt", "src/b.txt"), ZipScanner.stripRoot(r.entries, "repo-main").keys)
    }

    @Test fun noCommonRootWhenFileAtTopLevel() {
        assertNull(scan(zip("repo/a.txt" to "1", "README.md" to "2")).commonRoot)
    }

    @Test fun skipsSystemFiles() {
        val r = scan(zip("a.txt" to "1", "__MACOSX/._a.txt" to "x", ".DS_Store" to "x", "p/.git/config" to "x", ".gitignore" to "x"))
        assertEquals(setOf("a.txt", ".gitignore"), r.entries.keys)
        assertEquals(3, r.skipped)
    }

    @Test fun rejectsUnsafePaths() {
        for (bad in listOf("../evil.txt", "a/../../b", "/abs.txt", "C:/x.txt", "a//b.txt")) {
            expectError(R.string.err_unsafe_path) { scan(zip(bad to "x")) }
        }
    }

    @Test fun rejectsCaseInsensitiveDuplicates() {
        expectError(R.string.err_duplicate_path) { scan(zip("Foo.txt" to "1", "foo.txt" to "2")) }
    }

    @Test fun enforcesFileLimitWhileReading() {
        expectError(R.string.err_file_too_large) {
            scan(zip("big.bin" to "x".repeat(50)), ZipLimits(maxFileBytes = 10))
        }
    }

    @Test fun enforcesTotalLimit() {
        expectError(R.string.err_zip_too_large) {
            scan(zip("a" to "x".repeat(8), "b" to "y".repeat(8)), ZipLimits(maxTotalBytes = 12))
        }
    }

    @Test fun emptyZipIsRejected() {
        expectError(R.string.err_zip_empty) { scan(zip()) }
    }

    @Test fun skipRulesDoNotHitNormalFiles() {
        assertFalse(ZipScanner.shouldSkip("src/git/main.kt"))
        assertTrue(ZipScanner.shouldSkip("a/b/Thumbs.db"))
    }
}
