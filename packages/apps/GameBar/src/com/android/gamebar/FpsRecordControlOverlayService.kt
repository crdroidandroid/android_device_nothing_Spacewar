/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.preference.PreferenceManager
import kotlin.math.abs

class FpsRecordControlOverlayService : Service() {

    companion object {
        const val ACTION_SET_ENABLED = "com.android.gamebar.action.SET_FPS_RECORD_CONTROL_ENABLED"
        const val EXTRA_ENABLED = "enabled"

        private const val PREF_ENABLED = "game_bar_fps_record_control_enabled"
        private const val PREF_X = "game_bar_fps_record_control_x"
        private const val PREF_Y = "game_bar_fps_record_control_y"
        private const val SAMPLE_INTERVAL_MS = 1000L
        private const val MONITOR_INTERVAL_MS = 700L
    }

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val perAppLogManager = PerAppLogManager.getInstance()

    private var bubbleView: ImageView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var blinkAnimator: ValueAnimator? = null

    @Volatile
    private var isRecording = false
    private var recordingPackage = ""

    private val sampleRunnable = object : Runnable {
        override fun run() {
            if (!isRecording) return
            val pkg = recordingPackage
            if (pkg.isNotBlank()) {
                val snapshot = GameBarStatsSampler.capture(this@FpsRecordControlOverlayService, pkg)
                GameBarStatsSampler.addToExport(this@FpsRecordControlOverlayService, snapshot)
            }
            handler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isRecording) return
            val foreground = ForegroundAppDetector.getForegroundPackageName(this@FpsRecordControlOverlayService)
            if (foreground != recordingPackage) {
                stopRecording(autoStop = true)
                return
            }
            handler.postDelayed(this, MONITOR_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val enabled = when (intent?.action) {
            ACTION_SET_ENABLED -> intent.getBooleanExtra(EXTRA_ENABLED, false)
            else -> prefs.getBoolean(PREF_ENABLED, false)
        }

        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply()

        if (!enabled) {
            removeBubble()
            stopRecording(autoStop = false)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_SHORT).show()
            prefs.edit().putBoolean(PREF_ENABLED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }

        if (bubbleView == null) {
            showBubble()
        }
        return START_STICKY
    }

    private fun showBubble() {
        val view = ImageView(this).apply {
            val size = (48 * resources.displayMetrics.density).toInt()
            layoutParams = WindowManager.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(14, 14, 14, 14)
            setOnTouchListener(BubbleTouchListener())
        }
        bubbleView = view
        updateBubbleAppearance()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(PREF_X, 24)
            y = prefs.getInt(PREF_Y, 220)
        }
        bubbleParams = params

        try {
            windowManager.addView(view, params)
        } catch (_: Exception) {
            bubbleView = null
            bubbleParams = null
        }
    }

    private fun removeBubble() {
        blinkAnimator?.cancel()
        blinkAnimator = null
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        bubbleView = null
        bubbleParams = null
    }

    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val params = bubbleParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    try {
                        windowManager.updateViewLayout(v, params)
                    } catch (_: Exception) {
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit().putInt(PREF_X, params.x).putInt(PREF_Y, params.y).apply()
                    if (!moved) {
                        toggleRecordingFromBubble()
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun toggleRecordingFromBubble() {
        if (!isRecording) {
            startRecording()
        } else {
            stopRecording(autoStop = false)
        }
    }

    private fun startRecording() {
        val currentPackage = ForegroundAppDetector.getForegroundPackageName(this)
        if (currentPackage.isBlank() || currentPackage == "Unknown" || currentPackage == packageName) {
            Toast.makeText(this, "Open your game first, then start recording", Toast.LENGTH_SHORT).show()
            return
        }

        val dataExport = GameDataExport.getInstance()
        dataExport.setLoggingMode(GameDataExport.LoggingMode.PER_APP)
        if (!dataExport.isCapturing()) {
            dataExport.startCapture()
        }

        GameBarFpsMeter.getInstance(this).start()
        perAppLogManager.startManualLoggingForApp(currentPackage)

        if (!perAppLogManager.isAppLoggingActive(currentPackage)) {
            return
        }

        recordingPackage = currentPackage
        isRecording = true
        updateBubbleAppearance()

        handler.removeCallbacks(sampleRunnable)
        handler.removeCallbacks(monitorRunnable)
        handler.post(sampleRunnable)
        handler.post(monitorRunnable)
    }

    private fun stopRecording(autoStop: Boolean) {
        if (!isRecording) return

        handler.removeCallbacks(sampleRunnable)
        handler.removeCallbacks(monitorRunnable)

        if (recordingPackage.isNotBlank()) {
            perAppLogManager.stopManualLoggingForApp(recordingPackage)
        }
        recordingPackage = ""
        isRecording = false

        if (perAppLogManager.getCurrentlyLoggingApps().isEmpty()) {
            GameDataExport.getInstance().stopCapture()
        }
        if (!GameBar.isShowing()) {
            GameBarFpsMeter.getInstance(this).stop()
        }

        if (autoStop) {
            Toast.makeText(this, "Recording stopped (app minimized/switched)", Toast.LENGTH_SHORT).show()
        }
        updateBubbleAppearance()
    }

    private fun updateBubbleAppearance() {
        val view = bubbleView ?: return

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (isRecording) Color.parseColor("#B71C1C") else Color.parseColor("#1E2B36"))
            setStroke(2, Color.parseColor("#80FFFFFF"))
        }
        view.background = bg
        view.setImageResource(if (isRecording) R.drawable.ic_fps_record_bubble_record else R.drawable.ic_fps_record_bubble_play)
        view.setColorFilter(Color.WHITE)

        blinkAnimator?.cancel()
        blinkAnimator = null
        if (isRecording) {
            blinkAnimator = ValueAnimator.ofFloat(1f, 0.35f).apply {
                duration = 700L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                addUpdateListener { anim -> view.alpha = anim.animatedValue as Float }
                start()
            }
        } else {
            view.alpha = 1f
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeBubble()
        stopRecording(autoStop = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

