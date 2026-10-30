package com.patryksliwinski.quicknotes.data

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareIntentTextTest {

    private fun sendIntent(action: String? = Intent.ACTION_SEND, type: String? = "text/plain"): Intent {
        return Intent(action).apply { this.type = type }
    }

    @Suppress("DEPRECATION")
    private fun Intent.putStreamExtra(uri: Uri): Intent = apply {
        putExtra(Intent.EXTRA_STREAM, uri)
    }

    @Test
    fun `extracts EXTRA_TEXT from a plain text send`() {
        val intent = sendIntent().apply {
            putExtra(Intent.EXTRA_TEXT, "shared from browser")
        }
        assertEquals("shared from browser", sharedTextFromIntent(intent))
    }

    @Test
    fun `returns null when action is not ACTION_SEND`() {
        val intent = sendIntent(action = Intent.ACTION_VIEW).apply {
            putExtra(Intent.EXTRA_TEXT, "not a share")
        }
        assertNull(sharedTextFromIntent(intent))
    }

    @Test
    fun `returns null when EXTRA_TEXT is missing`() {
        assertNull(sharedTextFromIntent(sendIntent()))
    }

    @Test
    fun `returns null when EXTRA_TEXT is blank`() {
        val intent = sendIntent().apply { putExtra(Intent.EXTRA_TEXT, "   ") }
        assertNull(sharedTextFromIntent(intent))
    }

    @Test
    fun `falls back to EXTRA_STREAM file uri when EXTRA_TEXT missing`() {
        val intent = sendIntent().putStreamExtra(Uri.parse("file:///tmp/shared.txt"))
        assertEquals("file:///tmp/shared.txt", sharedTextFromIntent(intent))
    }

    @Test
    fun `falls back to EXTRA_STREAM content uri`() {
        val intent = sendIntent().putStreamExtra(Uri.parse("content://media/external/file/42"))
        assertEquals("content://media/external/file/42", sharedTextFromIntent(intent))
    }

    @Test
    fun `returns null for unsupported EXTRA_STREAM scheme`() {
        val intent = sendIntent().putStreamExtra(Uri.parse("https://example.com/x.txt"))
        assertNull(sharedTextFromIntent(intent))
    }

    @Test
    fun `EXTRA_TEXT wins over EXTRA_STREAM`() {
        val intent = sendIntent().apply {
            putExtra(Intent.EXTRA_TEXT, "text wins")
        }.putStreamExtra(Uri.parse("file:///tmp/other.txt"))
        assertEquals("text wins", sharedTextFromIntent(intent))
    }
}
