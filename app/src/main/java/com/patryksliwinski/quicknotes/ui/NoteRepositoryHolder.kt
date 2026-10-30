package com.patryksliwinski.quicknotes.ui

import android.app.Application
import com.patryksliwinski.quicknotes.data.NoteRepository

object NoteRepositoryHolder {

    @Volatile
    private var application: Application? = null

    @Volatile
    private var instance: NoteRepository? = null

    fun get(application: Application): NoteRepository {
        if (this.application === application) {
            instance?.let { return it }
        }
        synchronized(this) {
            if (this.application !== application) {
                this.application = application
                instance = NoteRepository(application)
            }
            return instance!!
        }
    }
}
