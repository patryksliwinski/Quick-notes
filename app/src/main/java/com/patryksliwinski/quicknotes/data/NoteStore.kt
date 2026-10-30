package com.patryksliwinski.quicknotes.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

data class NoteMeta(
    val uri: String,
    val name: String,
    val lastModified: Long,
    val title: String,
    val snippet: String,
    val size: Long = 0L,
)

interface NoteStore {
    suspend fun listNotes(): List<NoteMeta>
    suspend fun createNote(): NoteMeta
    suspend fun createNoteWithContent(content: String): NoteMeta
    suspend fun restoreNote(name: String, content: String): NoteMeta
    suspend fun readNote(uri: String): String
    suspend fun readHead(uri: String, maxBytes: Int = HEAD_BYTES): String
    suspend fun writeNote(uri: String, content: String)
    suspend fun deleteNote(uri: String)
    fun displayName(): String
}

const val HEAD_BYTES = 4096

data class HeadKey(val uri: String, val lastModified: Long, val size: Long)

data class HeadFields(val title: String, val snippet: String)

fun deriveHead(name: String, head: String): HeadFields =
    HeadFields(title = deriveTitle(name, head), snippet = deriveSnippet(name, head))

private val FILENAME_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US).withZone(ZoneId.systemDefault())

fun newNoteFileName(): String {
    val ts = FILENAME_DATE.format(Instant.now())
    val suffix = Random.nextInt(36 * 36 * 36 * 36)
        .toString(36).padStart(4, '0')
    return "$ts-$suffix.md"
}

fun deriveTitle(name: String, head: String): String {
    val firstLine = head.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: return name.removeSuffix(".md")
    val stripped = firstLine.trimStart('#').trim()
    return if (stripped.isNotEmpty()) stripped else name.removeSuffix(".md")
}

fun deriveSnippet(name: String, head: String): String {
    val lines = head.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val titleLine = lines.firstOrNull()?.trimStart('#')?.trim()
    val body = lines.drop(1).firstOrNull { it != titleLine } ?: titleLine ?: ""
    return body.take(80)
}

fun parentDocumentUriOf(treeUri: Uri): Uri =
    DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

private fun InputStream.readHead(maxBytes: Int): String {
    val buf = ByteArray(maxBytes)
    var read = 0
    while (read < maxBytes) {
        val n = this.read(buf, read, maxBytes - read)
        if (n < 0) break
        read += n
    }
    return if (read > 0) String(buf, 0, read, Charsets.UTF_8) else ""
}


class InternalNoteStore(private val dir: File) : NoteStore {

    init {
        dir.mkdirs()
    }

    override suspend fun listNotes(): List<NoteMeta> {
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".md") && !it.name.startsWith(".") }
            ?.map { f ->
                NoteMeta(
                    uri = f.toURI().toString(),
                    name = f.name,
                    lastModified = f.lastModified(),
                    title = "",
                    snippet = "",
                    size = f.length(),
                )
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    override suspend fun readHead(uri: String, maxBytes: Int): String {
        return toFile(uri).inputStream().use { it.readHead(maxBytes) }
    }

    override suspend fun createNote(): NoteMeta {
        return createNoteWithContent("")
    }

    override suspend fun createNoteWithContent(content: String): NoteMeta {
        val file = File(dir, newNoteFileName())
        file.writeText(content)
        val head = content.take(HEAD_BYTES)
        return NoteMeta(
            uri = file.toURI().toString(),
            name = file.name,
            lastModified = file.lastModified(),
            title = deriveTitle(file.name, head),
            snippet = deriveSnippet(file.name, head),
            size = file.length(),
        )
    }

    override suspend fun restoreNote(name: String, content: String): NoteMeta {
        val file = File(dir, name)
        file.writeText(content)
        return NoteMeta(
            uri = file.toURI().toString(),
            name = file.name,
            lastModified = file.lastModified(),
            title = file.name.removeSuffix(".md"),
            snippet = "",
            size = file.length(),
        )
    }

    private fun toFile(uri: String): File = File(java.net.URI(uri).path ?: "")

    override suspend fun readNote(uri: String): String {
        return toFile(uri).readText()
    }

    override suspend fun writeNote(uri: String, content: String) {
        toFile(uri).writeText(content)
    }

    override suspend fun deleteNote(uri: String) {
        toFile(uri).delete()
    }

    override fun displayName(): String = "App-private storage"
}

