package com.patryksliwinski.quicknotes.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class NoteMeta(
    val uri: String,
    val name: String,
    val lastModified: Long,
    val title: String,
    val snippet: String,
    val size: Long = 0L,
    val content: String = "",
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

fun deriveTitle(name: String, @Suppress("UNUSED_PARAMETER") head: String): String =
    name.removeSuffix(".md")

fun deriveSnippet(name: String, head: String): String {
    val firstLine = head.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: return ""
    return firstLine.take(80)
}

fun parentDocumentUriOf(treeUri: Uri): Uri =
    DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

fun safLastModified(raw: Long, now: Long = System.currentTimeMillis()): Long =
    if (raw > 0L) raw else now

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
        healOrphanedNoteFiles(dir)
        return dir.listFiles()
            ?.filter { it.isFile && isNoteFileName(it.name) }
            ?.map { f ->
                NoteMeta(
                    uri = f.toURI().toString(),
                    name = f.name,
                    lastModified = f.lastModified(),
                    title = "",
                    snippet = "",
                    size = f.length(),
                    content = "",
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
            content = content,
        )
    }

    override suspend fun restoreNote(name: String, content: String): NoteMeta {
        val file = File(dir, name)
        file.writeText(content)
        val head = content.take(HEAD_BYTES)
        return NoteMeta(
            uri = file.toURI().toString(),
            name = file.name,
            lastModified = file.lastModified(),
            title = deriveTitle(file.name, head),
            snippet = deriveSnippet(file.name, head),
            size = file.length(),
            content = content,
        )
    }

    private fun toFile(uri: String): File = File(java.net.URI(uri).path ?: "")

    override suspend fun readNote(uri: String): String {
        return toFile(uri).readText()
    }

    override suspend fun writeNote(uri: String, content: String) {
        val file = toFile(uri)
        val tmp = File(file.parentFile, ".${file.name}.tmp")
        tmp.writeText(content)
        if (file.exists()) {
            val bak = File(file.parentFile, ".${file.name}.bak")
            if (bak.exists()) bak.delete()
            if (!file.renameTo(bak)) {
                tmp.delete()
                throw java.io.IOException("Could not stage note write for $uri")
            }
            try {
                if (!tmp.renameTo(file)) throw java.io.IOException("Could not commit note write for $uri")
            } finally {
                bak.delete()
            }
        } else {
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw java.io.IOException("Could not create note file for $uri")
            }
        }
    }

    override suspend fun deleteNote(uri: String) {
        toFile(uri).delete()
    }

    override fun displayName(): String = "App-private storage"
}

fun isNoteFileName(name: String): Boolean =
    name.endsWith(".md") && !name.startsWith(".")

fun orphanNameOf(noteName: String): String? = when {
    noteName.startsWith(".") && noteName.endsWith(".tmp") ->
        noteName.removePrefix(".").removeSuffix(".tmp").ifEmpty { null }
    noteName.startsWith(".") && noteName.endsWith(".bak") ->
        noteName.removePrefix(".").removeSuffix(".bak").ifEmpty { null }
    else -> null
}

fun healOrphanedNoteFiles(dir: File) {
    val files = dir.listFiles() ?: return
    for (f in files) {
        val orphan = orphanNameOf(f.name) ?: continue
        if (!orphan.endsWith(".md") || orphan.startsWith(".")) continue
        val target = File(dir, orphan)
        if (target.exists()) {
            f.delete()
        } else if (f.name.endsWith(".bak") && f.renameTo(target)) {
            continue
        } else {
            f.delete()
        }
    }
}

private val safMutationMutexes = ConcurrentHashMap<String, Mutex>()

internal fun mutationMutexFor(treeUri: String): Mutex =
    safMutationMutexes.computeIfAbsent(treeUri) { Mutex() }

