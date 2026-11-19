package io.github.sachin102492.floatnote

import android.app.Application
import android.content.Context
import io.github.sachin102492.floatnote.data.NoteRepository

class FloatNoteApp : Application() {
    val repository: NoteRepository by lazy { NoteRepository(this) }
}

/** The single, app-wide note store. Must be used from the main thread. */
val Context.notes: NoteRepository
    get() = (applicationContext as FloatNoteApp).repository
