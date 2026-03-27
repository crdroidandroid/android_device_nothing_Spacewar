/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.util.Locale

object GameBarBatteryInfo {

    fun getBatteryLevelPercent(context: Context): String {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return "N/A"
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return "N/A"
            val percent = (level * 100f / scale).toInt().coerceIn(0, 100)
            percent.toString()
        } catch (_: Exception) {
            "N/A"
        }
    }

    fun getBatteryPowerWatt(context: Context): String {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return "N/A"
            val batteryManager = context.getSystemService(BatteryManager::class.java)
                ?: return "N/A"
            val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            if (currentUa == Int.MIN_VALUE || currentUa == 0 || voltageMv <= 0) return "N/A"
            val currentA = kotlin.math.abs(currentUa) / 1_000_000.0
            val voltageV = voltageMv / 1000.0
            val watt = currentA * voltageV
            if (watt <= 0.0 || watt.isNaN() || watt.isInfinite()) return "N/A"
            String.format(Locale.getDefault(), "%.3f", watt)
        } catch (_: Exception) {
            "N/A"
        }
    }

    fun getBatteryTempC(context: Context): String {
        // Primary source: battery broadcast temperature.
        try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                if (tempTenths != Int.MIN_VALUE) {
                    val celsius = tempTenths / 10f
                    if (celsius in -30f..120f) {
                        return String.format(Locale.getDefault(), "%.1f", celsius)
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback below
        }

        // Fallback source: sysfs temperature path.
        return try {
            val (path, divider) = GameBarConfig.getBatteryTempConfig()
            if (path == null) return "N/A"
            val rawLine = readLine(path) ?: return "N/A"
            val raw = rawLine.trim().toIntOrNull() ?: return "N/A"
            val celsius = raw / divider.toFloat()
            if (celsius in -30f..120f) {
                String.format(Locale.getDefault(), "%.1f", celsius)
            } else {
                "N/A"
            }
        } catch (_: Exception) {
            "N/A"
        }
    }

    private fun readLine(path: String): String? {
        return try {
            BufferedReader(FileReader(path)).use { it.readLine() }
        } catch (_: IOException) {
            null
        }
    }
}
