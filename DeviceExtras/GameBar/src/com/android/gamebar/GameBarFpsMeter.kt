/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.view.WindowManager
import android.window.TaskFpsCallback
import androidx.preference.PreferenceManager
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.lang.reflect.Field
import java.lang.reflect.Method

class GameBarFpsMeter private constructor(context: Context) {

    companion object {
        private const val TOLERANCE = 0.1f
        private const val STALENESS_THRESHOLD_MS = 2000L
        private const val TASK_CHECK_INTERVAL_MS = 1000L
        
        @Volatile
        private var INSTANCE: GameBarFpsMeter? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): GameBarFpsMeter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GameBarFpsMeter(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val context: Context = context.applicationContext
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var currentFps = 0f
    private var taskFpsCallback: TaskFpsCallback? = null
    private var callbackRegistered = false
    private var currentTaskId = -1
    private var lastFpsUpdateTime = System.currentTimeMillis()
    private val handler = Handler()
    
    // FPS history for percentile calculations
    private val fpsHistory = mutableListOf<Float>()
    private val maxHistorySize = 100

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            taskFpsCallback = object : TaskFpsCallback() {
                override fun onFpsReported(fps: Float) {
                    if (fps > 0) {
                        currentFps = fps
                        lastFpsUpdateTime = System.currentTimeMillis()
                        
                        synchronized(fpsHistory) {
                            fpsHistory.add(fps)
                            if (fpsHistory.size > maxHistorySize) {
                                fpsHistory.removeAt(0)
                            }
                        }
                    }
                }
            }
        }
    }

    fun start() {
        val method = prefs.getString("game_bar_fps_method", "new")
        if (method != "new") return

        stop()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val taskId = getFocusedTaskId()
            if (taskId <= 0) {
                return
            }
            currentTaskId = taskId
            try {
                taskFpsCallback?.let {
                    windowManager.registerTaskFpsCallback(currentTaskId, Runnable::run, it)
                    callbackRegistered = true
                }
            } catch (e: Exception) {
                // Ignore registration errors
            }
            lastFpsUpdateTime = System.currentTimeMillis()
            handler.postDelayed(taskCheckRunnable, TASK_CHECK_INTERVAL_MS)
        }
    }

    fun stop() {
        val method = prefs.getString("game_bar_fps_method", "new")
        if (method == "new" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (callbackRegistered) {
                try {
                    taskFpsCallback?.let {
                        windowManager.unregisterTaskFpsCallback(it)
                    }
                } catch (e: Exception) {
                    // Ignore unregistration errors
                }
                callbackRegistered = false
            }
            handler.removeCallbacks(taskCheckRunnable)
        }
    }

    fun getFps(): Float {
        val method = prefs.getString("game_bar_fps_method", "new")
        val fps = if (method == "legacy") {
            readLegacyFps()
        } else {
            currentFps
        }
        
        // Add to history for legacy method
        if (method == "legacy" && fps > 0) {
            synchronized(fpsHistory) {
                fpsHistory.add(fps)
                if (fpsHistory.size > maxHistorySize) {
                    fpsHistory.removeAt(0)
                }
            }
        }
        
        return fps
    }
    
    /**
     * Updates dynamically every frame after initial 100 samples
     */
    fun get1PercentLowFps(): Float {
        synchronized(fpsHistory) {
            if (fpsHistory.size < 100) return -1f
            
            val sorted = fpsHistory.sorted()
            val index = (sorted.size * 0.01).toInt().coerceAtLeast(0)
            return sorted[index]
        }
    }
    
    /**
     * Updates dynamically every frame after initial 100 samples
     */
    fun get01PercentLowFps(): Float {
        synchronized(fpsHistory) {
            if (fpsHistory.size < 100) return -1f
            
            val sorted = fpsHistory.sorted()
            val index = (sorted.size * 0.001).toInt().coerceAtLeast(0)
            return sorted[index]
        }
    }
    
    /**
     * Clear FPS history
     */
    fun clearHistory() {
        synchronized(fpsHistory) {
            fpsHistory.clear()
        }
    }

    private fun readLegacyFps(): Float {
        try {
            BufferedReader(FileReader(GameBarConfig.fpsSysfsPath)).use { br ->
                val line = br.readLine()
                if (line != null && line.startsWith("fps:")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        return parts[1].trim().toFloat()
                    }
                }
            }
        } catch (e: IOException) {
            // Ignore errors
        } catch (e: NumberFormatException) {
            // Ignore errors
        }
        return -1f
    }

    private val taskCheckRunnable = object : Runnable {
        override fun run() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val newTaskId = getFocusedTaskId()
                if (newTaskId > 0 && newTaskId != currentTaskId) {
                    reinitCallback()
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastFpsUpdateTime > STALENESS_THRESHOLD_MS) {
                        reinitCallback()
                    }
                }
                handler.postDelayed(this, TASK_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun getFocusedTaskId(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return -1
        }
        try {
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val getServiceMethod = atmClass.getDeclaredMethod("getService")
            val atmService = getServiceMethod.invoke(null)
            val getFocusedRootTaskInfoMethod = atmService.javaClass.getMethod("getFocusedRootTaskInfo")
            val taskInfo = getFocusedRootTaskInfoMethod.invoke(atmService)
            if (taskInfo != null) {
                try {
                    val taskIdField = taskInfo.javaClass.getField("taskId")
                    return taskIdField.getInt(taskInfo)
                } catch (nsfe: NoSuchFieldException) {
                    try {
                        val taskIdField = taskInfo.javaClass.getField("mTaskId")
                        return taskIdField.getInt(taskInfo)
                    } catch (nsfe2: NoSuchFieldException) {
                        // Both field names failed
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore reflection errors
        }
        return -1
    }

    private fun reinitCallback() {
        stop()
        handler.postDelayed({
            start()
        }, 500)
    }
}
