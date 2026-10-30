package com.patryksliwinski.quicknotes.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TextStats(val words: Int, val characters: Int)

fun computeStats(content: String): TextStats {
    val words = if (content.isBlank()) 0
    else content.split(Regex("\\s+")).count { it.isNotEmpty() }
    return TextStats(words = words, characters = content.length)
}

fun timestampLine(date: Date = Date()): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)

fun appendTimestampLine(content: String, line: String = timestampLine()): String {
    return if (content.isEmpty() || content.endsWith("\n")) "$content$line\n"
    else "$content\n$line\n"
}
