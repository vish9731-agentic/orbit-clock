package dev.orbit.clock.core

import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.*
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import kotlin.math.*

/** Full-resolution, unscaled old scene over the real home screen; only vector light expands. */
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
        val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            activity.startActivity(launch, ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle())
        }
        activity.finish()
        @Suppress("DEPRECATION") activity.overridePendingTransition(0, 0)
    }
    fun attach(activity: Activity) {
        val scene = pending ?: return; pending = null
        if (!OrbitMotion.enabled() || SystemClock.elapsedRealtime() - scene.created > 2000) { scene.bitmap.recycle(); return }
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
        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 760; interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                revealProgress = it.animatedValue as Float
                val growth = ease.getInterpolation(revealProgress)
                val farX = max(scene.x, width - scene.x); val farY = max(scene.y, height - scene.y)
                revealRadius = 12f * resources.displayMetrics.density + hypot(farX, farY) * 1.15f * growth
                val fade = ((revealProgress - .06f) / .80f).coerceIn(0f, 1f)
                sceneAlpha = 1f - fade * fade * (3f - 2f * fade)
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { (parent as? ViewGroup)?.removeView(this@Reveal) }
            })
        }
        init { tag = "watch_exit_reveal"; isClickable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        fun start() { post { if (isAttachedToWindow) { if (OrbitMotion.enabled()) animator.start() else (parent as? ViewGroup)?.removeView(this) } } }
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
            if (!hasWindowFocus && animator.isStarted) animator.end()
        }
        override fun onDetachedFromWindow() {
            animator.cancel(); if (!scene.bitmap.isRecycled) scene.bitmap.recycle(); super.onDetachedFromWindow()
        }
    }
}
