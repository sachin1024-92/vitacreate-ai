package io.github.sachin102492.floatnote.ads

import android.app.Activity
import android.view.ViewGroup

/** The FOSS build never shows ads and contains no ad code. */
@Suppress("UNUSED_PARAMETER")
class Ads(activity: Activity, container: ViewGroup) {
    fun resume() = Unit
    fun pause() = Unit
    fun destroy() = Unit
}
