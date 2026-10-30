package com.patryksliwinski.quicknotes.ui

import android.content.Intent
import com.patryksliwinski.quicknotes.data.NoteMeta
import com.patryksliwinski.quicknotes.data.sharedTextFromIntent

sealed interface IncomingNoteRequest {
    data class NewNote(val sharedText: String? = null) : IncomingNoteRequest
    data class OpenNote(val note: NoteMeta) : IncomingNoteRequest
}

fun incomingNoteRequestFromIntent(intent: Intent?): IncomingNoteRequest? = when (intent?.action) {
    MainActivity.ACTION_NEW_NOTE -> IncomingNoteRequest.NewNote()
    MainActivity.ACTION_OPEN_NOTE -> {
        val uri = intent.getStringExtra(MainActivity.EXTRA_NOTE_URI) ?: return null
        IncomingNoteRequest.OpenNote(noteMetaFromUri(uri))
    }
    Intent.ACTION_SEND -> {
        val shared = sharedTextFromIntent(intent) ?: return null
        IncomingNoteRequest.NewNote(sharedText = shared)
    }
    else -> null
}

fun noteMetaFromUri(uri: String): NoteMeta {
    val name = uri.substringAfterLast('/')
    return NoteMeta(
        uri = uri,
        name = name,
        lastModified = 0L,
        title = name.removeSuffix(".md"),
        snippet = "",
        content = "",
    )
}
