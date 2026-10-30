package com.patryksliwinski.quicknotes.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class SafNoteStoreTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val treeUri =
        Uri.parse("content://com.android.externalstorage.documents/tree/XXXX-XXXX%3ANotes")

    private fun store(): SafNoteStore = SafNoteStore(context, treeUri)

    private fun install(provider: FakeSafProvider) {
        provider.attachInfoForTest(context)
        ShadowContentResolver.registerProviderInternal(
            "com.android.externalstorage.documents", provider
        )
    }

    @Test
    fun `listNotes throws when the children query returns a null cursor`() = runTest {
        install(FakeSafProvider(childrenRows = null))
        val exception = assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking { store().listNotes() }
        }
        assertTrue(exception.message!!.contains("Could not list documents"))
    }

    @Test
    fun `listNotes returns an empty list for a non-null cursor with zero rows`() = runTest {
        install(FakeSafProvider(childrenRows = emptyList()))
        assertEquals(emptyList<NoteMeta>(), store().listNotes())
    }

    @Test
    fun `listNotes maps rows skips dotfiles and sanitizes zero last-modified to now`() = runTest {
        val before = System.currentTimeMillis()
        val rows = listOf(
            arrayOf("XXXX-XXXX:Notes/a.md", "a.md", "0", "12"),
            arrayOf("XXXX-XXXX:Notes/b.md", "b.md", "1700000000000", "34"),
            arrayOf("XXXX-XXXX:Notes/.hidden.md", ".hidden.md", "1700000000000", "1"),
        )
        install(FakeSafProvider(childrenRows = rows))
        val notes = store().listNotes()
        val after = System.currentTimeMillis()
        assertEquals(2, notes.size)
        val a = notes.first { it.name == "a.md" }
        assertTrue(a.lastModified in before..after)
        assertEquals(
            "content://com.android.externalstorage.documents/tree/XXXX-XXXX%3ANotes" +
                "/document/XXXX-XXXX%3ANotes%2Fa.md",
            a.uri,
        )
        assertEquals(12L, a.size)
        val b = notes.first { it.name == "b.md" }
        assertEquals(1700000000000L, b.lastModified)
        assertEquals(34L, b.size)
    }

    @Test
    fun `readNote throws when the document stream cannot be opened`() = runTest {
        install(FakeSafProvider(openFileFails = true))
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "XXXX-XXXX:Notes/a.md")
        val exception = assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking { store().readNote(docUri.toString()) }
        }
        assertTrue(exception !is AssertionError)
    }

    @Test
    fun `readHead throws when the document stream cannot be opened`() = runTest {
        install(FakeSafProvider(openFileFails = true))
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "XXXX-XXXX:Notes/a.md")
        val exception = assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking { store().readHead(docUri.toString()) }
        }
        assertTrue(exception !is AssertionError)
    }

    @Test
    fun `readNote returns the stream content when the document is readable`() = runTest {
        install(FakeSafProvider())
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "XXXX-XXXX:Notes/a.md")
        shadowOf(context.contentResolver).registerInputStream(
            docUri, ByteArrayInputStream("# Hi\nbody".toByteArray())
        )
        assertEquals("# Hi\nbody", store().readNote(docUri.toString()))
    }

    @Test
    fun `writeText creates the tmp document under the tree document uri`() = runTest {
        val provider = FakeSafProvider(displayName = "a.md")
        install(provider)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "XXXX-XXXX:Notes/a.md")
        assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking { store().writeNote(docUri.toString(), "hello") }
        }
        val parentDocUri = parentDocumentUriOf(treeUri)
        assertEquals(listOf(parentDocUri to ".a.md.tmp"), provider.createCalls)
    }

    @Test
    fun `createNoteWithContent throws when the provider creates the document but the commit fails`() = runTest {
        val created = DocumentsContract.buildDocumentUriUsingTree(treeUri, "XXXX-XXXX:Notes/new.md")
        val provider = FakeSafProvider(displayName = "new.md", createResult = created, renameFails = true)
        install(provider)
        val exception = assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking { store().createNoteWithContent("hello") }
        }
        assertTrue(exception.message!!.contains("Could not commit note write"))
        assertEquals(2, provider.createCalls.size)
        assertTrue(provider.callMethods.contains("android:renameDocument"))
    }

    @Test
    fun `createNoteWithContent succeeds when the provider creates and renames documents`() = runTest {
        val created = DocumentsContract.buildDocumentUriUsingTree(treeUri, "XXXX-XXXX:Notes/new.md")
        val provider = FakeSafProvider(createResult = created)
        install(provider)
        val meta = store().createNoteWithContent("# Hello\nbody")
        assertEquals(created.toString(), meta.uri)
        assertTrue(meta.name.endsWith(".md"))
        assertEquals("Hello", meta.title)
        assertEquals("body", meta.snippet)
        assertEquals(2, provider.createCalls.size)
        assertTrue(provider.callMethods.contains("android:renameDocument"))
        assertTrue(provider.callMethods.contains("android:deleteDocument"))
    }

    @Test
    fun `restoreNote creates a document with the requested name`() = runTest {
        val created = DocumentsContract.buildDocumentUriUsingTree(treeUri, "XXXX-XXXX:Notes/restored.md")
        install(FakeSafProvider(createResult = created))
        val meta = store().restoreNote("restored.md", "restored content")
        assertEquals("restored.md", meta.name)
        assertEquals(created.toString(), meta.uri)
        assertEquals("restored content", meta.title)
    }

    @Test
    fun `readHead returns the stream content when the document is readable`() = runTest {
        install(FakeSafProvider())
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "XXXX-XXXX:Notes/a.md")
        shadowOf(context.contentResolver).registerInputStream(
            docUri, ByteArrayInputStream("# Head\nbody".toByteArray())
        )
        assertEquals("# Head\nbody", store().readHead(docUri.toString()))
    }

    @Test
    fun `listNotes heals orphan tmp and bak documents during the scan`() = runTest {
        val rows = listOf(
            arrayOf("XXXX-XXXX:Notes/a.md", "a.md", "1700000000000", "10"),
            arrayOf("XXXX-XXXX:Notes/.a.md.tmp", ".a.md.tmp", "1700000000000", "10"),
            arrayOf("XXXX-XXXX:Notes/.b.md.bak", ".b.md.bak", "1700000000000", "10"),
            arrayOf("XXXX-XXXX:Notes/c.md", "c.md", "1700000000000", "5"),
        )
        val provider = FakeSafProvider(childrenRows = rows)
        install(provider)
        val notes = store().listNotes()
        assertEquals(setOf("a.md", "c.md"), notes.map { it.name }.toSet())
        assertTrue(provider.callMethods.contains("android:deleteDocument"))
        assertTrue(provider.callMethods.contains("android:renameDocument"))
    }

    @Test
    fun `createNote throws when the provider returns no document uri`() = runTest {
        install(FakeSafProvider(createResult = null))
        assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking { store().createNote() }
        }
    }

    @Test
    fun `deleteNote issues the delete call for the document uri`() = runTest {
        val provider = FakeSafProvider()
        install(provider)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "XXXX-XXXX:Notes/a.md")
        store().deleteNote(docUri.toString())
        assertEquals(listOf("android:deleteDocument" to docUri), provider.deleteCalls)
    }

    @Test
    fun `displayName returns the queried tree display name`() = runTest {
        install(FakeSafProvider(displayName = "Notes"))
        assertEquals("Notes", store().displayName())
    }

    @Test
    fun `displayName falls back to the last path segment when the query yields no name`() = runTest {
        install(FakeSafProvider(displayName = null))
        assertEquals("XXXX-XXXX:Notes", store().displayName())
    }

    @Test
    fun `displayName falls back to the last path segment when the query throws`() = runTest {
        install(FakeSafProvider(queryFails = true))
        assertEquals("XXXX-XXXX:Notes", store().displayName())
    }

    @Test
    fun `safLastModified keeps positive values and replaces zero or negative with now`() {
        assertEquals(1700000000000L, safLastModified(1700000000000L, now = 42L))
        assertEquals(42L, safLastModified(0L, now = 42L))
        assertEquals(42L, safLastModified(-5L, now = 42L))
    }

    private inner class FakeSafProvider(
        var childrenRows: List<Array<String>>? = emptyList(),
        var displayName: String? = null,
        var openFileFails: Boolean = false,
        var createResult: Uri? = null,
        var queryFails: Boolean = false,
        var renameFails: Boolean = false,
    ) : ContentProvider() {
        val createCalls = mutableListOf<Pair<Uri, String>>()
        val callMethods = mutableListOf<String>()
        val deleteCalls = mutableListOf<Pair<String, Uri>>()

        override fun onCreate() = true

        fun attachInfoForTest(ctx: android.content.Context) {
            val info = android.content.pm.ProviderInfo().apply {
                authority = "com.android.externalstorage.documents"
                name = "FakeSafProvider"
            }
            val m = ContentProvider::class.java.getDeclaredMethod(
                "attachInfo", android.content.Context::class.java,
                android.content.pm.ProviderInfo::class.java, Boolean::class.javaPrimitiveType,
            )
            m.isAccessible = true
            m.invoke(this, ctx, info, true)
        }

        override fun call(method: String, arg: String?, extras: android.os.Bundle?): android.os.Bundle? {
            callMethods += method
            if (method == "android:createDocument") {
                val parent = extras?.getParcelable<Uri>("uri")
                val name = extras?.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (parent != null && name != null) createCalls += parent to name
                val result = createResult ?: return null
                return android.os.Bundle().apply {
                    putParcelable("uri", result)
                }
            }
            if (method == "android:deleteDocument") {
                val uri = extras?.getParcelable<Uri>("uri")
                if (uri != null) deleteCalls += method to uri
                return android.os.Bundle().apply {
                    putBoolean("result", true)
                }
            }
            if (method == "android:renameDocument") {
                if (renameFails) return null
                val uri = extras?.getParcelable<Uri>("uri")
                return android.os.Bundle().apply {
                    putParcelable("uri", uri)
                }
            }
            return null
        }

        override fun query(
            uri: Uri, projection: Array<String>?, selection: String?,
            selectionArgs: Array<String>?, sortOrder: String?,
        ): Cursor? {
            if (queryFails) throw java.io.IOException("volume unavailable")
            if (uri.toString().contains("/children")) {
                val rows = childrenRows ?: return null
                val all = listOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_SIZE,
                )
                val wanted = projection?.toList().orEmpty()
                val columns = if (wanted.isEmpty()) all.toTypedArray() else wanted.toTypedArray()
                val cursor = MatrixCursor(columns)
                for (row in rows) {
                    cursor.addRow(columns.map { col -> row[all.indexOf(col)] }.toTypedArray())
                }
                return cursor
            }
            val cursor = MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            if (displayName != null) cursor.addRow(arrayOf(displayName))
            return cursor
        }

        override fun getType(uri: Uri) = "vnd.android.document/directory"

        override fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor? {
            if (openFileFails) throw java.io.FileNotFoundException("volume unavailable")
            return super.openFile(uri, mode)
        }

        override fun openAssetFile(uri: Uri, mode: String): android.content.res.AssetFileDescriptor? {
            if (openFileFails) throw java.io.FileNotFoundException("volume unavailable")
            val file = java.io.File.createTempFile("saf", ".tmp")
            file.deleteOnExit()
            shadowOf(context!!.contentResolver).registerOutputStream(uri, java.io.FileOutputStream(file))
            val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_WRITE)
            return android.content.res.AssetFileDescriptor(pfd, 0, file.length())
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0

        override fun update(
            uri: Uri, values: ContentValues?, selection: String?,
            selectionArgs: Array<String>?,
        ) = 0
    }
}
