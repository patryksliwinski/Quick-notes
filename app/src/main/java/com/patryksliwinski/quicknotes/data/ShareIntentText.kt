package com.patryksliwinski.quicknotes.data

import android.content.Intent
import android.net.Uri

fun sharedTextFromIntent(intent: Intent): String? {
    if (intent.action != Intent.ACTION_SEND) return null
    intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { return it }
    @Suppress("DEPRECATION")
    val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
    if (stream.scheme != "file" && stream.scheme != "content") return null
    return stream.toString()
}
