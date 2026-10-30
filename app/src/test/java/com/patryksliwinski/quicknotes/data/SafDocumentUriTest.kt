package com.patryksliwinski.quicknotes.data

import android.net.Uri
import android.provider.DocumentsContract
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SafDocumentUriTest {

    @Test
    fun `parentDocumentUriOf converts tree uri to document uri with same document id`() {
        val treeUri = Uri.parse("content://com.android.externalstorage.documents/tree/XXXX-XXXX%3ANotes")
        val parent = parentDocumentUriOf(treeUri)
        assertEquals(
            "content://com.android.externalstorage.documents/tree/XXXX-XXXX%3ANotes/document/XXXX-XXXX%3ANotes",
            parent.toString()
        )
        assertEquals(DocumentsContract.getTreeDocumentId(treeUri), DocumentsContract.getDocumentId(parent))
    }

    @Test
    fun `parentDocumentUriOf works for primary storage tree`() {
        val treeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        val parent = parentDocumentUriOf(treeUri)
        assertEquals("primary:Documents", DocumentsContract.getDocumentId(parent))
        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3ADocuments/document/primary%3ADocuments",
            parent.toString()
        )
    }
}
