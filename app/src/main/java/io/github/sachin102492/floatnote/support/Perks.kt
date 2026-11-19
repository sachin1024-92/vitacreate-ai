package io.github.sachin102492.floatnote.support

import android.content.Context
import io.github.sachin102492.floatnote.BuildConfig

/** Bubble colour themes. Amber is free; the rest are a thank-you for supporters. */
enum class BubbleColor(
    val start: Int,
    val end: Int,
    val header: Int,
    val icon: Int,
    val free: Boolean = false,
) {
    AMBER(0xFFFFDC73.toInt(), 0xFFFFA800.toInt(), 0xFFFFC940.toInt(), INK, free = true),
    MINT(0xFFA8F0D1.toInt(), 0xFF2BB673.toInt(), 0xFF7FDDB4.toInt(), INK),
    CORAL(0xFFFFB4A2.toInt(), 0xFFF2545B.toInt(), 0xFFFF8A80.toInt(), INK),
    SKY(0xFFB3E5FC.toInt(), 0xFF2F80ED.toInt(), 0xFF81C4F8.toInt(), INK),
    GRAPE(0xFFD7B8FF.toInt(), 0xFF7B3FE4.toInt(), 0xFFB794F6.toInt(), INK),
    NIGHT(0xFF5B5873.toInt(), 0xFF1F1B2E.toInt(), 0xFF3A3650.toInt(), WHITE),
}

private const val INK = 0xFF1F1B2E.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

object Perks {
    private const val PREFS = "perks"
    private const val KEY_SUPPORTER = "supporter"
    private const val KEY_COLOR = "bubble_color"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSupporter(context: Context): Boolean =
        BuildConfig.PERKS_FREE || prefs(context).getBoolean(KEY_SUPPORTER, false)

    fun setSupporter(context: Context, supporter: Boolean) {
        prefs(context).edit().putBoolean(KEY_SUPPORTER, supporter).apply()
    }

    fun isUnlocked(context: Context, color: BubbleColor): Boolean =
        color.free || isSupporter(context)

    /** The chosen colour, falling back to amber if it is no longer unlocked (e.g. a refund). */
    fun bubbleColor(context: Context): BubbleColor {
        val saved = prefs(context).getString(KEY_COLOR, null)
        val color = BubbleColor.entries.firstOrNull { it.name == saved } ?: BubbleColor.AMBER
        return if (isUnlocked(context, color)) color else BubbleColor.AMBER
    }

    fun setBubbleColor(context: Context, color: BubbleColor) {
        prefs(context).edit().putString(KEY_COLOR, color.name).apply()
    }
}