class SafNoteStore(
    private val context: Context,
    private val treeUri: Uri,
) : NoteStore {

    private val resolver get() = context.contentResolver
    private val mutationMutex = mutationMutexFor(treeUri.toString())
    private val childrenUri: Uri
        get() = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
    private val parentDocumentUri: Uri
        get() = parentDocumentUriOf(treeUri)

    override suspend fun listNotes(): List<NoteMeta> = mutationMutex.withLock {
        listNotesLocked()
    }

    private suspend fun listNotesLocked(): List<NoteMeta> {
        healOrphanedSafDocuments()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val notes = mutableListOf<NoteMeta>()
        val cursor = resolver.query(
            childrenUri, projection, null, null,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED + " DESC"
        ) ?: throw java.io.IOException("Folder unavailable (volume unmounted or permission lost): $treeUri")
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val modIdx = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val sizeIdx = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                if (!isNoteFileName(name)) continue
                val docId = it.getString(idIdx)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                notes += NoteMeta(
                    uri = docUri.toString(),
                    name = name,
                    lastModified = safLastModified(it.getLong(modIdx)),
                    title = "",
                    snippet = "",
                    size = it.getLong(sizeIdx),
                    content = "",
                )
            }
        }
        return notes.sortedByDescending { it.lastModified }
    }

    private fun healOrphanedSafDocuments() {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val cursor = resolver.query(childrenUri, projection, null, null, null)
            ?: throw java.io.IOException("Could not list documents in $treeUri (volume unavailable)")
        val children = cursor.use {
            val idIdx = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val docs = mutableListOf<Pair<String, String>>()
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                docs += it.getString(idIdx) to name
            }
            docs
        }
        val names = children.map { it.second }.toSet()
        for ((docId, name) in children) {
            val orphan = orphanNameOf(name) ?: continue
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            if (names.contains(orphan)) {
                runCatching { DocumentsContract.deleteDocument(resolver, docUri) }
            } else if (name.endsWith(".bak")) {
                runCatching { DocumentsContract.renameDocument(resolver, docUri, orphan) }
            } else {
                runCatching { DocumentsContract.deleteDocument(resolver, docUri) }
            }
        }
    }

    override suspend fun readHead(uri: String, maxBytes: Int): String {
        val docUri = Uri.parse(uri)
        return openInputStreamOrThrow(docUri).use { it.readHead(maxBytes) }
    }

    override suspend fun createNote(): NoteMeta {
        return createNoteWithContent("")
    }

    override suspend fun createNoteWithContent(content: String): NoteMeta =
        mutationMutex.withLock { createWithName(newNoteFileName(), content) }

    override suspend fun restoreNote(name: String, content: String): NoteMeta =
        mutationMutex.withLock { createWithName(name, content) }

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
            content = content,
        )
    }

    override suspend fun readNote(uri: String): String {
        return openInputStreamOrThrow(Uri.parse(uri)).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun openOutputStreamOrThrow(docUri: Uri): java.io.OutputStream =
        resolver.openOutputStream(docUri, "w")
            ?: throw java.io.IOException("Note document not writable (volume unmounted or permission lost): $docUri")

    private fun openInputStreamOrThrow(docUri: Uri): InputStream =
        resolver.openInputStream(docUri)
            ?: throw java.io.IOException("Note document unavailable (volume unmounted or permission lost): $docUri")

    override suspend fun writeNote(uri: String, content: String) {
        mutationMutex.withLock {
            writeText(Uri.parse(uri), content)
        }
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
                openOutputStreamOrThrow(tmp).use { it.write(content.toByteArray(Charsets.UTF_8)) }
                val bak = runCatching {
                    DocumentsContract.renameDocument(resolver, uri, ".$targetName.bak")
                }.getOrNull()
                val renamed = runCatching {
                    DocumentsContract.renameDocument(resolver, tmp, targetName)
                }.getOrNull()
                if (renamed == null) {
                    if (bak != null) {
                        runCatching { DocumentsContract.renameDocument(resolver, bak, targetName) }
                    }
                    throw java.io.IOException("Could not commit note write for $uri")
                }
                if (bak != null) {
                    runCatching { DocumentsContract.deleteDocument(resolver, bak) }
                }
            } else {
                openOutputStreamOrThrow(uri).use { it.write(content.toByteArray(Charsets.UTF_8)) }
            }
        } finally {
            tmp?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        }
    }

    override suspend fun deleteNote(uri: String) {
        mutationMutex.withLock {
            DocumentsContract.deleteDocument(resolver, Uri.parse(uri))
        }
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
