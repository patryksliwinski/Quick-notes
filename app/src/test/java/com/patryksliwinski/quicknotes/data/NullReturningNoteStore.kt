package com.patryksliwinski.quicknotes.data

class NullReturningNoteStore : NoteStore {
    override suspend fun listNotes(): List<NoteMeta> = emptyList()

    override suspend fun createNote(): NoteMeta =
        NoteMeta("content://null/doc1", "doc1.md", 0L, "doc1", "")

    override suspend fun createNoteWithContent(content: String): NoteMeta =
        NoteMeta("content://null/doc1", "doc1.md", 0L, "doc1", "")

    override suspend fun restoreNote(name: String, content: String): NoteMeta =
        NoteMeta("content://null/$name", name, 0L, name.removeSuffix(".md"), "")

    override suspend fun readNote(uri: String): String = ""

    override suspend fun readHead(uri: String, maxBytes: Int): String = ""

    override suspend fun writeNote(uri: String, content: String) {}

    override suspend fun deleteNote(uri: String) {}

    override fun displayName(): String = "Null-returning store"
}
