package com.patipan.zip2git.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import com.patipan.zip2git.R
import com.patipan.zip2git.domain.AppException
import com.patipan.zip2git.domain.FolderChild
import com.patipan.zip2git.domain.FolderScanner
import com.patipan.zip2git.domain.FolderWalker
import com.patipan.zip2git.domain.ScanResult
import com.patipan.zip2git.domain.ZipScanner
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Android glue: imports a picked ZIP file or folder into the cache directory. Blocking, use on Dispatchers.IO. */
object SourceImporter {
    private fun workDir(context: Context) = File(context.cacheDir, "source_import")

    fun importZip(context: Context, uri: Uri): ScanResult {
        val input = context.contentResolver.openInputStream(uri) ?: throw AppException(R.string.err_open_zip)
        return input.use { ZipScanner.scan(it, workDir(context)) }
    }

    fun importFolder(context: Context, treeUri: Uri): ScanResult =
        FolderScanner.scan(DocumentTreeWalker(context.contentResolver, treeUri), workDir(context))

    fun zipName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "archive.zip"

    fun folderName(context: Context, treeUri: Uri): String {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        return runCatching {
            context.contentResolver.query(docUri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: rootId.substringAfterLast('/').substringAfterLast(':')
    }

    fun clear(context: Context) {
        workDir(context).deleteRecursively()
    }
}

/** Walks a folder granted through ACTION_OPEN_DOCUMENT_TREE using DocumentsContract directly (no extra dependency). */
private class DocumentTreeWalker(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : FolderWalker {
    override val rootId: String = DocumentsContract.getTreeDocumentId(treeUri)

    override fun children(dirId: String): List<FolderChild> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val out = ArrayList<FolderChild>()
        val cursor = resolver.query(uri, projection, null, null, null) ?: throw IOException("Cannot list folder")
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                out += FolderChild(id, name, c.getString(2) == Document.MIME_TYPE_DIR)
            }
        }
        return out
    }

    override fun open(id: String): InputStream =
        resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
            ?: throw IOException("Cannot open file")
}
