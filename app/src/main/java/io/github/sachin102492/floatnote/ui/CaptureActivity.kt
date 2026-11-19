package io.github.sachin102492.floatnote.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import io.github.sachin102492.floatnote.R
import io.github.sachin102492.floatnote.notes

/**
 * Saves text handed over by another app as a new note, then closes straight away so the
 * user stays where they were. Handles the Share sheet (ACTION_SEND) and the text-selection
 * menu (ACTION_PROCESS_TEXT). Only plain text is read; nothing else from the intent is used.
 */
class CaptureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (text, title) = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() to ""
            Intent.ACTION_SEND ->
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() to
                    intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
            else -> null to ""
        }
        val saved = text?.take(MAX_CHARS)?.let { notes.createFrom(it, title.take(MAX_TITLE)) }
        Toast.makeText(
            this,
            if (saved != null) R.string.capture_saved else R.string.capture_empty,
            Toast.LENGTH_SHORT,
        ).show()
        finish()
    }

    private companion object {
        // Keeps a single shared blob from bloating the notes file.
        const val MAX_CHARS = 100_000
        const val MAX_TITLE = 200
    }
}
