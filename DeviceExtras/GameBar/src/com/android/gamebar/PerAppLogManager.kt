/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PerAppLogManager private constructor() {

    interface PerAppStateListener {
        fun onAppLoggingStarted(packageName: String)
        fun onAppLoggingStopped(packageName: String)
    }

    companion object {
        @Volatile
        private var INSTANCE: PerAppLogManager? = null
        private const val TAG = "PerAppLogManager"
        private const val PREF_PER_APP_ENABLED_APPS = "per_app_logging_enabled_apps"
        private const val MAX_ROWS_PER_APP = 5000

        @JvmStatic
        @Synchronized
        fun getInstance(): PerAppLogManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PerAppLogManager().also { INSTANCE = it }
            }
        }

        private val CSV_HEADER = arrayOf(
            "DateTime",
            "PackageName", 
            "FPS",
            "Frame_Time",
            "Battery_Temp",
            "CPU_Usage",
            "CPU_Clock",
            "CPU_Temp",
            "RAM_Usage",
            "RAM_Speed",
            "RAM_Temp",
            "GPU_Usage",
            "GPU_Clock",
            "GPU_Temp",
            "Battery_Level",
            "Power_W"
        )
    }

    private val activeLogSessions = ConcurrentHashMap<String, MutableList<Array<String>>>()
    private val activeLogResolutions = ConcurrentHashMap<String, String>()
    private val handler = Handler(Looper.getMainLooper())
    private var listener: PerAppStateListener? = null
    private var currentForegroundApp: String? = null

    fun setPerAppStateListener(listener: PerAppStateListener?) {
        this.listener = listener
    }

    fun getEnabledApps(context: Context): Set<String> {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(PREF_PER_APP_ENABLED_APPS, emptySet()) ?: emptySet()
    }

    fun setAppLoggingEnabled(context: Context, packageName: String, enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val current = prefs.getStringSet(PREF_PER_APP_ENABLED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
        
        if (enabled) {
            current.add(packageName)
        } else {
            current.remove(packageName)
            // Stop logging for this app if it's currently active
            stopLoggingForApp(packageName)
        }
        
        prefs.edit().putStringSet(PREF_PER_APP_ENABLED_APPS, current).apply()
    }

    fun isAppLoggingEnabled(context: Context, packageName: String): Boolean {
        return getEnabledApps(context).contains(packageName)
    }

    fun onAppBecameForeground(context: Context, packageName: String) {
        if (currentForegroundApp == packageName) return
        
        // Stop logging for previous app if it was being logged
        currentForegroundApp?.let { previousApp ->
            if (isAppLoggingActive(previousApp)) {
                stopLoggingForApp(previousApp)
            }
        }

        currentForegroundApp = packageName
        
        // Start logging for new app if enabled AND per-app logging mode is active
        // Note: Per-app logging works independently of GameBar overlay visibility
        val gameDataExport = GameDataExport.getInstance()
        if (isAppLoggingEnabled(context, packageName) && 
            gameDataExport.getLoggingMode() == GameDataExport.LoggingMode.PER_APP &&
            gameDataExport.isCapturing()) {
            startLoggingForApp(packageName)
        }
    }

    fun onAppWentToBackground(context: Context, packageName: String) {
        if (currentForegroundApp == packageName) {
            currentForegroundApp = null
            if (isAppLoggingActive(packageName)) {
                stopLoggingForApp(packageName)
            }
        }
    }

    private fun startLoggingForApp(packageName: String) {
        if (activeLogSessions.containsKey(packageName)) return

        // Check if GameBar overlay is actually showing
        val isGameBarShowing = GameBar.isShowing()
        
        if (!isGameBarShowing) {
            // GameBar overlay is OFF - cannot collect data
            Log.w(TAG, "Cannot start logging for $packageName - GameBar overlay is OFF")
            handler.post {
                try {
                    val context = android.app.ActivityThread.currentApplication()
                    context?.let {
                        val pm = it.packageManager
                        try {
                            val appInfo = pm.getApplicationInfo(packageName, 0)
                            val appName = pm.getApplicationLabel(appInfo).toString()
                            android.widget.Toast.makeText(it, 
                                "$appName: GameBar logging enabled but GameBar overlay is OFF. Turn ON GameBar to collect logs.", 
                                android.widget.Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(it, 
                                "GameBar logging enabled but GameBar overlay is OFF. Turn ON GameBar to collect logs.", 
                                android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to show warning toast for $packageName", e)
                }
            }
            return
        }

        Log.d(TAG, "Starting logging for app: $packageName")
        val logData = mutableListOf<Array<String>>()
        logData.add(CSV_HEADER)
        activeLogSessions[packageName] = logData
        
        Thread {
            try {
                val context = android.app.ActivityThread.currentApplication()
                if (context != null) {
                    val res = GameResolutionDetector.detectGameRenderResolution(context, packageName)
                    activeLogResolutions[packageName] = res
                } else {
                    activeLogResolutions[packageName] = "Unknown"
                }
            } catch (e: Exception) {
                activeLogResolutions[packageName] = "Unknown"
            }
        }.start()
        
        listener?.onAppLoggingStarted(packageName)
        
        // Show toast notification
        handler.post {
            try {
                val context = android.app.ActivityThread.currentApplication()
                context?.let {
                    val pm = it.packageManager
                    try {
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        android.widget.Toast.makeText(it, "$appName GameBar log started", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(it, "$packageName GameBar log started", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to show start toast for $packageName", e)
            }
        }
    }

    private fun stopLoggingForApp(packageName: String) {
        val logData = activeLogSessions.remove(packageName)
        if (logData != null && logData.size > 1) {
            Log.d(TAG, "Stopping logging for app: $packageName")
            exportPerAppDataToCsv(packageName, logData)
            listener?.onAppLoggingStopped(packageName)
            
            // Show toast notification
            handler.post {
                try {
                    val context = android.app.ActivityThread.currentApplication()
                    context?.let {
                        val pm = it.packageManager
                        try {
                            val appInfo = pm.getApplicationInfo(packageName, 0)
                            val appName = pm.getApplicationLabel(appInfo).toString()
                            android.widget.Toast.makeText(it, "$appName GameBar log ended", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(it, "$packageName GameBar log ended", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to show stop toast for $packageName", e)
                }
            }
        }
    }

    fun isAppLoggingActive(packageName: String): Boolean {
        return activeLogSessions.containsKey(packageName)
    }

    fun addPerAppData(
        packageName: String,
        dateTime: String,
        fps: String,
        frameTime: String,
        batteryTemp: String,
        cpuUsage: String,
        cpuClock: String,
        cpuTemp: String,
        ramUsage: String,
        ramSpeed: String,
        ramTemp: String,
        gpuUsage: String,
        gpuClock: String,
        gpuTemp: String,
        batteryLevel: String,
        powerWatt: String
    ) {
        val logData = activeLogSessions[packageName] ?: return

        // Prevent unlimited memory growth
        if (logData.size >= MAX_ROWS_PER_APP) {
            // Remove oldest entries but keep header
            if (logData.size > 1) {
                val toRemove = logData.size / 2
                repeat(toRemove) {
                    if (logData.size > 1) {
                        logData.removeAt(1)
                    }
                }
            }
        }

        val row = arrayOf(
            dateTime,
            packageName,
            fps,
            frameTime,
            batteryTemp,
            cpuUsage,
            cpuClock,
            cpuTemp,
            ramUsage,
            ramSpeed,
            ramTemp,
            gpuUsage,
            gpuClock,
            gpuTemp,
            batteryLevel,
            powerWatt
        )
        logData.add(row)
    }

    private fun exportPerAppDataToCsv(packageName: String, logData: List<Array<String>>) {
        if (logData.size <= 1) return

        val resolution = activeLogResolutions.remove(packageName) ?: "Unknown"
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${packageName}_GameBar_log_${resolution}_$timeStamp.csv"
        val outFile = File(getLogsDirectory(), fileName)

        var bw: BufferedWriter? = null
        try {
            bw = BufferedWriter(FileWriter(outFile, false))
            for (row in logData) {
                bw.write(toCsvLine(row))
                bw.newLine()
            }
            bw.flush()
            Log.d(TAG, "Exported per-app log for $packageName to ${outFile.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to export per-app log for $packageName", e)
        } finally {
            bw?.let {
                try { it.close() } catch (ignored: IOException) {}
            }
        }
    }

    private fun toCsvLine(columns: Array<String>): String {
        return columns.joinToString(",")
    }

    fun getPerAppLogFiles(packageName: String): List<File> {
        val files = getLogsDirectory().listFiles { file ->
            file.name.startsWith("${packageName}_GameBar_log_") && file.name.endsWith(".csv")
        }
        return files?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun getAllPerAppLogFiles(): Map<String, List<File>> {
        val files = getLogsDirectory().listFiles { file ->
            file.name.contains("_GameBar_log_") && 
            file.name.endsWith(".csv") && 
            !file.name.startsWith("GameBar_log_") // Exclude global logs
        } ?: emptyArray()

        return files.groupBy { file ->
            // Extract package name from filename: packageName_GameBar_log_timestamp.csv
            val fileName = file.name
            val endIndex = fileName.indexOf("_GameBar_log_")
            if (endIndex > 0) fileName.substring(0, endIndex) else "unknown"
        }.mapValues { (_, fileList) ->
            fileList.sortedByDescending { it.lastModified() }
        }
    }

    fun getCurrentlyLoggingApps(): Set<String> {
        return activeLogSessions.keys.toSet()
    }

    private fun getLogsDirectory(): File {
        val context = android.app.ActivityThread.currentApplication()
        val baseDir = context?.filesDir ?: File(".")
        val dir = File(baseDir, "fps_records")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Start manual logging for an app via double-tap (even if not in enabled apps list)
     */
    fun startManualLoggingForApp(packageName: String) {
        if (activeLogSessions.containsKey(packageName)) {
            // Already logging, ignore
            return
        }
        
        // Check if GameBar overlay is showing
        val isGameBarShowing = GameBar.isShowing()
        if (!isGameBarShowing) {
            handler.post {
                try {
                    val context = android.app.ActivityThread.currentApplication()
                    context?.let {
                        android.widget.Toast.makeText(it, 
                            "Cannot start logging - GameBar overlay is OFF", 
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to show toast", e)
                }
            }
            return
        }
        
        Log.d(TAG, "Starting manual logging for app: $packageName")
        val logData = mutableListOf<Array<String>>()
        logData.add(CSV_HEADER)
        activeLogSessions[packageName] = logData
        
        Thread {
            try {
                val context = android.app.ActivityThread.currentApplication()
                if (context != null) {
                    val res = GameResolutionDetector.detectGameRenderResolution(context, packageName)
                    activeLogResolutions[packageName] = res
                } else {
                    activeLogResolutions[packageName] = "Unknown"
                }
            } catch (e: Exception) {
                activeLogResolutions[packageName] = "Unknown"
            }
        }.start()
        
        listener?.onAppLoggingStarted(packageName)
        
        // Show toast notification
        handler.post {
            try {
                val context = android.app.ActivityThread.currentApplication()
                context?.let {
                    val pm = it.packageManager
                    try {
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        android.widget.Toast.makeText(it, it.getString(R.string.toast_manual_logging_started, appName), android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(it, it.getString(R.string.toast_manual_logging_started_pkg, packageName), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to show start toast for $packageName", e)
            }
        }
    }
    
    /**
     * Stop manual logging for an app via double-tap
     */
    fun stopManualLoggingForApp(packageName: String) {
        stopLoggingForApp(packageName)
    }

    fun stopAllPerAppLogging() {
        val appsToStop = activeLogSessions.keys.toList()
        appsToStop.forEach { packageName ->
            stopLoggingForApp(packageName)
        }
    }
    
    fun handlePerAppLoggingStarted(context: Context) {
        // When per-app logging is started, check if current foreground app should be logged
        val foregroundApp = ForegroundAppDetector.getForegroundPackageName(context)
        if (foregroundApp != "Unknown" && foregroundApp.isNotEmpty()) {
            currentForegroundApp = foregroundApp
            if (isAppLoggingEnabled(context, foregroundApp)) {
                startLoggingForApp(foregroundApp)
            }
        }
    }
}
