package io.github.sachin102492.floatnote.ads

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import io.github.sachin102492.floatnote.BuildConfig
import io.github.sachin102492.floatnote.support.Perks

/**
 * One banner on the notes list of the Play build.
 *
 * - Supporters never see it, and the ad SDK is not even started for them.
 * - Consent is requested first (Google's User Messaging Platform), as required in the EEA/UK.
 * - It is never shown in the floating bubble or the editor, only on this screen.
 */
class Ads(private val activity: Activity, private val container: ViewGroup) {

    private var adView: AdView? = null
    private var started = false

    fun resume() {
        if (Perks.isSupporter(activity)) {
            remove()
            return
        }
        if (!started) {
            started = true
            requestConsentThenLoad()
        }
        adView?.resume()
    }

    fun pause() {
        adView?.pause()
    }

    fun destroy() = remove()

    private fun requestConsentThenLoad() {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (consent.canRequestAds()) load()
                }
            },
            {
                // Consent service unreachable: fall back to whatever was decided last time.
                if (consent.canRequestAds()) load()
            },
        )
    }

    private fun load() {
        if (adView != null || activity.isFinishing || activity.isDestroyed) return
        if (Perks.isSupporter(activity)) return
        MobileAds.initialize(activity) {}
        val view = AdView(activity).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(AdSize.BANNER)
        }
        container.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.visibility = View.VISIBLE
        adView = view
        view.loadAd(AdRequest.Builder().build())
    }

    private fun remove() {
        adView?.destroy()
        adView = null
        container.removeAllViews()
        container.visibility = View.GONE
    }
}
