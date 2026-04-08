/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.lang.reflect.Method

object ForegroundAppDetector {

    private const val TAG = "ForegroundAppDetector"
    private var lastKnownPackage = "Unknown"
    private var lastUpdateTime = 0L
    private const val CACHE_TIMEOUT = 500L // Reduce cache timeout
    
    // Simple reflection caching
    private var reflectionSetupFailed = false

    fun getForegroundPackageName(context: Context): String {
        // Use cached result if still valid
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < CACHE_TIMEOUT && lastKnownPackage != "Unknown") {
            return lastKnownPackage
        }

        val pkg = tryGetRunningTasks(context)
        if (pkg != null) {
            lastKnownPackage = pkg
            lastUpdateTime = currentTime
            return pkg
        }
        
        if (!reflectionSetupFailed) {
            val reflectionPkg = tryReflectActivityTaskManager()
            if (reflectionPkg != null) {
                lastKnownPackage = reflectionPkg
                lastUpdateTime = currentTime
                return reflectionPkg
            }
        }
        
        // Return cached value if available, otherwise "Unknown"
        return lastKnownPackage
    }

    private fun tryGetRunningTasks(context: Context): String? {
        try {
            if (context.checkSelfPermission("android.permission.GET_TASKS") == PackageManager.PERMISSION_GRANTED) {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val tasks = am.getRunningTasks(1)
                if (tasks.isNotEmpty()) {
                    val top = tasks[0]
                    top.topActivity?.let {
                        return it.packageName
                    }
                }
            } else {
                Log.w(TAG, "GET_TASKS permission not granted to this system app?")
            }
        } catch (e: Exception) {
            Log.e(TAG, "tryGetRunningTasks error: ", e)
        }
        return null
    }

    private fun tryReflectActivityTaskManager(): String? {
        try {
            if (reflectionSetupFailed) {
                return null
            }
            
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val getServiceMethod = atmClass.getDeclaredMethod("getService")
            getServiceMethod.isAccessible = true
            val atmService = getServiceMethod.invoke(null)
            val getTasksMethod = atmService.javaClass.getMethod("getTasks", Int::class.java)
            
            @Suppress("UNCHECKED_CAST")
            val taskList = getTasksMethod.invoke(atmService, 1) as? List<*>
            if (!taskList.isNullOrEmpty()) {
                val firstTask = taskList[0]
                val rtiClass = firstTask!!.javaClass
                val getTopActivityMethod = rtiClass.getDeclaredMethod("getTopActivity")
                val compName = getTopActivityMethod.invoke(firstTask)
                if (compName != null) {
                    val getPackageNameMethod = compName.javaClass.getMethod("getPackageName")
                    val pkgName = getPackageNameMethod.invoke(compName) as String
                    return pkgName
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "tryReflectActivityTaskManager error: ", e)
            reflectionSetupFailed = true // Disable reflection on error
        }
        return null
    }
}
