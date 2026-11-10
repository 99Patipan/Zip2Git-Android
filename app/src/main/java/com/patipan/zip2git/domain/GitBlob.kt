package com.patipan.zip2git.domain

import java.io.File
import java.security.MessageDigest

/** Git blob object id: SHA-1("blob <size>\u0000" + content). */
object GitBlob {
    fun sha1(file: File, size: Long = file.length()): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob $size\u0000".toByteArray(Charsets.US_ASCII))
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().toHex()
    }

    fun sha1(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${bytes.size}\u0000".toByteArray(Charsets.US_ASCII))
        md.update(bytes)
        return md.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
