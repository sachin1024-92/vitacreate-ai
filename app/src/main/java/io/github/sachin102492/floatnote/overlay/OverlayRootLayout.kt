package io.github.sachin102492.floatnote.overlay

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout

/** Root of the floating panel window: turns Back and taps outside the panel into callbacks. */
class OverlayRootLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var onBackPressed: (() -> Unit)? = null
    var onOutsideTouch: (() -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val callback = onBackPressed
        if (event.keyCode == KeyEvent.KEYCODE_BACK && callback != null) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) callback()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch?.invoke()
            return true
        }
        return super.dispatchTouchEvent(event)
    }
}
