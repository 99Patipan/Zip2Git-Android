package com.patipan.zip2git.domain

import com.patipan.zip2git.domain.FileChange.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DifferTest {
    private fun local(path: String, sha: String) = ZipFileEntry(path, File("unused"), 3, sha)
    private fun remote(path: String, sha: String, mode: String = "100644") = RemoteEntry(path, mode, sha)

    @Test fun classifiesAllKinds() {
        val changes = Differ.diff(
            mapOf("same" to local("same", "1"), "mod" to local("mod", "2"), "new" to local("new", "3")),
            mapOf("same" to remote("same", "1"), "mod" to remote("mod", "9", "100755"), "gone" to remote("gone", "4", "100755")),
        ).associateBy { it.path }
        assertEquals(Kind.UNCHANGED, changes.getValue("same").kind)
        assertEquals(Kind.MODIFY, changes.getValue("mod").kind)
        assertEquals(Kind.ADD, changes.getValue("new").kind)
        assertEquals(Kind.DELETE, changes.getValue("gone").kind)
    }

    @Test fun modifyKeepsExistingMode() {
        val c = Differ.diff(mapOf("run.sh" to local("run.sh", "2")), mapOf("run.sh" to remote("run.sh", "1", "100755"))).single()
        assertEquals("100755", c.mode)
    }

    @Test fun addUsesDefaultMode() {
        assertEquals("100644", Differ.diff(mapOf("n" to local("n", "1")), emptyMap()).single().mode)
    }

    @Test fun massDeleteDetection() {
        val remote = (1..10).associate { "f$it" to remote("f$it", "x") }
        val wipe = Differ.diff(emptyMap(), remote)
        assertTrue(Differ.massDeleteSuspected(wipe, remote.size))
        assertFalse(Differ.massDeleteSuspected(wipe.take(2), 100))
    }
}
