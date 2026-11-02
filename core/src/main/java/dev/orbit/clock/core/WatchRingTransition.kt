package dev.orbit.clock.core

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.Choreographer
import android.view.animation.PathInterpolator
import kotlin.math.*

/** Full-resolution old scene over the real watch face; only vector light expands. */
object WatchRingTransition {
    private data class Scene(val bitmap: Bitmap, val x: Float, val y: Float, val created: Long)
    private var pending: Scene? = null
    fun openHome(activity: Activity, x: Float, y: Float) {
        pending?.bitmap?.recycle(); pending = null
        if (OrbitMotion.enabled()) {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            if (root.width > 0 && root.height > 0) {
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap)); pending = Scene(bitmap, x, y, SystemClock.elapsedRealtime())
            }
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val intents = if (pending != null) arrayOf(home, Intent(activity, WatchExitActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)) else arrayOf(home)
        // The transient activity has its own task and a transparent window. HOME
        // is below it, so the fade reveals the actual watch face, not Orbit.
        activity.startActivities(intents, ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle())
        activity.finish()
        @Suppress("DEPRECATION") activity.overridePendingTransition(0, 0)
    }
    fun attach(activity: Activity) {
        val scene = pending
        pending = null
        if (scene == null) { activity.finish(); return }
        if (!OrbitMotion.enabled() || SystemClock.elapsedRealtime() - scene.created > 5000) { scene.bitmap.recycle(); activity.finish(); return }
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val reveal = Reveal(activity, scene)
        root.addView(reveal, ViewGroup.LayoutParams(-1, -1))
        reveal.start()
    }
    private class Reveal(activity: Activity, private val scene: Scene) : View(activity) {
        private val ease = PathInterpolator(.2f, 0f, 0f, 1f)
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val matrix = Matrix()
        private val shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xaa74e8d7.toInt(), 0x5974e8d7, 0x0074e8d7), floatArrayOf(0f, .50f, 1f), Shader.TileMode.CLAMP)
        private var revealProgress = 0f
        private var revealRadius = 0f
        private var sceneAlpha = 1f
        private var running = false
        private var lastFrame = 0L
        private val frames = Choreographer.getInstance()
        private val animation = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return
                val elapsed = if (lastFrame == 0L) 0f else ((frameTimeNanos - lastFrame) / 1e6f).coerceIn(0f, 32f)
                lastFrame = frameTimeNanos
                revealProgress = (revealProgress + elapsed / 760f).coerceAtMost(1f)
                val growth = ease.getInterpolation(revealProgress)
                val farX = max(scene.x, width - scene.x); val farY = max(scene.y, height - scene.y)
                revealRadius = 12f * resources.displayMetrics.density + hypot(farX, farY) * 1.15f * growth
                val fade = ((revealProgress - .06f) / .80f).coerceIn(0f, 1f)
                sceneAlpha = 1f - fade * fade * (3f - 2f * fade)
                invalidate()
                if (revealProgress >= 1f || !OrbitMotion.enabled()) end()
                else frames.postFrameCallback(this)
            }
        }
        private val owner = activity
        private fun end() {
            if (!running) return
            running = false; frames.removeFrameCallback(animation)
            (parent as? ViewGroup)?.removeView(this)
            owner.finish()
            @Suppress("DEPRECATION") owner.overridePendingTransition(0, 0)
        }
        init { tag = "watch_exit_reveal"; isClickable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        fun start() { post {
            if (isAttachedToWindow) {
                running = true
                if (OrbitMotion.enabled()) { frames.postFrameCallback(animation); postDelayed({ end() }, 4000) }
                else end()
            }
        } }
        override fun onDraw(canvas: Canvas) {
            // This bitmap stays at its captured pixel size. It is never enlarged.
            imagePaint.alpha = (sceneAlpha * 255).toInt()
            canvas.drawBitmap(scene.bitmap, 0f, 0f, imagePaint)
            val ellipse = 1f + .35f * (1f - revealProgress)
            matrix.setScale(revealRadius, revealRadius * ellipse); matrix.postTranslate(scene.x, scene.y)
            shader.setLocalMatrix(matrix); glowPaint.shader = shader
            glowPaint.alpha = (sin(Math.PI * revealProgress).toFloat().coerceAtLeast(0f) * 150).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)
        }
        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (!hasWindowFocus && running) end()
        }
        override fun onDetachedFromWindow() {
            running = false; frames.removeFrameCallback(animation)
            if (!scene.bitmap.isRecycled) scene.bitmap.recycle(); super.onDetachedFromWindow()
        }
    }
}

/** Private, short-lived transition window; does not request overlay permission. */
class WatchExitActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setShowWhenLocked(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        WatchRingTransition.attach(this)
    }
}
