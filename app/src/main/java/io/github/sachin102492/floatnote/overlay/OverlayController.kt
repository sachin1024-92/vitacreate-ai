package io.github.sachin102492.floatnote.overlay

import android.animation.ObjectAnimator
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import io.github.sachin102492.floatnote.R
import io.github.sachin102492.floatnote.data.Note
import io.github.sachin102492.floatnote.data.NoteRepository
import io.github.sachin102492.floatnote.notes
import io.github.sachin102492.floatnote.support.Perks
import io.github.sachin102492.floatnote.ui.EditorActivity
import io.github.sachin102492.floatnote.util.AutoSaver
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Owns the three overlay windows: the chat-head style bubble, the dismiss target shown while
 * dragging, and the notepad panel the bubble expands into.
 *
 * The bubble moves on spring physics, can be flung, snaps to the nearest screen edge, tucks
 * itself half off-screen when idle, and is magnetically pulled into the dismiss target.
 */
class OverlayController(
    private val service: Service,
    private val onDismissed: () -> Unit,
) : NoteRepository.Listener {

    private enum class Side { LEFT, RIGHT }

    private val windowContext: Context = createOverlayContext(service)
    private val ctx = ContextThemeWrapper(windowContext, R.style.Theme_FloatNote)
    private val wm = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val inflater = LayoutInflater.from(ctx)
    private val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private val handler = Handler(Looper.getMainLooper())
    private val repo = service.notes
    private val prefs = service.getSharedPreferences("bubble", Context.MODE_PRIVATE)
    private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val attached = HashSet<View>()

    // ---- Bubble -------------------------------------------------------------------------

    private val bubbleSize = dp(64)
    private val bubbleRoot: View = inflater.inflate(R.layout.overlay_bubble, null)
    private val bubbleIcon: View = bubbleRoot.findViewById(R.id.bubble_icon)
    private val bubbleParams = overlayParams(
        bubbleSize,
        bubbleSize,
        LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    )
    private var side = if (prefs.getString(KEY_SIDE, null) == Side.LEFT.name) Side.LEFT else Side.RIGHT
    private var dismissing = false

    private val paramX = object : FloatPropertyCompat<LayoutParams>("x") {
        override fun getValue(params: LayoutParams) = params.x.toFloat()
        override fun setValue(params: LayoutParams, value: Float) {
            params.x = value.roundToInt()
            updateWindow(bubbleRoot, params)
        }
    }
    private val paramY = object : FloatPropertyCompat<LayoutParams>("y") {
        override fun getValue(params: LayoutParams) = params.y.toFloat()
        override fun setValue(params: LayoutParams, value: Float) {
            params.y = value.roundToInt()
            updateWindow(bubbleRoot, params)
        }
    }
    private val springX = SpringAnimation(bubbleParams, paramX).setSpring(
        SpringForce().setDampingRatio(0.62f).setStiffness(380f),
    )
    private val springY = SpringAnimation(bubbleParams, paramY).setSpring(
        SpringForce().setDampingRatio(0.75f).setStiffness(380f),
    )
    private val tuckRunnable = Runnable { tuck() }

    // ---- Dismiss target -----------------------------------------------------------------

    private val dismissSize = dp(104)
    private val dismissRoot: View = inflater.inflate(R.layout.overlay_dismiss, null)
    private val dismissCircle: View = dismissRoot.findViewById(R.id.dismiss_circle)
    private val dismissParams = overlayParams(
        dismissSize,
        dismissSize,
        LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCHABLE or
            LayoutParams.FLAG_LAYOUT_IN_SCREEN or LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    )
    private var dismissShown = false
    private var magnetized = false

    // ---- Notepad panel ------------------------------------------------------------------

    private val panelRoot = inflater.inflate(R.layout.overlay_panel, null) as OverlayRootLayout
    private val panelCard: View = panelRoot.findViewById(R.id.panel_card)
    private val panelContent: View = panelRoot.findViewById(R.id.panel_content)
    private val panelHeader: View = panelRoot.findViewById(R.id.panel_header)
    private val headerIcons: List<ImageView> = listOf(
        R.id.header_icon, R.id.btn_prev, R.id.btn_next, R.id.btn_new,
        R.id.btn_copy, R.id.btn_open, R.id.btn_minimize,
    ).map { panelRoot.findViewById(it) }
    private val titleInput: EditText = panelRoot.findViewById(R.id.note_title)
    private val bodyInput: EditText = panelRoot.findViewById(R.id.note_body)
    private val indexLabel: TextView = panelRoot.findViewById(R.id.note_index)
    private val statusLabel: TextView = panelRoot.findViewById(R.id.save_status)
    private val statsLabel: TextView = panelRoot.findViewById(R.id.note_stats)
    private val prevButton: View = panelRoot.findViewById(R.id.btn_prev)
    private val nextButton: View = panelRoot.findViewById(R.id.btn_next)
    private val panelMargin = dp(10)
    private val panelParams = overlayParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.MATCH_PARENT,
        LayoutParams.FLAG_NOT_TOUCH_MODAL or LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    ).apply {
        softInputMode = LayoutParams.SOFT_INPUT_ADJUST_PAN
    }
    private var expanded = false
    private var noteId = NoteRepository.NO_ID
    private var binding = false
    private val autoSaver = AutoSaver { saveNow() }

    init {
        bubbleRoot.setOnTouchListener(BubbleTouchListener())
        bubbleRoot.setOnClickListener { expand() }

        panelCard.clipToOutline = true
        panelRoot.onBackPressed = { collapse() }
        panelRoot.onOutsideTouch = { collapse() }
        titleInput.doAfterTextChanged { onEdited() }
        bodyInput.doAfterTextChanged { onEdited() }
        prevButton.setOnClickListener { navigate(-1) }
        nextButton.setOnClickListener { navigate(1) }
        panelRoot.findViewById<View>(R.id.btn_new).setOnClickListener { newNote() }
        panelRoot.findViewById<View>(R.id.btn_copy).setOnClickListener { copyNote() }
        panelRoot.findViewById<View>(R.id.btn_open).setOnClickListener { openInApp() }
        panelRoot.findViewById<View>(R.id.btn_minimize).setOnClickListener { collapse() }
    }

    /** Paints the bubble and panel header in the user's chosen [BubbleColor]. */
    fun applyStyle() {
        val color = Perks.bubbleColor(service)
        bubbleIcon.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(color.start, color.end),
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(2), BUBBLE_RING)
        }
        (bubbleIcon as ImageView).setColorFilter(color.icon)
        panelHeader.setBackgroundColor(color.header)
        headerIcons.forEach { it.setColorFilter(color.icon) }
        indexLabel.setTextColor(color.icon)
    }

    fun show() {
        applyStyle()
        val screen = screenSize()
        bubbleParams.y = clampY((prefs.getFloat(KEY_Y, 0.3f) * screen.y).roundToInt())
        // Start just off the edge and spring in.
        bubbleParams.x = if (side == Side.LEFT) -bubbleSize else screen.x
        addWindow(bubbleRoot, bubbleParams)
        bubbleIcon.scaleX = 0.3f
        bubbleIcon.scaleY = 0.3f
        bubbleIcon.alpha = 0f
        bubbleIcon.animate().setStartDelay(0).scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(450).setInterpolator(OvershootInterpolator(2.2f)).start()
        springX.animateToFinalPosition(edgeX(side, tucked = false).toFloat())
        repo.addListener(this)
        scheduleTuck()
    }

    fun destroy() {
        autoSaver.flush()
        if (expanded) repo.deleteIfBlank(noteId)
        repo.removeListener(this)
        handler.removeCallbacksAndMessages(null)
        springX.cancel()
        springY.cancel()
        for (view in listOf(panelRoot, dismissRoot, bubbleRoot)) removeWindow(view)
    }

    fun onConfigurationChanged() {
        springX.cancel()
        springY.cancel()
        hideDismissTarget()
        bubbleParams.x = edgeX(side, tucked = false)
        bubbleParams.y = clampY(bubbleParams.y)
        updateWindow(bubbleRoot, bubbleParams)
        if (expanded) {
            layoutPanel()
            updateWindow(panelRoot, panelParams)
        } else {
            scheduleTuck()
        }
    }

    override fun onNotesChanged() {
        if (expanded) updateMeta()
    }

    // ---- Bubble motion ------------------------------------------------------------------

    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var tracker: VelocityTracker? = null

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (dismissing) return true
            if (expanded) {
                if (event.actionMasked == MotionEvent.ACTION_UP) collapse()
                return true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(tuckRunnable)
                    springX.cancel()
                    springY.cancel()
                    downX = event.rawX
                    downY = event.rawY
                    startX = bubbleParams.x
                    startY = bubbleParams.y
                    dragging = false
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain()
                    track(event)
                    bubbleIcon.animate().setStartDelay(0).scaleX(0.86f).scaleY(0.86f).alpha(1f)
                        .setDuration(120).setInterpolator(DecelerateInterpolator()).start()
                }

                MotionEvent.ACTION_MOVE -> {
                    track(event)
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && hypot(dx, dy) > touchSlop) {
                        dragging = true
                        showDismissTarget()
                    }
                    if (dragging) {
                        val x = startX + dx
                        val y = startY + dy
                        val near = isOverDismissTarget(x + bubbleSize / 2f, y + bubbleSize / 2f)
                        if (near && !magnetized) {
                            magnetize()
                        } else if (!near && magnetized) {
                            demagnetize()
                        }
                        if (!magnetized) {
                            bubbleParams.x = x.roundToInt()
                            bubbleParams.y = y.roundToInt()
                            updateWindow(bubbleRoot, bubbleParams)
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    track(event)
                    val velocity = tracker
                    velocity?.computeCurrentVelocity(1000, MAX_FLING_VELOCITY)
                    val vx = velocity?.xVelocity ?: 0f
                    val vy = velocity?.yVelocity ?: 0f
                    tracker?.recycle()
                    tracker = null
                    bubbleIcon.animate().setStartDelay(0).scaleX(1f).scaleY(1f)
                        .setDuration(260).setInterpolator(OvershootInterpolator(3f)).start()
                    when {
                        !dragging && event.actionMasked == MotionEvent.ACTION_UP -> view.performClick()
                        !dragging -> scheduleTuck()
                        magnetized -> dismiss()
                        else -> {
                            hideDismissTarget()
                            settle(vx, vy)
                        }
                    }
                }
            }
            return true
        }

        private fun track(event: MotionEvent) {
            // The window moves under the finger, so track screen coordinates.
            val copy = MotionEvent.obtain(event)
            copy.setLocation(event.rawX, event.rawY)
            tracker?.addMovement(copy)
            copy.recycle()
        }
    }

    private fun settle(vx: Float, vy: Float) {
        val screen = screenSize()
        val flingThreshold = dp(700)
        side = when {
            vx > flingThreshold -> Side.RIGHT
            vx < -flingThreshold -> Side.LEFT
            bubbleParams.x + bubbleSize / 2f > screen.x / 2f -> Side.RIGHT
            else -> Side.LEFT
        }
        val targetY = clampY((bubbleParams.y + vy * 0.12f).roundToInt())
        springX.setStartVelocity(vx)
        springX.animateToFinalPosition(edgeX(side, tucked = false).toFloat())
        springY.setStartVelocity(vy)
        springY.animateToFinalPosition(targetY.toFloat())
        prefs.edit()
            .putString(KEY_SIDE, side.name)
            .putFloat(KEY_Y, targetY / screen.y.toFloat())
            .apply()
        scheduleTuck()
    }

    private fun scheduleTuck() {
        handler.removeCallbacks(tuckRunnable)
        handler.postDelayed(tuckRunnable, TUCK_DELAY_MS)
    }

    /** Slides the idle bubble half off the edge and fades it so it stays out of the way. */
    private fun tuck() {
        if (expanded || dismissing || dismissShown) return
        springX.animateToFinalPosition(edgeX(side, tucked = true).toFloat())
        bubbleIcon.animate().setStartDelay(0).alpha(TUCKED_ALPHA).scaleX(0.92f).scaleY(0.92f)
            .setDuration(400).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun edgeX(side: Side, tucked: Boolean): Int {
        val width = screenSize().x
        val hidden = (bubbleSize * 0.5f).roundToInt()
        return when (side) {
            Side.LEFT -> if (tucked) -hidden else -dp(2)
            Side.RIGHT -> if (tucked) width - bubbleSize + hidden else width - bubbleSize + dp(2)
        }
    }

    private fun clampY(y: Int): Int {
        val top = dp(48)
        val bottom = max(top, screenSize().y - bubbleSize - dp(96))
        return y.coerceIn(top, bottom)
    }

    // ---- Dismiss target -----------------------------------------------------------------

    private fun showDismissTarget() {
        if (dismissShown) return
        dismissShown = true
        val screen = screenSize()
        dismissParams.x = (screen.x - dismissSize) / 2
        dismissParams.y = screen.y - dismissSize - dp(72)
        addWindow(dismissRoot, dismissParams)
        dismissCircle.alpha = 0f
        dismissCircle.translationY = dpf(56f)
        dismissCircle.scaleX = 0.6f
        dismissCircle.scaleY = 0.6f
        dismissCircle.animate().setStartDelay(0).alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(280).setInterpolator(OvershootInterpolator(1.6f)).start()
    }

    private fun hideDismissTarget(then: (() -> Unit)? = null) {
        if (!dismissShown) {
            then?.invoke()
            return
        }
        dismissShown = false
        magnetized = false
        dismissCircle.animate().setStartDelay(0).alpha(0f).translationY(dpf(56f))
            .scaleX(0.6f).scaleY(0.6f).setDuration(200).setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (!dismissShown) removeWindow(dismissRoot)
                then?.invoke()
            }
            .start()
    }

    private fun isOverDismissTarget(centerX: Float, centerY: Float): Boolean {
        if (!dismissShown) return false
        val targetX = dismissParams.x + dismissSize / 2f
        val targetY = dismissParams.y + dismissSize / 2f
        return hypot(centerX - targetX, centerY - targetY) < dp(110)
    }

    private fun magnetize() {
        magnetized = true
        springX.animateToFinalPosition(dismissParams.x + (dismissSize - bubbleSize) / 2f)
        springY.animateToFinalPosition(dismissParams.y + (dismissSize - bubbleSize) / 2f)
        dismissCircle.animate().setStartDelay(0).scaleX(1.25f).scaleY(1.25f)
            .setDuration(200).setInterpolator(OvershootInterpolator(3f)).start()
        haptic()
    }

    private fun demagnetize() {
        magnetized = false
        springX.cancel()
        springY.cancel()
        dismissCircle.animate().setStartDelay(0).scaleX(1f).scaleY(1f)
            .setDuration(160).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun dismiss() {
        dismissing = true
        haptic()
        bubbleIcon.animate().setStartDelay(0).scaleX(0f).scaleY(0f).alpha(0f)
            .setDuration(220).setInterpolator(AccelerateInterpolator())
            .withEndAction { hideDismissTarget { onDismissed() } }
            .start()
    }

    // ---- Notepad panel ------------------------------------------------------------------

    fun expand() {
        if (expanded || dismissing) return
        expanded = true
        handler.removeCallbacks(tuckRunnable)
        springX.cancel()
        springY.cancel()
        hideDismissTarget()

        setBubbleTouchable(false)
        applyStyle()
        bindNote(repo.activeOrNewest())
        layoutPanel()
        addWindow(panelRoot, panelParams)

        bubbleIcon.animate().setStartDelay(0).scaleX(0f).scaleY(0f).alpha(0f)
            .setDuration(160).setInterpolator(AccelerateInterpolator()).start()

        // Grow the panel out of the bubble.
        val cardWidth = panelParams.width - 2 * panelMargin
        val cardHeight = panelParams.height - 2 * panelMargin
        val bubbleCenterY = bubbleParams.y + bubbleSize / 2f
        panelCard.pivotX = if (side == Side.LEFT) 0f else cardWidth.toFloat()
        panelCard.pivotY =
            (bubbleCenterY - panelParams.y - panelMargin).coerceIn(0f, cardHeight.toFloat())
        panelCard.animate().cancel()
        panelCard.scaleX = 0.15f
        panelCard.scaleY = 0.15f
        panelCard.alpha = 0f
        panelCard.animate().setStartDelay(0).scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(380).setInterpolator(OvershootInterpolator(1.1f)).start()

        bodyInput.requestFocus()
        handler.postDelayed({
            if (expanded) imm.showSoftInput(bodyInput, InputMethodManager.SHOW_IMPLICIT)
        }, 250)
    }

    fun collapse() {
        if (!expanded) return
        expanded = false
        autoSaver.flush()
        repo.deleteIfBlank(noteId)
        imm.hideSoftInputFromWindow(panelRoot.windowToken, 0)

        panelCard.animate().setStartDelay(0).scaleX(0.15f).scaleY(0.15f).alpha(0f)
            .setDuration(220).setInterpolator(AccelerateInterpolator(1.5f))
            .withEndAction { if (!expanded) removeWindow(panelRoot) }
            .start()

        // Pop the bubble back to its edge.
        bubbleParams.x = edgeX(side, tucked = false)
        setBubbleTouchable(true)
        bubbleIcon.animate().setStartDelay(100).scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(380).setInterpolator(OvershootInterpolator(2.5f)).start()
        scheduleTuck()
    }

    /** The hidden bubble must not swallow taps meant for the app underneath the panel. */
    private fun setBubbleTouchable(touchable: Boolean) {
        bubbleParams.flags = if (touchable) {
            bubbleParams.flags and LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            bubbleParams.flags or LayoutParams.FLAG_NOT_TOUCHABLE
        }
        updateWindow(bubbleRoot, bubbleParams)
    }

    private fun layoutPanel() {
        val screen = screenSize()
        val width = min(screen.x - 2 * dp(6), dp(420))
        val height = min((screen.y * 0.5f).roundToInt(), dp(480))
        panelParams.width = width
        panelParams.height = height
        panelParams.x = if (side == Side.LEFT) dp(6) else screen.x - width - dp(6)
        // Keep the panel in the upper half so the keyboard does not cover it.
        val top = dp(36)
        val maxY = max(top, (screen.y * 0.55f).roundToInt() - height)
        panelParams.y = (bubbleParams.y - dp(24)).coerceIn(top, maxY)
    }

    private fun bindNote(note: Note, direction: Int = 0) {
        noteId = note.id
        repo.activeNoteId = note.id
        binding = true
        titleInput.setText(note.title)
        bodyInput.setText(note.body)
        binding = false
        bodyInput.setSelection(bodyInput.length())
        statusLabel.text = if (note.isBlank) "" else ctx.getString(R.string.status_saved)
        updateMeta()
        updateStats()
        if (direction != 0) {
            panelContent.translationX = dpf(40f) * direction
            panelContent.alpha = 0f
            panelContent.animate().setStartDelay(0).translationX(0f).alpha(1f)
                .setDuration(260).setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    private fun onEdited() {
        if (binding) return
        statusLabel.setText(R.string.status_typing)
        statusLabel.alpha = 0.6f
        updateStats()
        autoSaver.poke()
    }

    private fun saveNow() {
        repo.save(noteId, titleInput.text.toString(), bodyInput.text.toString())
        statusLabel.setText(R.string.status_saved)
        statusLabel.animate().setStartDelay(0).alpha(1f).setDuration(250)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun navigate(delta: Int) {
        autoSaver.flush()
        val list = repo.byCreation
        val index = list.indexOfFirst { it.id == noteId }
        val target = list.getOrNull(index + delta)
        if (index < 0 || target == null) {
            nudge(delta)
            return
        }
        repo.deleteIfBlank(noteId)
        bindNote(target, direction = delta)
    }

    private fun newNote() {
        autoSaver.flush()
        if (repo[noteId]?.isBlank != false && titleInput.text.isBlank() && bodyInput.text.isBlank()) {
            nudge(-1)
            bodyInput.requestFocus()
            return
        }
        bindNote(repo.create(), direction = -1)
        bodyInput.requestFocus()
    }

    private fun copyNote() {
        autoSaver.flush()
        val text = repo[noteId]?.asPlainText().orEmpty()
        if (text.isEmpty()) return
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(ctx.getString(R.string.app_name), text))
        // Android 13+ shows its own clipboard confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(service, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInApp() {
        autoSaver.flush()
        val id = noteId
        collapse()
        val intent = EditorActivity.intent(service, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            service.startActivity(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not open the editor", e)
        }
    }

    private fun updateMeta() {
        val list = repo.byCreation
        val index = list.indexOfFirst { it.id == noteId }
        indexLabel.text = if (index < 0) {
            ctx.getString(R.string.note_index_new)
        } else {
            ctx.getString(R.string.note_index, index + 1, list.size)
        }
        prevButton.alpha = if (index > 0) 1f else DISABLED_ALPHA
        nextButton.alpha = if (index >= 0 && index < list.size - 1) 1f else DISABLED_ALPHA
    }

    private fun updateStats() {
        val text = bodyInput.text
        val words = text.split(WHITESPACE).count { it.isNotEmpty() }
        statsLabel.text = ctx.resources.getQuantityString(R.plurals.word_count, words, words)
    }

    /** A little rubber-band shake when there is nowhere further to go. */
    private fun nudge(direction: Int) {
        val d = dpf(10f) * -direction
        ObjectAnimator.ofFloat(panelContent, View.TRANSLATION_X, 0f, d, -d * 0.6f, d * 0.3f, 0f)
            .setDuration(320)
            .start()
    }

    // ---- Window helpers -----------------------------------------------------------------

    private fun overlayParams(width: Int, height: Int, flags: Int) = LayoutParams(
        width,
        height,
        LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun addWindow(view: View, params: LayoutParams) {
        if (!attached.add(view)) {
            updateWindow(view, params)
            return
        }
        try {
            wm.addView(view, params)
        } catch (e: RuntimeException) {
            attached.remove(view)
            Log.w(TAG, "Could not add overlay window", e)
        }
    }

    private fun updateWindow(view: View, params: LayoutParams) {
        if (view !in attached) return
        try {
            wm.updateViewLayout(view, params)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not update overlay window", e)
        }
    }

    private fun removeWindow(view: View) {
        if (!attached.remove(view)) return
        try {
            wm.removeViewImmediate(view)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not remove overlay window", e)
        }
    }

    private fun screenSize(): Point {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            return Point(bounds.width(), bounds.height())
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return Point(metrics.widthPixels, metrics.heightPixels)
    }

    private fun haptic() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        bubbleRoot.performHapticFeedback(constant)
    }

    private fun dp(value: Int): Int = (value * ctx.resources.displayMetrics.density).roundToInt()

    private fun dpf(value: Float): Float = value * ctx.resources.displayMetrics.density

    private companion object {
        /** On Android 11+ overlays should be created from a window context for the display. */
        fun createOverlayContext(service: Service): Context {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return service
            val displays = service.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = displays.getDisplay(Display.DEFAULT_DISPLAY) ?: return service
            return service.createDisplayContext(display)
                .createWindowContext(LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        }


        const val TAG = "FloatNoteOverlay"
        const val KEY_SIDE = "side"
        const val KEY_Y = "y_fraction"
        const val TUCK_DELAY_MS = 2500L
        const val TUCKED_ALPHA = 0.55f
        const val DISABLED_ALPHA = 0.35f
        const val MAX_FLING_VELOCITY = 8000f
        val WHITESPACE = Regex("\\s+")
        const val BUBBLE_RING = 0xE6FFFFFF.toInt()
    }
}
