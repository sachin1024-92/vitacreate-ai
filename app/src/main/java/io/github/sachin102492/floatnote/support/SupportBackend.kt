package io.github.sachin102492.floatnote.support

import android.app.Activity
import androidx.annotation.StringRes

/** A one-time "supporter" tip, as priced by the store. */
data class SupportTier(val id: String, val title: String, val price: String)

/**
 * How a build takes support payments. The Play build uses Google Play Billing; the FOSS build
 * has no store and links to an external donation page instead.
 */
interface SupportBackend {

    interface Listener {
        fun onTiers(tiers: List<SupportTier>)
        fun onSupporterChanged(isSupporter: Boolean)
        fun onMessage(@StringRes message: Int)
    }

    /** External donation page, or null where in-app links to payments are not allowed. */
    val donateUrl: String?

    fun start(listener: Listener)
    fun buy(activity: Activity, tierId: String)
    fun end()
}
