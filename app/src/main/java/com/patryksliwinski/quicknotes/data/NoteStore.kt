package com.patryksliwinski.quicknotes.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class NoteMeta(
    val uri: String,
    val name: String,
    val lastModified: Long,
    val title: String,
    val snippet: String,
)

interface NoteStore {
    suspend fun listNotes(): List<NoteMeta>
    suspend fun createNote(): NoteMeta
    suspend fun restoreNote(name: String, content: String): NoteMeta
    suspend fun readNote(uri: String): String
    suspend fun writeNote(uri: String, content: String)
    suspend fun deleteNote(uri: String)
    fun displayName(): String
}

private val FILENAME_DATE = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

fun newNoteFileName(): String {
    val ts = FILENAME_DATE.format(Date())
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
    val body = lines.drop(1).firstOrNull { it != titleLine } ?: lines.firstOrNull() ?: ""
    return body.take(80)
}

private fun readHead(uri: Uri, resolver: android.content.ContentResolver, maxBytes: Int = 4096): String {
    return resolver.openInputStream(uri)?.use { input ->
        input.readNBytes(maxBytes).toString(Charsets.UTF_8)
    } ?: ""
}

class InternalNoteStore(private val dir: File) : NoteStore {

    init {
        dir.mkdirs()
    }

    override suspend fun listNotes(): List<NoteMeta> {
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".md") && !it.name.startsWith(".") }
            ?.map { f ->
                val head = f.inputStream().use { it.readNBytes(4096).toString(Charsets.UTF_8) }
                NoteMeta(
                    uri = f.toURI().toString(),
                    name = f.name,
                    lastModified = f.lastModified(),
                    title = deriveTitle(f.name, head),
                    snippet = deriveSnippet(f.name, head),
                )
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    override suspend fun createNote(): NoteMeta {
        val file = File(dir, newNoteFileName())
        file.writeText("")
        return NoteMeta(file.toURI().toString(), file.name, file.lastModified(), file.name.removeSuffix(".md"), "")
    }

    override suspend fun restoreNote(name: String, content: String): NoteMeta {
        val file = File(dir, name)
        file.writeText(content)
        return NoteMeta(file.toURI().toString(), file.name, file.lastModified(), file.name.removeSuffix(".md"), "")
    }

    private fun toFile(uri: String): File = File(Uri.parse(uri).path ?: "")

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
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                if (!name.endsWith(".md") || name.startsWith(".")) continue
                val docId = cursor.getString(idIdx)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val head = readHead(docUri, resolver)
                notes += NoteMeta(
                    uri = docUri.toString(),
                    name = name,
                    lastModified = cursor.getLong(modIdx),
                    title = deriveTitle(name, head),
                    snippet = deriveSnippet(name, head),
                )
            }
        }
        return notes
    }

    override suspend fun createNote(): NoteMeta {
        return createWithName(newNoteFileName(), "")
    }

    override suspend fun restoreNote(name: String, content: String): NoteMeta {
        return createWithName(name, content)
    }

    private suspend fun createWithName(name: String, content: String): NoteMeta {
        val uri = DocumentsContract.createDocument(resolver, treeUri, "text/markdown", name)
            ?: throw IllegalStateException("Could not create note in selected folder")
        if (content.isNotEmpty()) {
            writeText(uri, content)
        }
        val head = if (content.isNotEmpty()) content.take(4096) else ""
        return NoteMeta(
            uri = uri.toString(),
            name = name,
            lastModified = System.currentTimeMillis(),
            title = deriveTitle(name, head),
            snippet = deriveSnippet(name, head),
        )
    }

    override suspend fun readNote(uri: String): String {
        return resolver.openInputStream(Uri.parse(uri))?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: ""
    }

    override suspend fun writeNote(uri: String, content: String) {
        writeText(Uri.parse(uri), content)
    }

    private fun writeText(uri: Uri, content: String) {
        val tmpName = "." + uri.lastPathSegment + ".tmp"
        val tmp = DocumentsContract.createDocument(resolver, treeUri, "application/octet-stream", tmpName)
        try {
            if (tmp != null) {
                resolver.openOutputStream(tmp, "w")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                DocumentsContract.deleteDocument(resolver, uri)
                DocumentsContract.renameDocument(resolver, tmp, uri.lastPathSegment ?: tmpName)
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

    fun takePermission(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            // Permission could not be persisted; the folder still works for this session.
        }
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
