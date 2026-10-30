package com.patryksliwinski.quicknotes.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class NoteSortOrder {
    LAST_MODIFIED,
    TITLE,
    FILENAME_DATE,
}

sealed interface NoteListRow {
    data class Header(val dayKey: String) : NoteListRow
    data class Note(val meta: NoteMeta) : NoteListRow
}

const val DAY_KEY_TODAY = "today"
const val DAY_KEY_YESTERDAY = "yesterday"

fun sortNotes(notes: List<NoteMeta>, order: NoteSortOrder): List<NoteMeta> = when (order) {
    NoteSortOrder.LAST_MODIFIED -> notes.sortedByDescending { it.lastModified }
    NoteSortOrder.TITLE -> notes.sortedBy { it.title.lowercase(Locale.ROOT) }
    NoteSortOrder.FILENAME_DATE -> notes.sortedByDescending { it.name }
}

fun filterNotes(notes: List<NoteMeta>, query: String): List<NoteMeta> {
    if (query.isBlank()) return notes
    val q = query.trim().lowercase(Locale.ROOT)
    return notes.filter { note ->
        note.title.lowercase(Locale.ROOT).contains(q) ||
            note.snippet.lowercase(Locale.ROOT).contains(q) ||
            note.name.lowercase(Locale.ROOT).contains(q) ||
            note.content.lowercase(Locale.ROOT).contains(q)
    }
}

fun dayKeyOf(lastModified: Long, now: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val today = cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)
    val noteCal = Calendar.getInstance().apply { timeInMillis = lastModified }
    val noteDay = noteCal.get(Calendar.YEAR) * 10000 + (noteCal.get(Calendar.MONTH) + 1) * 100 + noteCal.get(Calendar.DAY_OF_MONTH)
    return when (today - noteDay) {
        0 -> DAY_KEY_TODAY
        1 -> DAY_KEY_YESTERDAY
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(lastModified))
    }
}

fun groupByDay(notes: List<NoteMeta>, now: Long = System.currentTimeMillis()): List<NoteListRow> {
    if (notes.isEmpty()) return emptyList()
    val byDay = LinkedHashMap<String, MutableList<NoteMeta>>()
    for (note in notes) {
        byDay.getOrPut(dayKeyOf(note.lastModified, now)) { mutableListOf() }.add(note)
    }
    val rows = mutableListOf<NoteListRow>()
    for ((dayKey, dayNotes) in byDay) {
        rows += NoteListRow.Header(dayKey)
        rows += dayNotes.map { NoteListRow.Note(it) }
    }
    return rows
}
