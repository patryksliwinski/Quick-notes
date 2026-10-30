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
    fun `safLastModified keeps positive values and replaces zero or negative with now`() {
        assertEquals(1700000000000L, safLastModified(1700000000000L, now = 42L))
        assertEquals(42L, safLastModified(0L, now = 42L))
        assertEquals(42L, safLastModified(-5L, now = 42L))
    }

    private class FakeSafProvider(
        var childrenRows: List<Array<String>>? = emptyList(),
        var displayName: String? = null,
        var openFileFails: Boolean = false,
    ) : ContentProvider() {
        val createCalls = mutableListOf<Pair<Uri, String>>()

        override fun onCreate() = true

        init {
            val info = android.content.pm.ProviderInfo().apply {
                authority = "com.android.externalstorage.documents"
                name = "FakeSafProvider"
            }
            val m = ContentProvider::class.java.getDeclaredMethod(
                "attachInfo", android.content.Context::class.java,
                android.content.pm.ProviderInfo::class.java, Boolean::class.javaPrimitiveType,
            )
            m.isAccessible = true
            m.invoke(this, RuntimeEnvironment.getApplication(), info, true)
        }

        override fun call(method: String, arg: String?, extras: android.os.Bundle?): android.os.Bundle? {
            if (method == "android:createDocument") {
                val parent = extras?.getParcelable<Uri>("uri")
                val name = extras?.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (parent != null && name != null) createCalls += parent to name
                return null
            }
            return super.call(method, arg, extras)
        }

        override fun query(
            uri: Uri, projection: Array<String>?, selection: String?,
            selectionArgs: Array<String>?, sortOrder: String?,
        ): Cursor? {
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

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0

        override fun update(
            uri: Uri, values: ContentValues?, selection: String?,
            selectionArgs: Array<String>?,
        ) = 0
    }
}
