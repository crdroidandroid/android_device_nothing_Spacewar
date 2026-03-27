/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import android.os.Environment
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class GameDataExport private constructor() {

    interface CaptureStateListener {
        fun onCaptureStarted()
        fun onCaptureStopped()
    }

    enum class LoggingMode {
        GLOBAL, PER_APP
    }

    companion object {
        @Volatile
        private var INSTANCE: GameDataExport? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): GameDataExport {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GameDataExport().also { INSTANCE = it }
            }
        }
        
        private const val MAX_ROWS = 10000 // Prevent unlimited memory growth
        
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

    @Volatile
    private var capturing = false
    private var currentLoggingMode = LoggingMode.PER_APP // Default to per-app logging

    private val statsRows = mutableListOf<Array<String>>()
    private var listener: CaptureStateListener? = null
    private val perAppLogManager = PerAppLogManager.getInstance()
    
    fun setCaptureStateListener(listener: CaptureStateListener?) {
        this.listener = listener
    }

    fun startCapture() {
        capturing = true
        if (currentLoggingMode == LoggingMode.GLOBAL) {
            statsRows.clear()
            statsRows.add(CSV_HEADER)
        }
        listener?.onCaptureStarted()
    }

    fun stopCapture() {
        capturing = false
        if (currentLoggingMode == LoggingMode.PER_APP) {
            perAppLogManager.stopAllPerAppLogging()
        }
        listener?.onCaptureStopped()
    }
    
    fun clearData() {
        statsRows.clear()
        statsRows.add(CSV_HEADER)
    }
    
    fun getDataSize(): Int {
        return statsRows.size
    }

    fun isCapturing(): Boolean {
        return capturing
    }

    fun addOverlayData(
        dateTime: String,
        packageName: String,
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
        if (!capturing) return

        when (currentLoggingMode) {
            LoggingMode.GLOBAL -> {
                // Prevent unlimited memory growth
                if (statsRows.size >= MAX_ROWS) {
                    // Remove oldest entries but keep header
                    if (statsRows.size > 1) {
                        val toRemove = statsRows.size / 2
                        repeat(toRemove) {
                            if (statsRows.size > 1) {
                                statsRows.removeAt(1)
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
                statsRows.add(row)
            }
            LoggingMode.PER_APP -> {
                // Add data to per-app logging system
                perAppLogManager.addPerAppData(
                    packageName,
                    dateTime,
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
            }
        }
    }

    fun exportDataToCsv() {
        if (currentLoggingMode == LoggingMode.GLOBAL) {
            if (statsRows.size <= 1) {
                return
            }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val outFile = File(Environment.getExternalStorageDirectory(), "GameBar_log_$timeStamp.csv")

            var bw: BufferedWriter? = null
            try {
                bw = BufferedWriter(FileWriter(outFile, true))
                for (row in statsRows) {
                    bw.write(toCsvLine(row))
                    bw.newLine()
                }
                bw.flush()
            } catch (ignored: IOException) {
            } finally {
                bw?.let {
                    try { it.close() } catch (ignored: IOException) {}
                }
            }
        }
        // Per-app logs are exported automatically when sessions end
    }

    private fun toCsvLine(columns: Array<String>): String {
        return columns.joinToString(",")
    }

    fun setLoggingMode(mode: LoggingMode) {
        currentLoggingMode = mode
    }

    fun getLoggingMode(): LoggingMode {
        return currentLoggingMode
    }

    fun getPerAppLogManager(): PerAppLogManager {
        return perAppLogManager
    }
}
