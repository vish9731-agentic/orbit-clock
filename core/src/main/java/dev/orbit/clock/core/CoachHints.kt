package dev.orbit.clock.core

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/** Local, contextual education. Learning/dismissal wins over the three-session display cap. */
object CoachHints {
    private val sessions = mutableSetOf<String>()
    private fun prefs(c: Context) = c.getSharedPreferences("coach_hints", Context.MODE_PRIVATE)
    fun eligible(c: Context, key: String): Boolean {
        val p = prefs(c)
        return !p.getBoolean("$key.learned", false) && !p.getBoolean("$key.dismissed", false) &&
            (key in sessions || p.getInt("$key.shown", 0) < 3)
    }
    fun learn(c: Context, key: String) { prefs(c).edit().putBoolean("$key.learned", true).apply() }
    fun dismiss(c: Context, key: String) { prefs(c).edit().putBoolean("$key.dismissed", true).apply() }
    private fun shown(c: Context, key: String) {
        if (sessions.add(key)) { val p = prefs(c); p.edit().putInt("$key.shown", p.getInt("$key.shown", 0) + 1).apply() }
    }
    fun row(c: Context, key: String, message: String, colors: OrbitPalette): View? {
        if (!eligible(c, key)) return null
        val dp = c.resources.displayMetrics.density
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((12*dp).toInt(), (4*dp).toInt(), 0, (4*dp).toInt())
            background = GradientDrawable().apply { setColor(colors.container); cornerRadius = 16*dp }
        }
        row.addView(TextView(c).apply {
            text = message; textSize = 13f; setTextColor(colors.muted); setLineSpacing(2*dp, 1f)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(MaterialButton(c, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
            setIconResource(R.drawable.ic_close); iconTint = android.content.res.ColorStateList.valueOf(colors.muted)
            text = ""; iconPadding = 0; contentDescription = "Dismiss hint"; tooltipText = "Hide tip"
            setOnClickListener { OrbitMotion.tick(this); dismiss(c, key) }; OrbitMotion.bind(this)
        }, LinearLayout.LayoutParams((48*dp).toInt(), (48*dp).toInt()))
        observe(row, c, key)
        return row
    }
    fun caption(c: Context, key: String, message: String, color: Int): View? {
        if (!eligible(c, key)) return null
        return TextView(c).apply {
            text = message; textSize = 12f; gravity = Gravity.CENTER; setTextColor(color)
            contentDescription = message; setLineSpacing(2 * resources.displayMetrics.density, 1f)
            observe(this, c, key)
        }
    }
    private fun observe(view: View, c: Context, key: String) {
        val p = prefs(c)
        var collapse: ValueAnimator? = null
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (!eligible(c, key) && view.visibility == View.VISIBLE && collapse == null) {
                if (!OrbitMotion.enabled() || view.height == 0 || view.windowVisibility != View.VISIBLE) view.visibility = View.GONE
                else {
                    val startHeight = view.height
                    collapse = ValueAnimator.ofFloat(1f, 0f).apply {
                        duration = 160
                        addUpdateListener {
                            val f = it.animatedValue as Float
                            view.alpha = f
                            view.layoutParams = view.layoutParams.apply { this.height = (startHeight * f).toInt() }
                            if (f == 0f) view.visibility = View.GONE
                        }
                        start()
                    }
                }
            }
        }
        var observer: android.view.ViewTreeObserver? = null
        val visible = android.graphics.Rect()
        val impression = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!eligible(c, key) || key in sessions) observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
                else if (view.height > 0 && view.getGlobalVisibleRect(visible) && visible.height() >= view.height / 2) {
                    shown(c, key); observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
                }
                return true
            }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                p.registerOnSharedPreferenceChangeListener(listener)
                observer = v.viewTreeObserver
                if (key !in sessions) observer?.addOnPreDrawListener(impression)
            }
            override fun onViewDetachedFromWindow(v: View) {
                p.unregisterOnSharedPreferenceChangeListener(listener); collapse?.cancel()
                observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(impression); observer = null
            }
        })
    }
}
