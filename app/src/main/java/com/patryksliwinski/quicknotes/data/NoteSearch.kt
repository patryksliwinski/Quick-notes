package com.patryksliwinski.quicknotes.data

import java.util.Locale

data class NoteSearchEntry(
    val meta: NoteMeta,
    val titleLower: String,
    val snippetLower: String,
    val nameLower: String,
    val contentLower: String,
)

fun List<NoteMeta>.toSearchEntries(): List<NoteSearchEntry> =
    map { NoteSearchEntry(it, it.title.lowercase(Locale.ROOT), it.snippet.lowercase(Locale.ROOT), it.name.lowercase(Locale.ROOT), it.content.lowercase(Locale.ROOT)) }

fun filterNotesPrecomputed(entries: List<NoteSearchEntry>, query: String): List<NoteMeta> {
    if (query.isBlank()) return entries.map { it.meta }
    val q = query.trim().lowercase(Locale.ROOT)
    return entries.filter {
        it.titleLower.contains(q) || it.snippetLower.contains(q) || it.nameLower.contains(q) || it.contentLower.contains(q)
    }.map { it.meta }
}
