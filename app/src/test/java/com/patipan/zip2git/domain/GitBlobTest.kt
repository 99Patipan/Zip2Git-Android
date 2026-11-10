package com.patipan.zip2git.domain

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitBlobTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun emptyBlobMatchesGit() {
        assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", GitBlob.sha1(ByteArray(0)))
    }

    @Test fun helloBlobMatchesGit() {
        // echo "hello" | git hash-object --stdin
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", GitBlob.sha1("hello\n".toByteArray()))
    }

    @Test fun fileAndBytesAgree() {
        val f = tmp.newFile().apply { writeBytes("hello\n".toByteArray()) }
        assertEquals(GitBlob.sha1("hello\n".toByteArray()), GitBlob.sha1(f))
    }
}
