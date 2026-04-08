/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.util.TypedValue

/**
 * Quick preset loader overlay - shows a floating list of saved presets
 * for quick loading without opening the app
 */
class PresetQuickLoaderOverlay(private val context: Context) {
    
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var isShowing = false
    
    fun show() {
        if (isShowing) {
            hide()
            return
        }
        
        val presetManager = PresetManager.getInstance(context)
        val presets = presetManager.getAllPresets()
        
        if (presets.isEmpty()) {
            // Show "No presets" message
            showNoPresetsMessage()
            return
        }
        
        // Create overlay container
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            
            // Background with rounded corners
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#E0000000"))
                cornerRadius = dpToPx(12).toFloat()
            }
            background = bg
        }
        
        // Title
        val title = TextView(context).apply {
            text = "Quick Load Preset"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(12))
        }
        container.addView(title)
        
        // Scrollable preset list (max height 400dp)
        val maxHeight = dpToPx(400)
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(280),
                maxHeight
            )
        }
        
        val presetList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        // Add preset items
        for (preset in presets) {
            val presetItem = createPresetItem(preset, presetManager)
            presetList.addView(presetItem)
        }
        
        scrollView.addView(presetList)
        container.addView(scrollView)
        
        // Close button
        val closeButton = TextView(context).apply {
            text = "✕ Close"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#FF5252"))
            gravity = Gravity.CENTER
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            setOnClickListener {
                hide()
            }
        }
        container.addView(closeButton)
        
        // Window layout params
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        
        overlayView = container
        windowManager.addView(container, params)
        isShowing = true
        
        // Auto-hide on outside touch
        container.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                hide()
                true
            } else {
                false
            }
        }
    }
    
    private fun createPresetItem(preset: PresetManager.Preset, presetManager: PresetManager): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#40FFFFFF"))
                cornerRadius = dpToPx(8).toFloat()
            }
            background = bg
            
            val margin = dpToPx(4)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, margin, 0, margin)
            }
            
            // Preset name
            val nameText = TextView(context).apply {
                text = preset.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            addView(nameText)
            
            // Preset date
            val dateText = TextView(context).apply {
                text = "Created: ${preset.createdDate}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(Color.parseColor("#B0FFFFFF"))
                setPadding(0, dpToPx(2), 0, 0)
            }
            addView(dateText)
            
            // Click to load
            setOnClickListener {
                if (presetManager.loadPreset(preset.id)) {
                    android.widget.Toast.makeText(
                        context,
                        "Loaded: ${preset.name}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    
                    // Notify GameBar to refresh
                    GameBar.getInstance(context).applyPreferences()
                    
                    hide()
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Failed to load preset",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun showNoPresetsMessage() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
            gravity = Gravity.CENTER
            
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#E0000000"))
                cornerRadius = dpToPx(12).toFloat()
            }
            background = bg
        }
        
        val message = TextView(context).apply {
            text = "No saved presets\n\nCreate presets in\nGameBar Settings"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(16))
        }
        container.addView(message)
        
        val closeButton = TextView(context).apply {
            text = "Close"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#4CAF50"))
            gravity = Gravity.CENTER
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            setOnClickListener {
                hide()
            }
        }
        container.addView(closeButton)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        
        overlayView = container
        windowManager.addView(container, params)
        isShowing = true
        
        // Auto-hide after 3 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            hide()
        }, 3000)
    }
    
    fun hide() {
        if (isShowing && overlayView != null) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                // View already removed
            }
            overlayView = null
            isShowing = false
        }
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    companion object {
        @Volatile
        private var instance: PresetQuickLoaderOverlay? = null
        
        fun getInstance(context: Context): PresetQuickLoaderOverlay {
            return instance ?: synchronized(this) {
                instance ?: PresetQuickLoaderOverlay(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
