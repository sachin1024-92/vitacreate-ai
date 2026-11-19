package io.github.sachin102492.floatnote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.sachin102492.floatnote.R
import io.github.sachin102492.floatnote.data.Note
import io.github.sachin102492.floatnote.data.NoteRepository
import io.github.sachin102492.floatnote.notes
import io.github.sachin102492.floatnote.overlay.FloatingService
import io.github.sachin102492.floatnote.util.AutoSaver
import io.github.sachin102492.floatnote.util.padForSystemBars

/** Full-screen editor. Every keystroke is autosaved; there is no save button. */
class EditorActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var titleInput: EditText
    private lateinit var bodyInput: EditText
    private var noteId = NoteRepository.NO_ID
    private var binding = false
    private val autoSaver = AutoSaver { save() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        findViewById<android.view.View>(R.id.root).padForSystemBars()

        toolbar = findViewById(R.id.toolbar)
        titleInput = findViewById(R.id.editor_title)
        bodyInput = findViewById(R.id.editor_body)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.menu_editor)
        toolbar.setOnMenuItemClickListener(::onMenuItem)

        val requested = savedInstanceState?.getLong(EXTRA_NOTE_ID, NoteRepository.NO_ID)
            ?: intent.getLongExtra(EXTRA_NOTE_ID, NoteRepository.NO_ID)
        val note = notes[requested] ?: notes.create()
        noteId = note.id
        notes.activeNoteId = note.id
        bind(note)

        titleInput.doAfterTextChanged { onEdited() }
        bodyInput.doAfterTextChanged { onEdited() }

        if (note.isBlank) {
            bodyInput.requestFocus()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up edits made in the floating notepad while this screen was in the background.
        val note = notes[noteId] ?: return
        if (!autoSaver.pending &&
            (note.title != titleInput.text.toString() || note.body != bodyInput.text.toString())
        ) {
            bind(note)
        }
    }

    override fun onPause() {
        autoSaver.flush()
        if (isFinishing) notes.deleteIfBlank(noteId)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(EXTRA_NOTE_ID, noteId)
    }

    private fun bind(note: Note) {
        binding = true
        titleInput.setText(note.title)
        bodyInput.setText(note.body)
        binding = false
        bodyInput.setSelection(bodyInput.length())
        toolbar.subtitle = if (note.isBlank) null else getString(R.string.status_saved)
    }

    private fun onEdited() {
        if (binding) return
        toolbar.setSubtitle(R.string.status_typing)
        autoSaver.poke()
    }

    private fun save() {
        notes.save(noteId, titleInput.text.toString(), bodyInput.text.toString())
        toolbar.setSubtitle(R.string.status_saved)
    }

    private fun onMenuItem(item: MenuItem): Boolean {
        autoSaver.flush()
        val note = notes[noteId]
        when (item.itemId) {
            R.id.action_float -> floatNote()
            R.id.action_copy -> if (note != null && !note.isBlank) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.app_name), note.asPlainText()),
                )
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
                }
            }
            R.id.action_share -> if (note != null && !note.isBlank) {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, note.displayTitle)
                    .putExtra(Intent.EXTRA_TEXT, note.asPlainText())
                startActivity(Intent.createChooser(send, getString(R.string.action_share)))
            }
            R.id.action_delete -> confirmDelete()
            else -> return false
        }
        return true
    }

    /** Pops this note out into the floating notepad, like picture-in-picture for text. */
    private fun floatNote() {
        notes.activeNoteId = noteId
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_START_BUBBLE, true),
            )
            return
        }
        FloatingService.start(this, expand = true)
        moveTaskToBack(true)
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                autoSaver.cancel()
                notes.delete(noteId)
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private const val EXTRA_NOTE_ID = "note_id"

        fun intent(context: Context, noteId: Long): Intent =
            Intent(context, EditorActivity::class.java).putExtra(EXTRA_NOTE_ID, noteId)
    }
}
