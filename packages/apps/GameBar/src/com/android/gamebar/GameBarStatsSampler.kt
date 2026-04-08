/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.content.Context
import androidx.preference.PreferenceManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GameBarStatsSampler {

    data class Snapshot(
        val dateTime: String,
        val packageName: String,
        val fps: String,
        val frameTime: String,
        val batteryTemp: String,
        val cpuUsage: String,
        val cpuClock: String,
        val cpuTemp: String,
        val ramUsage: String,
        val ramSpeed: String,
        val ramTemp: String,
        val gpuUsage: String,
        val gpuClock: String,
        val gpuTemp: String,
        val batteryLevel: String,
        val powerWatt: String,
        val appRamUsage: String
    )

    fun capture(context: Context, packageName: String): Snapshot {
        val fpsValue = GameBarFpsMeter.getInstance(context).getFps()
        val fps = if (fpsValue >= 0f) String.format(Locale.getDefault(), "%.0f", fpsValue) else "N/A"
        val frameTime = if (fpsValue > 0f) {
            String.format(Locale.getDefault(), "%.2f", 1000.0 / fpsValue)
        } else {
            "N/A"
        }

        val cpuClock = GameBarCpuInfo.getCpuFrequencies().joinToString("; ").ifBlank { "N/A" }
        val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        return Snapshot(
            dateTime = dateTime,
            packageName = packageName,
            fps = fps,
            frameTime = frameTime,
            batteryTemp = GameBarBatteryInfo.getBatteryTempC(context),
            cpuUsage = GameBarCpuInfo.getCpuUsage(),
            cpuClock = cpuClock,
            cpuTemp = GameBarCpuInfo.getCpuTemp(),
            ramUsage = GameBarMemInfo.getRamUsage(),
            ramSpeed = GameBarMemInfo.getRamSpeed(),
            ramTemp = GameBarMemInfo.getRamTemp(),
            gpuUsage = GameBarGpuInfo.getGpuUsage(),
            gpuClock = GameBarGpuInfo.getGpuClock(),
            gpuTemp = GameBarGpuInfo.getGpuTemp(),
            batteryLevel = GameBarBatteryInfo.getBatteryLevelPercent(context),
            powerWatt = GameBarBatteryInfo.getBatteryPowerWatt(context),
            appRamUsage = GameBarMemInfo.getAppRamUsage(context, packageName)
        )
    }

    fun addToExport(context: Context, snapshot: Snapshot) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        GameDataExport.getInstance().addOverlayData(
            snapshot.dateTime,
            snapshot.packageName,
            if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_FPS, true)) snapshot.fps else "N/A",
            if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_FRAME_TIME, true)) snapshot.frameTime else "N/A",
            if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_BATTERY_TEMP, true)) snapshot.batteryTemp else "N/A",
            if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_CPU_USAGE, true)) snapshot.cpuUsage else "N/A",
            if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_CPU_CLOCK, true)) snapshot.cpuClock else "N/A",
            if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_CPU_TEMP, true)) snapshot.cpuTemp else "N/A",
            if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_RAM, true)) snapshot.ramUsage else "N/A",
            if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_RAM_SPEED, true)) snapshot.ramSpeed else "N/A",
            if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_RAM_TEMP, true)) snapshot.ramTemp else "N/A",
            if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_GPU_USAGE, true)) snapshot.gpuUsage else "N/A",
            if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_GPU_CLOCK, true)) snapshot.gpuClock else "N/A",
            if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_GPU_TEMP, true)) snapshot.gpuTemp else "N/A",
            snapshot.batteryLevel,
            snapshot.powerWatt,
            if (prefs.getBoolean(GameBarLoggingPrefs.PREF_LOG_RAM, true)) snapshot.appRamUsage else "N/A"
        )
    }
}

