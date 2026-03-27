/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.preference.PreferenceManager

class GameBarMonitorService : Service() {

    private var handler: Handler? = null
    private var monitorRunnable: Runnable? = null
    @Volatile
    private var isRunning = false

    private var lastForegroundApp = ""
    private var lastGameBarState = false
    private val perAppLogManager = PerAppLogManager.getInstance()

    companion object {
        private const val MONITOR_INTERVAL = 2000L // 2 seconds
    }

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        monitorRunnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    monitorForegroundApp()
                    if (isRunning && handler != null) {
                        handler!!.postDelayed(this, MONITOR_INTERVAL)
                    }
                }
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            handler?.let { h ->
                monitorRunnable?.let { r ->
                    h.post(r)
                }
            }
        }
        return START_STICKY
    }
    
    private fun monitorForegroundApp() {
        try {
            if (!isRunning) return
            
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val masterEnabled = prefs.getBoolean("game_bar_enable", false)
            
            if (masterEnabled) {
                if (!lastGameBarState) {
                    val gameBar = GameBar.getInstance(this)
                    gameBar.applyPreferences()
                    gameBar.show()
                    lastGameBarState = true
                }
                return
            }
            
            val autoEnabled = prefs.getBoolean("game_bar_auto_enable", false)
            if (!autoEnabled) {
                if (lastGameBarState) {
                    GameBar.getInstance(this).hide()
                    lastGameBarState = false
                }
                return
            }
            
            val foreground = ForegroundAppDetector.getForegroundPackageName(this)
            
            // Only update if foreground app changed
            if (foreground != lastForegroundApp) {
                val autoApps = prefs.getStringSet(
                    GameBarPerAppConfigFragment.PREF_AUTO_APPS, 
                    emptySet()
                ) ?: emptySet()
                    
                val shouldShow = autoApps.contains(foreground)
                
                if (shouldShow && !lastGameBarState) {
                    val gameBar = GameBar.getInstance(this)
                    gameBar.applyPreferences()
                    gameBar.show()
                    lastGameBarState = true
                } else if (!shouldShow && lastGameBarState) {
                    GameBar.getInstance(this).hide()
                    lastGameBarState = false
                }
                
                // Handle per-app logging state changes
                if (lastForegroundApp.isNotEmpty() && lastForegroundApp != "Unknown") {
                    perAppLogManager.onAppWentToBackground(this, lastForegroundApp)
                }
                
                if (foreground.isNotEmpty() && foreground != "Unknown") {
                    perAppLogManager.onAppBecameForeground(this, foreground)
                }
                
                lastForegroundApp = foreground
            }
        } catch (e: Exception) {
            // Prevent crashes from propagating
            android.util.Log.e("GameBarMonitorService", "Error in monitorForegroundApp", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        
        handler?.let {
            monitorRunnable?.let { runnable ->
                it.removeCallbacks(runnable)
            }
            it.removeCallbacksAndMessages(null)
        }
        
        // Clean up GameBar instance
        try {
            GameBar.destroyInstance()
        } catch (e: Exception) {
            android.util.Log.e("GameBarMonitorService", "Error destroying GameBar instance", e)
        }
        
        // Clear state variables to prevent lingering references
        lastForegroundApp = ""
        lastGameBarState = false
        handler = null
        monitorRunnable = null
    }
}
