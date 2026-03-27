/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

import java.util.Locale

object GameBarGpuInfo {

    fun getGpuUsage(): String {
        val line = readLine(GameBarConfig.gpuUsagePath) ?: return "N/A"
        val cleanLine = line.replace("%", "").trim()
        return try {
            val value = cleanLine.toInt()
            value.toString()
        } catch (e: NumberFormatException) {
            "N/A"
        }
    }

    fun getGpuClock(): String {
        val line = readLine(GameBarConfig.gpuClockPath) ?: return "N/A"
        val cleanLine = line.trim()
        try {
            val hz = line.trim().toLong()
            val mhz = hz / GameBarConfig.gpuClockDivider
            return "$mhz".toString()
        } catch (e: NumberFormatException) {
            return "N/A"
        }
    }

    fun getGpuTemp(): String {
        val line = readLine(GameBarConfig.gpuTempPath) ?: return "N/A"
        val cleanLine = line.trim()
        try {
            val raw = line.trim().toInt()
            val celsius = raw / GameBarConfig.gpuTempDivider.toFloat()
            return String.format(Locale.getDefault(), "%.1f", celsius)
        } catch (e: NumberFormatException) {
            return "N/A"
        }
    }

    private fun readLine(path: String): String? {
        return try {
            BufferedReader(FileReader(path)).use { it.readLine() }
        } catch (e: IOException) {
            null
        }
    }
}
