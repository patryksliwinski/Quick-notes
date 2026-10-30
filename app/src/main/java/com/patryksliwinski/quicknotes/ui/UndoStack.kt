package com.patryksliwinski.quicknotes.ui

class UndoStack(private val maxSize: Int = 50) {

    private val history = ArrayDeque<String>()
    private val future = ArrayDeque<String>()

    val canUndo: Boolean get() = history.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun push(snapshot: String) {
        if (history.isNotEmpty() && history.last() == snapshot) return
        history.addLast(snapshot)
        while (history.size > maxSize) history.removeFirst()
        future.clear()
    }

    fun undo(current: String): String? {
        if (history.isEmpty()) return null
        future.addLast(current)
        return history.removeLast()
    }

    fun redo(current: String): String? {
        if (future.isEmpty()) return null
        history.addLast(current)
        return future.removeLast()
    }

    fun reset() {
        history.clear()
        future.clear()
    }
}
