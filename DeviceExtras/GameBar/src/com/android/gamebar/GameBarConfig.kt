/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.content.Context
import com.android.gamebar.utils.SysfsDetector

/**
 * Centralized configuration for GameBar hardware paths and conversion factors.
 * All values are loaded from resources to support device-specific overlays.
 */
object GameBarConfig {
    
    private lateinit var context: Context

    // Cache for detected paths
    private val detectedPaths = mutableMapOf<String, String?>()

    // cache for detected dividers
    private val detectedDividers = mutableMapOf<String, Int>()

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }
    
    // FPS paths
    val fpsSysfsPath: String?
        get() = SysfsDetector.getFpsPath()
    
    // Battery configuration
    val batteryTempPath: String?
        get() = SysfsDetector.getBatteryTempInfo().first
    val batteryTempDivider: Int
        get() = SysfsDetector.getBatteryTempInfo().second

    fun getBatteryTempConfig(): Pair<String?, Int> {
        return SysfsDetector.getBatteryTempInfo()
    }

    
    // CPU configuration - now dynamic
    val cpuBasePath: String?
        get() = SysfsDetector.getCpuBasePath()
    
    val cpuTempPath: String?
        get() = SysfsDetector.getCpuTempInfo().first
    val cpuTempDivider: Int
        get() = SysfsDetector.getCpuTempInfo().second

    fun getCpuTempConfig(): Pair<String?, Int> {
        return SysfsDetector.getCpuTempInfo()
    }
    
    // GPU configuration
    val gpuUsagePath: String
        get() = context.getString(R.string.config_gpu_usage_path)
    val gpuClockPath: String
        get() = context.getString(R.string.config_gpu_clock_path)
    val gpuTempPath: String
        get() = context.getString(R.string.config_gpu_temp_path)
    val gpuTempDivider: Int
        get() = context.resources.getInteger(R.integer.config_gpu_temp_divider)
    val gpuClockDivider: Int
        get() = context.resources.getInteger(R.integer.config_gpu_clock_divider)
    
    // RAM configuration
    val ramFreqPath: String
        get() = context.getString(R.string.config_ram_freq_path)
    val ramTempPath: String
        get() = context.getString(R.string.config_ram_temp_path)
    val ramTempDivider: Int
        get() = context.resources.getInteger(R.integer.config_ram_temp_divider)
    
    // Proc filesystem paths
    val procStatPath: String
        get() = context.getString(R.string.config_proc_stat_path)
    val procMeminfoPath: String
        get() = context.getString(R.string.config_proc_meminfo_path)


    /**
     * Utility function to check if all essential components are available
     */
    fun isSystemSupported(): Boolean {
        return listOf(
            SysfsDetector.getBatteryTempPath(),
            SysfsDetector.getFpsPath(),
            SysfsDetector.getCpuTempPath()
        ).any { it != null }
    }

}
