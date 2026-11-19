package io.github.sachin102492.floatnote.support

import android.app.Activity
import android.content.Context
import io.github.sachin102492.floatnote.BuildConfig

object SupportBackends {
    fun create(context: Context): SupportBackend = DonationBackend
}

/** No store in the FOSS build: perks are free and support goes through a donation page. */
private object DonationBackend : SupportBackend {
    override val donateUrl: String? = BuildConfig.DONATE_URL.ifEmpty { null }

    override fun start(listener: SupportBackend.Listener) {
        listener.onTiers(emptyList())
        listener.onSupporterChanged(true)
    }

    override fun buy(activity: Activity, tierId: String) = Unit

    override fun end() = Unit
}