class SafNoteStore(
    private val context: Context,
    private val treeUri: Uri,
) : NoteStore {

    private val resolver get() = context.contentResolver
    private val childrenUri: Uri
        get() = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
    private val parentDocumentUri: Uri
        get() = parentDocumentUriOf(treeUri)

    override suspend fun listNotes(): List<NoteMeta> {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val notes = mutableListOf<NoteMeta>()
        resolver.query(
            childrenUri, projection, null, null,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED + " DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val modIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                if (!name.endsWith(".md") || name.startsWith(".")) continue
                val docId = cursor.getString(idIdx)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                notes += NoteMeta(
                    uri = docUri.toString(),
                    name = name,
                    lastModified = cursor.getLong(modIdx),
                    title = "",
                    snippet = "",
                    size = cursor.getLong(sizeIdx),
                )
            }
        }
        return notes
    }

    override suspend fun readHead(uri: String, maxBytes: Int): String {
        val docUri = Uri.parse(uri)
        return resolver.openInputStream(docUri)?.use { it.readHead(maxBytes) } ?: ""
    }

    override suspend fun createNote(): NoteMeta {
        return createNoteWithContent("")
    }

    override suspend fun createNoteWithContent(content: String): NoteMeta {
        return createWithName(newNoteFileName(), content)
    }

    override suspend fun restoreNote(name: String, content: String): NoteMeta {
        return createWithName(name, content)
    }

    private suspend fun createWithName(name: String, content: String): NoteMeta {
        val uri = DocumentsContract.createDocument(resolver, parentDocumentUri, "text/markdown", name)
            ?: throw IllegalStateException("Could not create note in selected folder")
        if (content.isNotEmpty()) {
            writeText(uri, content)
        }
        val head = if (content.isNotEmpty()) content.take(HEAD_BYTES) else ""
        return NoteMeta(
            uri = uri.toString(),
            name = name,
            lastModified = System.currentTimeMillis(),
            title = deriveTitle(name, head),
            snippet = deriveSnippet(name, head),
            size = content.toByteArray(Charsets.UTF_8).size.toLong(),
        )
    }

    override suspend fun readNote(uri: String): String {
        return resolver.openInputStream(Uri.parse(uri))?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: ""
    }

    override suspend fun writeNote(uri: String, content: String) {
        writeText(Uri.parse(uri), content)
    }

    private fun documentDisplayName(uri: Uri): String? {
        return resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    private fun writeText(uri: Uri, content: String) {
        val targetName = documentDisplayName(uri)
            ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: uri.lastPathSegment
            ?: "note.md"
        val tmpName = ".$targetName.tmp"
        val tmp = DocumentsContract.createDocument(resolver, parentDocumentUri, "application/octet-stream", tmpName)
        try {
            if (tmp != null) {
                resolver.openOutputStream(tmp, "w")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                DocumentsContract.deleteDocument(resolver, uri)
                DocumentsContract.renameDocument(resolver, tmp, targetName)
            } else {
                resolver.openOutputStream(uri, "w")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            }
        } finally {
            tmp?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        }
    }

    override suspend fun deleteNote(uri: String) {
        DocumentsContract.deleteDocument(resolver, Uri.parse(uri))
    }

    override fun displayName(): String {
        return try {
            val name = resolver.query(
                treeUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            name ?: treeUri.lastPathSegment ?: "Selected folder"
        } catch (e: Exception) {
            treeUri.lastPathSegment ?: "Selected folder"
        }
    }
}

object FolderPrefs {
    private const val PREFS = "quicknotes_prefs"
    private const val KEY_FOLDER_URI = "folder_uri"

    fun saveFolderUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
    }

    fun loadFolderUri(context: Context): Uri? {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_URI, null) ?: return null
        return try {
            Uri.parse(stored)
        } catch (e: Exception) {
            null
        }
    }

    fun hasPersistedPermission(context: Context, uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    }

    fun takePermission(context: Context, uri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}

object ListPrefs {
    private const val PREFS = "quicknotes_prefs"
    private const val KEY_SORT_ORDER = "sort_order"
    private const val KEY_CONFIRM_DELETE = "confirm_delete"

    fun saveSortOrder(context: Context, order: NoteSortOrder) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SORT_ORDER, order.name).apply()
    }

    fun loadSortOrder(context: Context): NoteSortOrder {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SORT_ORDER, null) ?: return NoteSortOrder.LAST_MODIFIED
        return try {
            NoteSortOrder.valueOf(stored)
        } catch (e: Exception) {
            NoteSortOrder.LAST_MODIFIED
        }
    }

    fun saveConfirmDelete(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CONFIRM_DELETE, enabled).apply()
    }

    fun loadConfirmDelete(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONFIRM_DELETE, false)
    }
}

fun resolveNoteStore(context: Context): Pair<NoteStore, Boolean> {
    val uri = FolderPrefs.loadFolderUri(context)
    if (uri != null && FolderPrefs.hasPersistedPermission(context, uri)) {
        return SafNoteStore(context, uri) to false
    }
    val internal = InternalNoteStore(File(context.filesDir, "notes"))
    return internal to (uri != null)
}
