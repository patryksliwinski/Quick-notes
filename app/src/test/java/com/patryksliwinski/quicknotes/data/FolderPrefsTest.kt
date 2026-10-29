package com.patryksliwinski.quicknotes.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderPrefsTest {

    private val context get() = org.robolectric.RuntimeEnvironment.getApplication()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("quicknotes_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `loadFolderUri returns null when nothing saved`() {
        assertNull(FolderPrefs.loadFolderUri(context))
    }

    @Test
    fun `save then load roundtrips uri`() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        FolderPrefs.saveFolderUri(context, uri)
        assertEquals(uri, FolderPrefs.loadFolderUri(context))
    }

    @Test
    fun `hasPersistedPermission is false for uri without grant`() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        assertFalse(FolderPrefs.hasPersistedPermission(context, uri))
    }

    @Test
    fun `hasPersistedPermission is true after granting persistable permission`() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        assertTrue(FolderPrefs.hasPersistedPermission(context, uri))
    }

    @Test
    fun `takePermission returns true and persists write permission for a grantable uri`() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        assertTrue(FolderPrefs.takePermission(context, uri))
        assertTrue(FolderPrefs.hasPersistedPermission(context, uri))
    }

    @Test
    fun `takePermission result matches persisted permission state`() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/invalid")
        assertEquals(
            FolderPrefs.hasPersistedPermission(context, uri),
            FolderPrefs.takePermission(context, uri),
        )
    }

    @Test
    fun `resolveNoteStore uses saf store when persisted permission granted`() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        FolderPrefs.saveFolderUri(context, uri)
        val (store, revoked) = resolveNoteStore(context)
        assertTrue(store is SafNoteStore)
        assertFalse(revoked)
    }

    @Test
    fun `resolveNoteStore falls back to internal storage when no folder saved`() {
        val (store, revoked) = resolveNoteStore(context)
        assertTrue(store is InternalNoteStore)
        assertFalse(revoked)
    }

    @Test
    fun `resolveNoteStore reports revoked when saved folder has no persisted permission`() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        FolderPrefs.saveFolderUri(context, uri)
        val (store, revoked) = resolveNoteStore(context)
        assertTrue(store is InternalNoteStore)
        assertTrue(revoked)
    }
}
