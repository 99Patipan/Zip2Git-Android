package com.patipan.zip2git.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.patipan.zip2git.R
import com.patipan.zip2git.domain.AppException
import com.patipan.zip2git.domain.ScanResult
import com.patipan.zip2git.domain.ZipScanner
import java.io.File

/** Android glue for [ZipScanner]: opens the picked Uri and manages the cache directory. */
object ZipImporter {
    private fun workDir(context: Context) = File(context.cacheDir, "zip_import")

    /** Blocking: call from Dispatchers.IO. */
    fun import(context: Context, uri: Uri): ScanResult {
        val input = context.contentResolver.openInputStream(uri) ?: throw AppException(R.string.err_open_zip)
        return input.use { ZipScanner.scan(it, workDir(context)) }
    }

    fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "archive.zip"

    fun clear(context: Context) {
        workDir(context).deleteRecursively()
    }
}
