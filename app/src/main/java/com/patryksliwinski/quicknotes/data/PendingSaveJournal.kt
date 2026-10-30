package com.patryksliwinski.quicknotes.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

data class PendingSave(val uri: String, val content: String)

class PendingSaveJournal(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    fun record(uri: String, content: String) {
        prefs.edit().putString(uri, JSONObject().put(KEY_CONTENT, content).toString()).commit()
    }

    fun clear(uri: String) {
        prefs.edit().remove(uri).apply()
    }

    fun pending(): List<PendingSave> {
        return prefs.all.mapNotNull { (uri, value) ->
            val content = try {
                (value as? String)?.let { JSONObject(it).optString(KEY_CONTENT) }
            } catch (e: Exception) {
                null
            }
            if (content != null) PendingSave(uri, content) else null
        }
    }

    private companion object {
        const val PREFS_NAME = "quicknotes_pending_saves"
        const val KEY_CONTENT = "content"
    }
}
