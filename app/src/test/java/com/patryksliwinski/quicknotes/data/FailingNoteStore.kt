package com.patryksliwinski.quicknotes.data

class FailingNoteStore(
    private val failCreate: Boolean = true,
    private val failList: Boolean = false,
) : NoteStore {
    override suspend fun listNotes(): List<NoteMeta> {
        if (failList) throw IllegalStateException("folder unavailable")
        return emptyList()
    }

    override suspend fun createNote(): NoteMeta {
        if (failCreate) throw IllegalStateException("cannot create document")
        return NoteMeta("content://failing/doc1", "doc1.md", 0L, "doc1", "")
    }

    override suspend fun createNoteWithContent(content: String): NoteMeta {
        if (failCreate) throw IllegalStateException("cannot create document")
        return NoteMeta("content://failing/doc1", "doc1.md", 0L, "doc1", "")
    }

    override suspend fun restoreNote(name: String, content: String): NoteMeta =
        throw IllegalStateException("cannot create document")

    override suspend fun readNote(uri: String): String =
        throw IllegalStateException("cannot read")

    override suspend fun readHead(uri: String, maxBytes: Int): String =
        throw IllegalStateException("cannot read")

    override suspend fun writeNote(uri: String, content: String) {
        throw IllegalStateException("cannot write")
    }

    override suspend fun deleteNote(uri: String) {
        throw IllegalStateException("cannot delete")
    }

    override fun displayName(): String = "Failing store"
}
