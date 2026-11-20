package com.otokit.noharayh.platform

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import com.otokit.noharayh.R
import java.io.Closeable
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** 成绩和日志 ZIP 共用的 Android 目录授权与交付，不解释导出内容。 */
internal class DocumentZipExport(context: Context) : Closeable {
    private val registry = (context as ActivityResultRegistryOwner).activityResultRegistry
    private val resolver = context.contentResolver
    private val directory = DocumentsContract.buildDocumentUri(STORAGE, DOCUMENTS_ID)
    private var picker: ActivityResultLauncher<Uri?>? = null

    fun export(directoryName: String, label: String,
               write: (destination: ((OutputStream) -> Unit) -> Unit, done: (Boolean) -> Unit) -> Unit,
               written: (Uri) -> Unit, stopped: (Int?) -> Unit) {
        fun writeTo(tree: Uri) {
            var file: Uri? = null
            write({ writeZip ->
                val root = DocumentsContract.buildDocumentUriUsingTree(tree, DOCUMENTS_ID)
                val folder = resolver.childDirectory(resolver.childDirectory(root, "OTOKiT"), directoryName)
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
                val document = checkNotNull(DocumentsContract.createDocument(resolver, folder, ZIP, "OTOKiT_${label}_$stamp.zip"))
                try {
                    checkNotNull(resolver.openOutputStream(document, "w")).use(writeZip)
                    file = document
                } finally {
                    if (file == null) DocumentsContract.deleteDocument(resolver, document)
                }
            }) { success ->
                val result = file
                if (success && result != null) written(result) else stopped(R.string.settings_archive_export_error)
            }
        }
        val granted = resolver.persistedUriPermissions.firstOrNull {
            it.isReadPermission && it.isWritePermission && isDocuments(it.uri)
        }
        if (granted != null) { writeTo(granted.uri); return }
        picker = registry.register("document-zip-export-" + UUID.randomUUID(), ActivityResultContracts.OpenDocumentTree()) { tree ->
            close()
            when {
                tree == null -> stopped(null)
                !isDocuments(tree) -> stopped(R.string.settings_archive_choose_documents)
                else -> try {
                    resolver.takePersistableUriPermission(tree, READ_WRITE)
                    writeTo(tree)
                } catch (_: SecurityException) { stopped(R.string.settings_archive_export_error) }
            }
        }
        try { picker?.launch(directory) }
        catch (_: ActivityNotFoundException) { close(); stopped(R.string.settings_archive_export_error) }
    }

    override fun close() { picker?.unregister(); picker = null }

    private fun ContentResolver.childDirectory(parent: Uri, name: String): Uri {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
        query(children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE),
            null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name && cursor.getString(2) == Document.MIME_TYPE_DIR)
                    return DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(0))
            }
        }
        return checkNotNull(DocumentsContract.createDocument(this, parent, Document.MIME_TYPE_DIR, name))
    }

    private fun isDocuments(uri: Uri) = uri.authority == STORAGE && DocumentsContract.isTreeUri(uri) &&
        DocumentsContract.getTreeDocumentId(uri) == DOCUMENTS_ID

    companion object {
        private const val STORAGE = "com.android.externalstorage.documents"
        private const val DOCUMENTS_ID = "primary:Documents"
        private const val ZIP = "application/zip"
        private const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        fun open(context: Context, file: Uri, share: Boolean) {
            val intent = if (share) Intent(Intent.ACTION_SEND).setType(ZIP).putExtra(Intent.EXTRA_STREAM, file)
                else Intent(Intent.ACTION_VIEW).setDataAndType(file, ZIP)
            intent.clipData = ClipData.newRawUri("OTOKiT", file)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, null))
        }
    }
}
