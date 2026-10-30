package com.patryksliwinski.quicknotes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoStackTest {

    @Test
    fun `new stack cannot undo or redo`() {
        val stack = UndoStack()
        assertFalse(stack.canUndo)
        assertFalse(stack.canRedo)
        assertNull(stack.undo("current"))
        assertNull(stack.redo("current"))
    }

    @Test
    fun `push enables undo`() {
        val stack = UndoStack()
        stack.push("a")
        assertTrue(stack.canUndo)
        assertFalse(stack.canRedo)
    }

    @Test
    fun `undo returns last snapshot and enables redo`() {
        val stack = UndoStack()
        stack.push("a")
        stack.push("b")
        assertEquals("b", stack.undo("c"))
        assertTrue(stack.canRedo)
        assertEquals("a", stack.undo("b"))
        assertFalse(stack.canUndo)
        assertNull(stack.undo("a"))
    }

    @Test
    fun `redo replays future in order`() {
        val stack = UndoStack()
        stack.push("a")
        stack.push("b")
        assertEquals("b", stack.undo("c"))
        assertEquals("a", stack.undo("b"))
        assertEquals("b", stack.redo("a"))
        assertEquals("c", stack.redo("b"))
        assertFalse(stack.canRedo)
        assertNull(stack.redo("b"))
    }

    @Test
    fun `push clears redo future`() {
        val stack = UndoStack()
        stack.push("a")
        stack.undo("b")
        assertTrue(stack.canRedo)
        stack.push("c")
        assertFalse(stack.canRedo)
        assertNull(stack.redo("d"))
    }

    @Test
    fun `duplicate consecutive snapshot is ignored`() {
        val stack = UndoStack()
        stack.push("a")
        stack.push("a")
        assertEquals("a", stack.undo("b"))
        assertFalse(stack.canUndo)
    }

    @Test
    fun `stack is capped at max size`() {
        val stack = UndoStack(maxSize = 3)
        repeat(10) { stack.push("s$it") }
        var current = "top"
        val restored = mutableListOf<String>()
        while (true) {
            val prev = stack.undo(current) ?: break
            restored += prev
            current = prev
        }
        assertEquals(listOf("s9", "s8", "s7"), restored)
    }

    @Test
    fun `reset clears both directions`() {
        val stack = UndoStack()
        stack.push("a")
        stack.push("b")
        stack.undo("c")
        stack.reset()
        assertFalse(stack.canUndo)
        assertFalse(stack.canRedo)
        assertNull(stack.undo("x"))
        assertNull(stack.redo("x"))
    }

    @Test
    fun `undo redo round trip restores original content`() {
        val stack = UndoStack()
        stack.push("hello")
        var content = "hello world"
        content = stack.undo(content)!!
        assertEquals("hello", content)
        content = stack.redo(content)!!
        assertEquals("hello world", content)
    }
}
