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
import android.widget.Toast
import android.content.pm.PackageManager
import android.util.Log

/**
 * Dedicated service for per-app logging that runs independently of GameBar display logic.
 * This service only monitors foreground app changes for logging purposes and doesn't 
 * interfere with GameBar functionality.
 */
class PerAppLogService : Service() {

    private var handler: Handler? = null
    private var monitorRunnable: Runnable? = null
    @Volatile
    private var isRunning = false

    private var lastForegroundApp = ""
    private val perAppLogManager = PerAppLogManager.getInstance()

    companion object {
        private const val TAG = "PerAppLogService"
        private const val MONITOR_INTERVAL = 1000L // 1 second for responsive logging
    }

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        monitorRunnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    monitorForPerAppLogging()
                    if (isRunning && handler != null) {
                        handler!!.postDelayed(this, MONITOR_INTERVAL)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            Log.d(TAG, "Starting per-app logging service")
            isRunning = true
            handler?.let { h ->
                monitorRunnable?.let { r ->
                    h.post(r)
                }
            }
        }
        return START_STICKY
    }

    private fun monitorForPerAppLogging() {
        try {
            if (!isRunning) return

            // Only run if per-app logging is active
            val gameDataExport = GameDataExport.getInstance()
            if (gameDataExport.getLoggingMode() != GameDataExport.LoggingMode.PER_APP || 
                !gameDataExport.isCapturing()) {
                // Per-app logging is not active, stop service
                Log.d(TAG, "Per-app logging not active, stopping service")
                stopSelf()
                return
            }

            val foreground = ForegroundAppDetector.getForegroundPackageName(this)

            // Only process if foreground app changed
            if (foreground != lastForegroundApp) {
                Log.d(TAG, "Foreground app changed from $lastForegroundApp to $foreground")

                // Handle previous app
                if (lastForegroundApp.isNotEmpty() && lastForegroundApp != "Unknown") {
                    if (perAppLogManager.isAppLoggingActive(lastForegroundApp)) {
                        Log.d(TAG, "Stopping logging for $lastForegroundApp")
                        perAppLogManager.onAppWentToBackground(this, lastForegroundApp)
                    }
                }

                // Handle new app
                if (foreground.isNotEmpty() && foreground != "Unknown") {
                    if (perAppLogManager.isAppLoggingEnabled(this, foreground)) {
                        Log.d(TAG, "Starting logging for $foreground")
                        perAppLogManager.onAppBecameForeground(this, foreground)
                    }
                }

                lastForegroundApp = foreground
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in monitorForPerAppLogging", e)
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName // Fallback to package name
        }
    }

    private fun showToast(message: String) {
        handler?.post {
            try {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to show toast: $message", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Stopping per-app logging service")
        isRunning = false

        handler?.let {
            monitorRunnable?.let { runnable ->
                it.removeCallbacks(runnable)
            }
            it.removeCallbacksAndMessages(null)
        }

        // Stop all active per-app logging sessions
        perAppLogManager.stopAllPerAppLogging()

        // Clear state variables
        lastForegroundApp = ""
        handler = null
        monitorRunnable = null
    }
}
