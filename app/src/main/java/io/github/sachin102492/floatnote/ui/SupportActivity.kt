package io.github.sachin102492.floatnote.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import io.github.sachin102492.floatnote.R
import io.github.sachin102492.floatnote.overlay.FloatingService
import io.github.sachin102492.floatnote.support.BubbleColor
import io.github.sachin102492.floatnote.support.Perks
import io.github.sachin102492.floatnote.support.SupportBackend
import io.github.sachin102492.floatnote.support.SupportBackends
import io.github.sachin102492.floatnote.support.SupportTier
import io.github.sachin102492.floatnote.util.padForSystemBars
import kotlin.math.roundToInt

/** Optional tips. The app stays fully usable for free; supporters get bubble colours. */
class SupportActivity : AppCompatActivity(), SupportBackend.Listener {

    private lateinit var root: View
    private lateinit var backend: SupportBackend
    private lateinit var status: TextView
    private lateinit var tiersContainer: LinearLayout
    private lateinit var tiersHint: TextView
    private lateinit var colorsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support)
        root = findViewById(R.id.root)
        root.padForSystemBars()
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        status = findViewById(R.id.supporter_status)
        tiersContainer = findViewById(R.id.tiers_container)
        tiersHint = findViewById(R.id.tiers_hint)
        colorsContainer = findViewById(R.id.colors_container)

        backend = SupportBackends.create(this)
        val donateUrl = backend.donateUrl
        val donate = findViewById<MaterialButton>(R.id.donate_button)
        if (donateUrl != null) {
            donate.visibility = View.VISIBLE
            tiersHint.visibility = View.GONE
            donate.setOnClickListener { openUrl(donateUrl) }
        }

        renderSupporter(Perks.isSupporter(this))
        backend.start(this)
    }

    override fun onDestroy() {
        backend.end()
        super.onDestroy()
    }

    override fun onTiers(tiers: List<SupportTier>) {
        tiersContainer.removeAllViews()
        for (tier in tiers) {
            val button = MaterialButton(this).apply {
                text = getString(R.string.support_tier, tier.title, tier.price)
                setOnClickListener { backend.buy(this@SupportActivity, tier.id) }
            }
            tiersContainer.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) },
            )
        }
        if (backend.donateUrl == null) {
            tiersHint.setText(R.string.support_store_only)
            tiersHint.visibility = if (tiers.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onSupporterChanged(isSupporter: Boolean) = renderSupporter(isSupporter)

    override fun onMessage(message: Int) {
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun renderSupporter(isSupporter: Boolean) {
        status.setText(if (isSupporter) R.string.support_is_supporter else R.string.support_not_yet)
        renderColors()
    }

    private fun renderColors() {
        colorsContainer.removeAllViews()
        val selected = Perks.bubbleColor(this)
        val ring = MaterialColors.getColor(colorsContainer, com.google.android.material.R.attr.colorPrimary)
        for (color in BubbleColor.entries) {
            val unlocked = Perks.isUnlocked(this, color)
            val swatch = FrameLayout(this).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(color.start, color.end),
                ).apply {
                    shape = GradientDrawable.OVAL
                    if (color == selected) setStroke(dp(4), ring)
                }
                alpha = if (unlocked) 1f else 0.45f
                contentDescription = color.name.lowercase()
                setOnClickListener { pick(color, unlocked) }
            }
            if (!unlocked) {
                swatch.addView(
                    ImageView(this).apply {
                        setImageResource(R.drawable.ic_lock)
                        setColorFilter(color.icon)
                    },
                    FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER),
                )
            }
            colorsContainer.addView(
                swatch,
                LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12) },
            )
        }
    }

    private fun pick(color: BubbleColor, unlocked: Boolean) {
        if (!unlocked) {
            onMessage(R.string.support_locked)
            return
        }
        Perks.setBubbleColor(this, color)
        FloatingService.refreshStyle(this)
        renderColors()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            onMessage(R.string.support_no_browser)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
