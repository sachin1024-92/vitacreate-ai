package io.github.sachin102492.floatnote.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

/** Pads an edge-to-edge root view so content clears the system bars and the keyboard. */
fun View.padForSystemBars() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, ime.bottom))
        WindowInsetsCompat.CONSUMED
    }
}
