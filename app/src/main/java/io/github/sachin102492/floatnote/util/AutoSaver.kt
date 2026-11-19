package io.github.sachin102492.floatnote.util

import android.os.Handler
import android.os.Looper

/**
 * Debounces saves while the user types: every keystroke calls [poke], and [save] runs once
 * typing pauses for [delayMs]. [flush] saves immediately if anything is pending.
 */
class AutoSaver(private val delayMs: Long = DEFAULT_DELAY_MS, private val save: () -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private val task = Runnable {
        pending = false
        save()
    }

    var pending = false
        private set

    fun poke() {
        pending = true
        handler.removeCallbacks(task)
        handler.postDelayed(task, delayMs)
    }

    fun flush() {
        if (!pending) return
        handler.removeCallbacks(task)
        pending = false
        save()
    }

    fun cancel() {
        handler.removeCallbacks(task)
        pending = false
    }

    companion object {
        const val DEFAULT_DELAY_MS = 400L
    }
}
