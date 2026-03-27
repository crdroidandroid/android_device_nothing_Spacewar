/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-FileCopyrightText: 2025 DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.android.gamebar.R
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.UserHandle
import com.android.systemui.screenrecord.IRemoteRecording
import com.android.systemui.screenrecord.IRecordingCallback

class GameBar private constructor(context: Context) {

    companion object {
        @Volatile
        private var sInstance: GameBar? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): GameBar {
            return sInstance ?: synchronized(this) {
                sInstance ?: GameBar(context.applicationContext).also { sInstance = it }
            }
        }
        
        @JvmStatic
        @Synchronized
        fun destroyInstance() {
            sInstance?.cleanup()
            sInstance = null
        }
        
        @JvmStatic
        fun isInstanceCreated(): Boolean {
            return sInstance != null
        }
        
        @JvmStatic
        fun isShowing(): Boolean {
            return sInstance?.isShowing == true
        }

        private const val PREF_KEY_X = "game_bar_x"
        private const val PREF_KEY_Y = "game_bar_y"
        private const val TOUCH_SLOP = 30f
    }

    private val context: Context = context.applicationContext
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler: Handler = Handler(Looper.getMainLooper())

    init {
        // Initialize config with context
        GameBarConfig.init(context)
    }

    private var overlayView: View? = null
    private var rootLayout: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    @Volatile
    private var isShowing = false

    // Style properties
    private var textSizeSp = 15
    private var backgroundAlpha = 255
    private var backgroundColorInt = 0xFF0D1117.toInt()
    private var cornerRadius = 30
    private var paddingDp = 4
    private var titleColorHex = "#9FCEDE"
    private var valueColorHex = "#FFB347"
    private var customTypeface: Typeface? = null
    private var overlayFormat = "full"
    private var position = "top_center"
    private var splitMode = "side_by_side"
    private var updateIntervalMs = 1000
    private var draggable = false

    // Display toggles
    private var showBatteryTemp = false
    private var showCpuUsage = true
    private var showCpuClock = false
    private var showCpuTemp = false
    private var showRam = false
    private var showFps = true
    private var fpsDisplayMode = "basic"
    private var showFrameTime = false
    private var showGpuUsage = true
    private var showGpuClock = false
    private var showGpuTemp = false
    private var showRamSpeed = false
    private var showRamTemp = false

    // Touch handling
    private var longPressEnabled = false
    private var longPressThresholdMs = 500L
    private var pressActive = false
    private var downX = 0f
    private var downY = 0f

    private var gestureDetector: GestureDetector? = null
    private var singleTapEnabled = true
    private var singleTapFunction = "toggle_format"
    private var doubleTapEnabled = true
    private var doubleTapFunction = "capture_logs"
    private var longPressFunction = "load_preset"
    private var bgDrawable: GradientDrawable? = null
    
    private var isRecorderBound = false
    private var remoteRecording: IRemoteRecording? = null
    private val recorderConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remoteRecording = IRemoteRecording.Stub.asInterface(service)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remoteRecording = null
        }
    }

    private var itemSpacingDp = 8
    private var layoutChanged = false

    // Touch coordinates for dragging
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    init {
        bgDrawable = GradientDrawable()
        applyBackgroundStyle()
        
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (doubleTapEnabled) {
                    executeGestureFunction(doubleTapFunction)
                    return true
                }
                return super.onDoubleTap(e)
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (singleTapEnabled) {
                    executeGestureFunction(singleTapFunction)
                    return true
                }
                return super.onSingleTapConfirmed(e)
            }
        })
    }

    private fun executeGestureFunction(function: String) {
        when (function) {
            "no_action" -> {
                // Do nothing
            }
            "toggle_format" -> {
                overlayFormat = if (overlayFormat == "full") "minimal" else "full"
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString("game_bar_format", overlayFormat)
                    .apply()
                Toast.makeText(context, context.getString(R.string.toast_overlay_format, overlayFormat), Toast.LENGTH_SHORT).show()
                updateStats()
            }
            "capture_logs" -> {
                val dataExport = GameDataExport.getInstance()
                val perAppLogManager = dataExport.getPerAppLogManager()
                val currentPackage = ForegroundAppDetector.getForegroundPackageName(context)
                if (currentPackage.isBlank() || currentPackage == "Unknown") {
                    Toast.makeText(context, "Unable to detect foreground app", Toast.LENGTH_SHORT).show()
                    return
                }
                
                if (dataExport.getLoggingMode() == GameDataExport.LoggingMode.PER_APP) {
                    if (perAppLogManager.isAppLoggingActive(currentPackage)) {
                        perAppLogManager.stopManualLoggingForApp(currentPackage)
                        // Auto-stop capture when no active app sessions remain.
                        if (perAppLogManager.getCurrentlyLoggingApps().isEmpty()) {
                            dataExport.stopCapture()
                        }
                        Toast.makeText(context, R.string.toast_manual_logging_stopped, Toast.LENGTH_SHORT).show()
                    } else {
                        // Double tap now fully controls session logging lifecycle.
                        if (!dataExport.isCapturing()) {
                            dataExport.startCapture()
                        }
                        perAppLogManager.startManualLoggingForApp(currentPackage)
                    }
                } else {
                    if (dataExport.isCapturing()) {
                        dataExport.stopCapture()
                        dataExport.exportDataToCsv()
                        Toast.makeText(context, R.string.toast_capture_stopped, Toast.LENGTH_SHORT).show()
                    } else {
                        dataExport.startCapture()
                        Toast.makeText(context, R.string.toast_capture_started, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            "open_settings" -> {
                openOverlaySettings()
            }
            "take_screenshot" -> {
                takeScreenshot()
            }
            "screen_record" -> {
                toggleScreenRecording()
            }
            "load_preset" -> {
                showPresetQuickLoader()
            }
        }
    }

    // Long press runnable
    private val longPressRunnable = Runnable {
        if (pressActive) {
            executeGestureFunction(longPressFunction)
            pressActive = false
        }
    }

    // Update runnable
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isShowing) {
                updateStats()
                handler.postDelayed(this, updateIntervalMs.toLong())
            }
        }
    }

    fun applyPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        showFps = prefs.getBoolean("game_bar_fps_enable", true)
        fpsDisplayMode = prefs.getString("game_bar_fps_display_mode", "basic") ?: "basic"
        showFrameTime = prefs.getBoolean("game_bar_frame_time_enable", false)
        showBatteryTemp = prefs.getBoolean("game_bar_temp_enable", false)
        showCpuUsage = prefs.getBoolean("game_bar_cpu_usage_enable", true)
        showCpuClock = prefs.getBoolean("game_bar_cpu_clock_enable", false)
        showCpuTemp = prefs.getBoolean("game_bar_cpu_temp_enable", false)
        showRam = prefs.getBoolean("game_bar_ram_enable", false)

        showGpuUsage = prefs.getBoolean("game_bar_gpu_usage_enable", true)
        showGpuClock = prefs.getBoolean("game_bar_gpu_clock_enable", false)
        showGpuTemp = prefs.getBoolean("game_bar_gpu_temp_enable", false)

        showRamSpeed = prefs.getBoolean("game_bar_ram_speed_enable", false)
        showRamTemp = prefs.getBoolean("game_bar_ram_temp_enable", false)

        singleTapEnabled = prefs.getBoolean("game_bar_single_tap_enable", true)
        singleTapFunction = prefs.getString("game_bar_single_tap_function", "toggle_format") ?: "toggle_format"
        doubleTapEnabled = prefs.getBoolean("game_bar_doubletap_enable", true)
        doubleTapFunction = prefs.getString("game_bar_doubletap_function", "capture_logs") ?: "capture_logs"
        longPressFunction = prefs.getString("game_bar_longpress_function", "load_preset") ?: "load_preset"

        updateSplitMode(prefs.getString("game_bar_split_mode", "side_by_side") ?: "side_by_side")
        updateTextSize(prefs.getInt("game_bar_text_size", 15))
        updateBackgroundAlpha(prefs.getInt("game_bar_background_alpha", 255))
        updateBackgroundColor(prefs.getInt("game_bar_background_color", 0xFF0D1117.toInt()))
        updateCornerRadius(prefs.getInt("game_bar_corner_radius", 30))
        updatePadding(prefs.getInt("game_bar_padding", 4))
        val titleColorInt = prefs.getInt("game_bar_title_color", 0xFF9FCEDE.toInt())
        val titleColorHex = String.format("#%06X", 0xFFFFFF and titleColorInt)
        updateTitleColor(titleColorHex)
        
        val valueColorInt = prefs.getInt("game_bar_value_color", 0xFFFFB347.toInt())
        val valueColorHex = String.format("#%06X", 0xFFFFFF and valueColorInt)
        updateValueColor(valueColorHex)
        
        // Load custom font
        val fontPath = prefs.getString("game_bar_font_path", "fonts/Circular-Std-Bold.ttf") ?: "fonts/Circular-Std-Bold.ttf"
        loadCustomFont(fontPath)
        
        updateOverlayFormat(prefs.getString("game_bar_format", "full") ?: "full")
        updateUpdateInterval(prefs.getString("game_bar_update_interval", "1000") ?: "1000")
        updatePosition(prefs.getString("game_bar_position", "draggable") ?: "draggable")

        val spacing = prefs.getInt("game_bar_item_spacing", 8)
        updateItemSpacing(spacing)

        longPressEnabled = prefs.getBoolean("game_bar_longpress_enable", true)
        val lpTimeoutStr = prefs.getString("game_bar_longpress_timeout", "500") ?: "500"
        try {
            val lpt = lpTimeoutStr.toLong()
            setLongPressThresholdMs(lpt)
        } catch (ignored: NumberFormatException) {}
    }

    fun show() {
        // Force cleanup any existing overlay before showing new one
        if (isShowing) {
            hide()
        }
        
        // Double check to make sure no overlay view exists
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                // View might already be removed
            }
            overlayView = null
        }

        bindScreenRecorder()

        applyPreferences()

        layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        )

        if ("draggable" == position) {
            draggable = true
            loadSavedPosition(layoutParams!!)
            if (layoutParams!!.x == 0 && layoutParams!!.y == 0) {
                layoutParams!!.gravity = Gravity.TOP or Gravity.START
                layoutParams!!.x = 0
                layoutParams!!.y = 100
            }
        } else {
            draggable = false
            applyPosition(layoutParams!!, position)
        }

        overlayView = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout = overlayView as LinearLayout
        applySplitMode()
        applyBackgroundStyle()
        applyPadding()

        overlayView?.setOnTouchListener { _, event ->
            gestureDetector?.let {
                if (it.onTouchEvent(event)) {
                    return@setOnTouchListener true
                }
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (draggable) {
                        initialX = layoutParams!!.x
                        initialY = layoutParams!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                    }
                    if (longPressEnabled) {
                        pressActive = true
                        downX = event.rawX
                        downY = event.rawY
                        handler.postDelayed(longPressRunnable, longPressThresholdMs)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (longPressEnabled && pressActive) {
                        val dx = Math.abs(event.rawX - downX)
                        val dy = Math.abs(event.rawY - downY)
                        if (dx > TOUCH_SLOP || dy > TOUCH_SLOP) {
                            pressActive = false
                            handler.removeCallbacks(longPressRunnable)
                        }
                    }
                    if (draggable) {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        layoutParams!!.x = initialX + deltaX
                        layoutParams!!.y = initialY + deltaY
                        windowManager.updateViewLayout(overlayView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (longPressEnabled && pressActive) {
                        pressActive = false
                        handler.removeCallbacks(longPressRunnable)
                    }
                    if (draggable) {
                        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                        prefs.edit()
                                .putInt(PREF_KEY_X, layoutParams!!.x)
                                .putInt(PREF_KEY_Y, layoutParams!!.y)
                                .apply()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(overlayView, layoutParams)
        isShowing = true
        startUpdates()

        // Start the FPS meter if using the new API method
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            GameBarFpsMeter.getInstance(context).start()
        }
    }

    fun hide() {
        // Set showing to false first to prevent any further operations
        isShowing = false
        
        stopUpdates()
        
        // Stop FPS meter first
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            GameBarFpsMeter.getInstance(context).stop()
        }
        
        // Remove overlay view
        overlayView?.let { view ->
            try {
                if (view.parent != null) {
                    windowManager.removeView(view)
                }
            } catch (e: Exception) {
                // View might already be removed, log but continue cleanup
                android.util.Log.w("GameBar", "Error removing overlay view: ${e.message}")
            }
        }
        
        // Clear all references
        overlayView = null
        rootLayout = null
        layoutParams = null
        layoutChanged = true // Mark layout as changed
        
        unbindScreenRecorder()
    }
    
    fun cleanup() {
        hide()
        
        // Remove all handler callbacks and messages
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacksAndMessages(null)
        
        // Clear all object references to prevent memory leaks
        gestureDetector = null
        bgDrawable = null
        
        // Reset all state variables
        pressActive = false
        layoutChanged = false
    }
    
    private fun stopUpdates() {
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacksAndMessages(null)
    }
    
    private fun startUpdates() {
        handler.removeCallbacksAndMessages(null)
        handler.post(updateRunnable)
    }

    private fun updateStats() {
        // Early return if not showing or no layout
        if (!isShowing) return
        val layout = rootLayout ?: return
        
        try {
            // Always clear views to prevent duplication
            layout.removeAllViews()
            layoutChanged = false

        // Create fresh views each time
        val statViews = mutableListOf<View>()

        // 1) FPS - Always collect for logging
        val fpsMeter = GameBarFpsMeter.getInstance(context)
        val fpsVal = fpsMeter.getFps()
        val fpsStr = if (fpsVal >= 0) String.format(Locale.getDefault(), "%.0f", fpsVal) else "N/A"
        
        if (showFps) {
            if (fpsDisplayMode == "advanced") {
                // Advanced mode
                val fps1PercentLow = fpsMeter.get1PercentLowFps()
                val fps01PercentLow = fpsMeter.get01PercentLowFps()
                
                val fpsStats = mutableListOf<String>()
                fpsStats.add("FPS: $fpsStr")
                
                if (fps1PercentLow >= 0) {
                    fpsStats.add("1% Low: ${String.format(Locale.getDefault(), "%.0f", fps1PercentLow)}")
                } else {
                    fpsStats.add("1% Low: Collecting...")
                }
                
                if (fps01PercentLow >= 0) {
                    fpsStats.add("0.1% Low: ${String.format(Locale.getDefault(), "%.0f", fps01PercentLow)}")
                } else {
                    fpsStats.add("0.1% Low: Collecting...")
                }
                
                statViews.add(buildFpsView(fpsStats))
            } else {
                // Basic mode
                statViews.add(createStatLine("FPS", fpsStr))
            }
        }

        // 1.1) Frame Time - Calculate from FPS
        var frameTimeStr = "N/A"
        if (fpsVal > 0) {
            val frameTime = 1000.0 / fpsVal
            frameTimeStr = String.format(Locale.getDefault(), "%.2f", frameTime)
        }
        if (showFrameTime) {
            statViews.add(createStatLine("Frame Time", if (frameTimeStr == "N/A") "N/A" else "${frameTimeStr}ms"))
        }

        // 2) Battery temperature
        var batteryTempStr = GameBarBatteryInfo.getBatteryTempC(context)
        if (showBatteryTemp) {
            statViews.add(createStatLine("Temp", "${batteryTempStr}°C"))
        }

        // 3) CPU usage - Always collect for logging
        var cpuUsageStr = "N/A"
        cpuUsageStr = GameBarCpuInfo.getCpuUsage()
        if (showCpuUsage) {
            val display = if (cpuUsageStr == "N/A") "N/A" else "${cpuUsageStr}%"
            statViews.add(createStatLine("CPU", display))
        }

        // 4) CPU freq - Always collect for logging
        var cpuClockStr = "N/A"
        if (showCpuClock) {
            val freqs = GameBarCpuInfo.getCpuFrequencies()
            if (freqs.isNotEmpty()) {
                statViews.add(buildCpuFreqView(freqs))
                cpuClockStr = freqs.joinToString("; ")
            }
        } else {
            // Still collect even if not shown, for potential logging
            val freqs = GameBarCpuInfo.getCpuFrequencies()
            if (freqs.isNotEmpty()) {
                cpuClockStr = freqs.joinToString("; ")
            }
        }

        // 5) CPU temp
        var cpuTempStr = "N/A"
        if (showCpuTemp) {
            cpuTempStr = GameBarCpuInfo.getCpuTemp()
            statViews.add(createStatLine("CPU Temp", if (cpuTempStr == "N/A") "N/A" else "${cpuTempStr}°C"))
        } else {
            // Still collect even if not shown
            cpuTempStr = GameBarCpuInfo.getCpuTemp()
        }

        // 6) RAM usage
        var ramStr = "N/A"
        if (showRam) {
            ramStr = GameBarMemInfo.getRamUsage()
            statViews.add(createStatLine("RAM", if (ramStr == "N/A") "N/A" else "$ramStr MB"))
        } else {
            // Still collect even if not shown
            ramStr = GameBarMemInfo.getRamUsage()
        }

        // 6.1) RAM speed
        var ramSpeedStr = "N/A"
        if (showRamSpeed) {
            ramSpeedStr = GameBarMemInfo.getRamSpeed()
            statViews.add(createStatLine("RAM Freq", ramSpeedStr))
        } else {
            // Still collect even if not shown
            ramSpeedStr = GameBarMemInfo.getRamSpeed()
        }

        // 6.2) RAM temp
        var ramTempStr = "N/A"
        if (showRamTemp) {
            ramTempStr = GameBarMemInfo.getRamTemp()
            statViews.add(createStatLine("RAM Temp", ramTempStr))
        } else {
            // Still collect even if not shown
            ramTempStr = GameBarMemInfo.getRamTemp()
        }

        // 7) GPU usage - Always collect for logging
        var gpuUsageStr = "N/A"
        gpuUsageStr = GameBarGpuInfo.getGpuUsage()
        if (showGpuUsage) {
            statViews.add(createStatLine("GPU", if (gpuUsageStr == "N/A") "N/A" else "${gpuUsageStr}%"))
        }

        // 8) GPU clock - Always collect for logging
        var gpuClockStr = "N/A"
        gpuClockStr = GameBarGpuInfo.getGpuClock()
        if (showGpuClock) {
            statViews.add(createStatLine("GPU Freq", if (gpuClockStr == "N/A") "N/A" else "${gpuClockStr}MHz"))
        }

        // 9) GPU temp - Always collect for logging
        var gpuTempStr = "N/A"
        gpuTempStr = GameBarGpuInfo.getGpuTemp()
        if (showGpuTemp) {
            statViews.add(createStatLine("GPU Temp", if (gpuTempStr == "N/A") "N/A" else "${gpuTempStr}°C"))
        }

        // 10) Battery level - logging data
        val batteryLevelStr = GameBarBatteryInfo.getBatteryLevelPercent(context)
        val batteryPowerWattStr = GameBarBatteryInfo.getBatteryPowerWatt(context)

        if (splitMode == "side_by_side") {
            layout.orientation = LinearLayout.HORIZONTAL
            if (overlayFormat == "minimal") {
                for (i in statViews.indices) {
                    layout.addView(statViews[i])
                    if (i < statViews.size - 1) {
                        layout.addView(createDotView())
                    }
                }
            } else {
                for (view in statViews) {
                    layout.addView(view)
                }
            }
        } else {
            layout.orientation = LinearLayout.VERTICAL
            for (view in statViews) {
                layout.addView(view)
            }
        }

        if (GameDataExport.getInstance().isCapturing()) {
            val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val pkgName = ForegroundAppDetector.getForegroundPackageName(context)
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            
            // Check logging parameters and use N/A if disabled
            val logFps = if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_FPS, true)) fpsStr else "N/A"
            val logFrameTime = if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_FRAME_TIME, true)) frameTimeStr else "N/A"
            val logBatteryTemp = if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_BATTERY_TEMP, true)) batteryTempStr else "N/A"
            val logCpuUsage = if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_CPU_USAGE, true)) cpuUsageStr else "N/A"
            val logCpuClock = if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_CPU_CLOCK, true)) cpuClockStr else "N/A"
            val logCpuTemp = if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_CPU_TEMP, true)) cpuTempStr else "N/A"
            val logRam = if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_RAM, true)) ramStr else "N/A"
            val logRamSpeed = if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_RAM_SPEED, true)) ramSpeedStr else "N/A"
            val logRamTemp = if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_RAM_TEMP, true)) ramTempStr else "N/A"
            val logGpuUsage = if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_GPU_USAGE, true)) gpuUsageStr else "N/A"
            val logGpuClock = if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_GPU_CLOCK, true)) gpuClockStr else "N/A"
            val logGpuTemp = if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_GPU_TEMP, true)) gpuTempStr else "N/A"
            val logBatteryLevel = batteryLevelStr
            val logPowerWatt = batteryPowerWattStr

            GameDataExport.getInstance().addOverlayData(
                    dateTime,
                    pkgName,
                    logFps,
                    logFrameTime,
                    logBatteryTemp,
                    logCpuUsage,
                    logCpuClock,
                    logCpuTemp,
                    logRam,
                    logRamSpeed,
                    logRamTemp,
                    logGpuUsage,
                    logGpuClock,
                    logGpuTemp,
                    logBatteryLevel,
                    logPowerWatt
            )
        }

            layoutParams?.let { lp ->
                overlayView?.let { view ->
                    try {
                        windowManager.updateViewLayout(view, lp)
                    } catch (e: Exception) {
                        // View might be in invalid state, ignore
                    }
                }
            }
        } catch (e: Exception) {
            // Log error but continue operation to prevent crashes
            android.util.Log.e("GameBar", "Error updating overlay stats: ${e.message}")
        }
    }

    private fun buildCpuFreqView(freqs: List<String>): View {
        val freqContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val spacingPx = dpToPx(context, itemSpacingDp)
        val outerLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(spacingPx, spacingPx / 2, spacingPx, spacingPx / 2)
        }
        freqContainer.layoutParams = outerLp

        if (overlayFormat == "full") {
            val labelTv = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat())
                try {
                    setTextColor(Color.parseColor(titleColorHex))
                } catch (e: Exception) {
                    setTextColor(Color.WHITE)
                }
                setTypeface(this@GameBar.getTypeface(), Typeface.NORMAL)
                text = "CPU Freq "
            }
            freqContainer.addView(labelTv)
        }

        val verticalFreqs = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (freqLine in freqs) {
            val lineLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val freqTv = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat())
                try {
                    setTextColor(Color.parseColor(valueColorHex))
                } catch (e: Exception) {
                    setTextColor(Color.WHITE)
                }
                setTypeface(this@GameBar.getTypeface(), Typeface.NORMAL)
                text = freqLine
            }

            lineLayout.addView(freqTv)

            val lineLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(spacingPx, spacingPx / 4, spacingPx, spacingPx / 4)
            }
            lineLayout.layoutParams = lineLp

            verticalFreqs.addView(lineLayout)
        }

        freqContainer.addView(verticalFreqs)
        return freqContainer
    }

    private fun buildFpsView(fpsStats: List<String>): View {
        val fpsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val spacingPx = dpToPx(context, itemSpacingDp)
        val outerLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(spacingPx, spacingPx / 2, spacingPx, spacingPx / 2)
        }
        fpsContainer.layoutParams = outerLp

        if (overlayFormat == "full") {
            val labelTv = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat())
                try {
                    setTextColor(Color.parseColor(titleColorHex))
                } catch (e: Exception) {
                    setTextColor(Color.WHITE)
                }
                setTypeface(this@GameBar.getTypeface(), Typeface.NORMAL)
                text = "FPS Stats "
            }
            fpsContainer.addView(labelTv)
        }

        val verticalStats = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (statLine in fpsStats) {
            val lineLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val statTv = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat())
                try {
                    setTextColor(Color.parseColor(valueColorHex))
                } catch (e: Exception) {
                    setTextColor(Color.WHITE)
                }
                setTypeface(this@GameBar.getTypeface(), Typeface.NORMAL)
                text = statLine
            }

            lineLayout.addView(statTv)

            val lineLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(spacingPx, spacingPx / 4, spacingPx, spacingPx / 4)
            }
            lineLayout.layoutParams = lineLp

            verticalStats.addView(lineLayout)
        }

        fpsContainer.addView(verticalStats)
        return fpsContainer
    }

    private fun createStatLine(title: String, rawValue: String): LinearLayout {
        val lineLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        if (overlayFormat == "full") {
            val tvTitle = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat())
                try {
                    setTextColor(Color.parseColor(titleColorHex))
                } catch (e: Exception) {
                    setTextColor(Color.WHITE)
                }
                setTypeface(this@GameBar.getTypeface(), Typeface.NORMAL)
                text = if (title.isEmpty()) "" else "$title "
            }

            val tvValue = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat())
                try {
                    setTextColor(Color.parseColor(valueColorHex))
                } catch (e: Exception) {
                    setTextColor(Color.WHITE)
                }
                setTypeface(this@GameBar.getTypeface(), Typeface.NORMAL)
                text = rawValue
            }

            lineLayout.addView(tvTitle)
            lineLayout.addView(tvValue)
        } else {
            val tvMinimal = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat())
                try {
                    setTextColor(Color.parseColor(valueColorHex))
                } catch (e: Exception) {
                    setTextColor(Color.WHITE)
                }
                setTypeface(this@GameBar.getTypeface(), Typeface.NORMAL)
                text = rawValue
            }
            lineLayout.addView(tvMinimal)
        }

        val spacingPx = dpToPx(context, itemSpacingDp)
        val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(spacingPx, spacingPx / 2, spacingPx, spacingPx / 2)
        }
        lineLayout.layoutParams = lp

        return lineLayout
    }

    private fun createDotView(): View {
        return TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat())
            try {
                setTextColor(Color.parseColor(valueColorHex))
            } catch (e: Exception) {
                setTextColor(Color.WHITE)
            }
            setTypeface(this@GameBar.getTypeface(), Typeface.NORMAL)
            text = " . "
        }
    }

    // Public setter methods for feature toggles
    fun setShowBatteryTemp(show: Boolean) { showBatteryTemp = show }
    fun setShowCpuUsage(show: Boolean) { showCpuUsage = show }
    fun setShowCpuClock(show: Boolean) { showCpuClock = show }
    fun setShowCpuTemp(show: Boolean) { showCpuTemp = show }
    fun setShowRam(show: Boolean) { showRam = show }
    fun setShowFps(show: Boolean) { showFps = show }
    fun setShowFrameTime(show: Boolean) { showFrameTime = show }
    fun setShowGpuUsage(show: Boolean) { showGpuUsage = show }
    fun setShowGpuClock(show: Boolean) { showGpuClock = show }
    fun setShowGpuTemp(show: Boolean) { showGpuTemp = show }
    fun setShowRamSpeed(show: Boolean) { showRamSpeed = show }
    fun setShowRamTemp(show: Boolean) { showRamTemp = show }

    fun updateTextSize(sp: Int) {
        textSizeSp = sp
    }

    fun updateCornerRadius(radius: Int) {
        cornerRadius = radius
        applyBackgroundStyle()
    }

    fun updateBackgroundAlpha(alpha: Int) {
        backgroundAlpha = alpha
        applyBackgroundStyle()
    }

    fun updatePadding(dp: Int) {
        paddingDp = dp
        applyPadding()
    }

    fun updateTitleColor(hex: String) {
        titleColorHex = hex
    }

    fun updateValueColor(hex: String) {
        valueColorHex = hex
    }

    fun updateBackgroundColor(colorInt: Int) {
        backgroundColorInt = colorInt
        applyBackgroundStyle()
    }

    fun updateFont(fontPath: String) {
        loadCustomFont(fontPath)
        // Apply new typeface in-place to avoid overlay flicker
        if (isShowing) {
            applyTypefaceToOverlay()
        }
    }

    private fun loadCustomFont(fontPath: String) {
        customTypeface = if (fontPath == "default" || fontPath.isEmpty()) {
            null
        } else {
            try {
                Typeface.createFromAsset(context.assets, fontPath)
            } catch (e: Exception) {
                android.util.Log.e("GameBar", "Failed to load font: $fontPath - ${e.message}")
                null
            }
        }
    }

    private fun getTypeface(): Typeface {
        return customTypeface ?: Typeface.DEFAULT
    }

    private fun applyTypefaceToOverlay() {
        val root = rootLayout ?: return
        val targetTypeface = getTypeface()
        fun traverse(view: View) {
            when (view) {
                is TextView -> view.setTypeface(targetTypeface, Typeface.NORMAL)
                is ViewGroup -> {
                    for (i in 0 until view.childCount) {
                        traverse(view.getChildAt(i))
                    }
                }
            }
        }
        try {
            traverse(root)
            // Ensure layout is refreshed without rebuilding
            layoutParams?.let { lp ->
                overlayView?.let { view ->
                    try {
                        windowManager.updateViewLayout(view, lp)
                    } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GameBar", "Error applying typeface to overlay: ${e.message}")
        }
    }

    fun updateOverlayFormat(format: String) {
        overlayFormat = format
        if (isShowing) {
            updateStats()
        }
    }

    fun updateItemSpacing(dp: Int) {
        itemSpacingDp = dp
        if (isShowing) {
            updateStats()
        }
    }

    fun updatePosition(pos: String) {
        position = pos
        if (isShowing && overlayView != null && layoutParams != null) {
            if ("draggable" == position) {
                draggable = true
                loadSavedPosition(layoutParams!!)
                if (layoutParams!!.x == 0 && layoutParams!!.y == 0) {
                    layoutParams!!.gravity = Gravity.TOP or Gravity.START
                    layoutParams!!.x = 0
                    layoutParams!!.y = 100
                }
            } else {
                draggable = false
                applyPosition(layoutParams!!, position)
            }
            windowManager.updateViewLayout(overlayView, layoutParams)
        }
    }

    fun updateSplitMode(mode: String) {
        splitMode = mode
        if (isShowing && overlayView != null) {
            applySplitMode()
            updateStats()
        }
    }

    fun updateUpdateInterval(intervalStr: String) {
        try {
            updateIntervalMs = intervalStr.toInt()
        } catch (e: NumberFormatException) {
            updateIntervalMs = 1000
        }
        if (isShowing) {
            startUpdates()
        }
    }

    fun setLongPressEnabled(enabled: Boolean) {
        longPressEnabled = enabled
    }
    
    fun setLongPressThresholdMs(ms: Long) {
        longPressThresholdMs = ms
    }

    
    fun isCurrentlyShowing(): Boolean {
        return isShowing
    }

    private fun applyBackgroundStyle() {
        // Ensure we have a valid bgDrawable
        if (bgDrawable == null) {
            bgDrawable = GradientDrawable()
        }
        
        // Apply background color with proper alpha
        val red = Color.red(backgroundColorInt)
        val green = Color.green(backgroundColorInt)
        val blue = Color.blue(backgroundColorInt)
        
        // Use backgroundAlpha for transparency control
        val color = Color.argb(
            backgroundAlpha,
            red, green, blue
        )
        bgDrawable?.setColor(color)
        bgDrawable?.cornerRadius = cornerRadius.toFloat()

        // Only apply background if overlay view exists
        overlayView?.let { view ->
            view.background = bgDrawable
        }
    }

    private fun applyPadding() {
        rootLayout?.let {
            val px = dpToPx(context, paddingDp)
            it.setPadding(px, px, px, px)
        }
    }

    private fun applySplitMode() {
        rootLayout?.let {
            it.orientation = if (splitMode == "side_by_side") {
                LinearLayout.HORIZONTAL
            } else {
                LinearLayout.VERTICAL
            }
        }
    }

    private fun loadSavedPosition(lp: WindowManager.LayoutParams) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val savedX = prefs.getInt(PREF_KEY_X, Int.MIN_VALUE)
        val savedY = prefs.getInt(PREF_KEY_Y, Int.MIN_VALUE)
        if (savedX != Int.MIN_VALUE && savedY != Int.MIN_VALUE) {
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = savedX
            lp.y = savedY
        }
    }

    private fun applyPosition(lp: WindowManager.LayoutParams, pos: String) {
        when (pos) {
            "top_left" -> {
                lp.gravity = Gravity.TOP or Gravity.START
                lp.x = 0
                lp.y = 100
            }
            "top_center" -> {
                lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                lp.y = 100
            }
            "top_right" -> {
                lp.gravity = Gravity.TOP or Gravity.END
                lp.x = 0
                lp.y = 100
            }
            "bottom_left" -> {
                lp.gravity = Gravity.BOTTOM or Gravity.START
                lp.x = 0
                lp.y = 100
            }
            "bottom_center" -> {
                lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                lp.y = 100
            }
            "bottom_right" -> {
                lp.gravity = Gravity.BOTTOM or Gravity.END
                lp.x = 0
                lp.y = 100
            }
            else -> {
                lp.gravity = Gravity.TOP or Gravity.START
                lp.x = 0
                lp.y = 100
            }
        }
    }

    private fun readLine(path: String): String? {
        return try {
            BufferedReader(FileReader(path)).use { it.readLine() }
        } catch (e: IOException) {
            null
        }
    }

    private fun takeScreenshot() {
        try {
            // Trigger system screenshot using shell command
            Runtime.getRuntime().exec("input keyevent KEYCODE_SYSRQ")
            Toast.makeText(context, R.string.toast_screenshot_taken, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, R.string.toast_screenshot_failed, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun bindScreenRecorder() {
        try {
            isRecorderBound = context.bindServiceAsUser(Intent().apply {
                component = ComponentName(
                    "com.android.systemui",
                    "com.android.systemui.screenrecord.RecordingService"
                )
            }, recorderConnection, Context.BIND_AUTO_CREATE, UserHandle.CURRENT)
        } catch (e: Exception) {
            android.util.Log.e("GameBar", "Failed to bind screen recorder: ${e.message}")
            isRecorderBound = false
        }
    }
    
    private fun unbindScreenRecorder() {
        if (isRecorderBound) {
            try {
                context.unbindService(recorderConnection)
            } catch (e: Exception) {
                android.util.Log.w("GameBar", "Failed to unbind screen recorder: ${e.message}")
            } finally {
                isRecorderBound = false
                remoteRecording = null
            }
        }
    }
    
    private fun toggleScreenRecording() {
        val recorder = remoteRecording
        if (recorder == null) {
            Toast.makeText(context, R.string.toast_screen_recorder_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val isStarting = try { recorder.isStarting } catch (e: Exception) { false }
            val isRecording = try { recorder.isRecording } catch (e: Exception) { false }
            
            if (!isStarting) {
                if (!isRecording) {
                    recorder.startRecording()
                    Toast.makeText(context, R.string.toast_screen_recording_started, Toast.LENGTH_SHORT).show()
                } else {
                    recorder.stopRecording()
                    Toast.makeText(context, R.string.toast_screen_recording_stopped, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, R.string.toast_screen_recording_failed, Toast.LENGTH_SHORT).show()
            android.util.Log.e("GameBar", "Screen recording error: ${e.message}")
        }
    }
    
    private fun showPresetQuickLoader() {
        PresetQuickLoaderOverlay.getInstance(context).show()
    }
    
    private fun openOverlaySettings() {
        try {
            val intent = Intent(context, GameBarSettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Exception ignored
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        val scale = context.resources.displayMetrics.density
        return Math.round(dp * scale)
    }
}
