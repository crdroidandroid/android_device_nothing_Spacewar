/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-FileCopyrightText: 2025 carlos 'klozz' jesus
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar.utils
import android.util.Log
import java.io.File

/**
 * Utility class to automatically detect sysfs nodes for hardware monitoring.
 * Eliminates the need for device-specific overlays.
 */
object SysfsDetector {
    private const val TAG = "SysfsDetector"
    
    // Cache for detected paths to avoid repeated filesystem scans
    private val detectedPaths = mutableMapOf<String, String?>()

    // Cache for detected dividers
    private val detectedDividers = mutableMapOf<String, Int>()

    /**
     * Automatically detects the temperature divider by reading the node value
     * and analyzing the raw temperature format (milli, deci, centi, or celsius)
     *
     * @param path The sysfs path to analyze
     * @return The appropriate divider value (1000, 100, 10, or 1)
     */
    private fun detectTemperatureDivider(path: String): Int {
        return try {
            val file = File(path)
            if (!file.exists() || !file.canRead()) return 1000 //by default

            val content = file.readText().trim()
            if (content.isEmpty()) return 1000

            val rawValue = content.toInt()

            // Detect format based on typical temperature values
            when {
                rawValue in 20000..50000 -> 1000 // Mili-celsius (example: 35000 = 35°C)
                rawValue in 2000..5000 -> 100    // Centi-celsius (example: 3500 = 35°c)
                rawValue in 200..500 -> 10       // Deci-celsius (example: 350 = 35°C)
                rawValue in 20..100 -> 1         //celcius
                else -> 1000 // by default use 1000
            }.also { divider ->
                Log.d("GameBarConfig:","Detected temperature divider for $path: $divider (raw value: $rawValue)")
            }

        } catch (e: Exception){
            Log.w(TAG, "Failed to detect temperature divider for $path, using default 1000")
            1000
        }
    }

    /**
     * Detects path and divider for battery temperature
     */
    fun getBatteryTempInfo(): Pair<String?, Int> {
        val path = getBatteryTempPath()
        if (path == null) return Pair(null, 1000)
        
        if (detectedDividers.containsKey("battery_temp")) {
            return Pair(path, detectedDividers["battery_temp"]!!)
        }
        
        // Detectar divisor automáticamente
        val divider = detectTemperatureDivider(path)
        detectedDividers["battery_temp"] = divider
        return Pair(path, divider)
    }

    /**
     * Dynamically searches for CPU temperature node with type-based prioritization
     * For cpuss-0, cpuss-1, etc., selects the first available one
     * 
     * @return The path to CPU temperature node or null if not found
     */
    private fun findCpuTempPath(): String? {
        // Return cached path if available
        detectedPaths["cpu_temp"]?.let { return it }

        val thermalBaseDirs = arrayOf(
            "/sys/class/thermal",
            "/sys/devices/virtual/thermal"
        )

        // Priority order for CPU sensor types (most specific to least specific)
        // add more on newer or older devices
        val priorityTypes = arrayOf(
            "cpu_therm",    // Highest priority - specific CPU thermal
            "cpuss",        // CPU subsystem
            "cpu",          // Generic CPU
            "cluster0"     // CPU clusters
        )

       // Scan all thermal base directories
        for (baseDirPath in thermalBaseDirs) {
            val baseDir = File(baseDirPath)
            if (!baseDir.exists() || !baseDir.isDirectory()) continue

            Log.d(TAG, "Scanning thermal directory: $baseDirPath")

            // First pass: search by priority type order
            for (priorityType in priorityTypes) {
                baseDir.listFiles()?.forEach { zone ->
                    val typeFile = File(zone, "type")
                    val tempFile = File(zone, "temp")

                    if (typeFile.exists() && tempFile.exists() && tempFile.canRead()) {
                        try {
                            val type = typeFile.readText().trim().lowercase()
                            
                            // For cpuss, accept any cpuss-* variant (cpuss-0, cpuss-1, etc.)
                            val isMatch = when {
                                priorityType == "cpuss" && type.startsWith("cpuss") -> true
                                else -> type == priorityType
                            }
                            
                            if (isMatch) {
                                detectedPaths["cpu_temp"] = tempFile.absolutePath
                                Log.d(TAG, "Found CPU temp (priority): ${tempFile.absolutePath} (type=$type)")
                                return tempFile.absolutePath
                            }
                        } catch (e: Exception) {
                            // Continue to next file on error
                        }
                    }
                }
            }

            // Second pass: search for any thermal zone that might be CPU-related
            baseDir.listFiles()?.forEach { zone ->
                val typeFile = File(zone, "type")
                val tempFile = File(zone, "temp")

                if (typeFile.exists() && tempFile.exists() && tempFile.canRead()) {
                    try {
                        val type = typeFile.readText().trim().lowercase()
                        
                        // Look for patterns indicating CPU sensor
                        if (type.contains("cpu") || 
                            type.contains("core") || 
                            type.contains("cluster") ||
                            type.contains("tsens") ||
                            type.startsWith("thermal")) {
                            
                            // Read value to verify it's a reasonable temperature
                            val tempValue = tempFile.readText().trim().toIntOrNull()
                            if (tempValue != null && tempValue in 20000..90000) { // 20-90°C in milli
                                detectedPaths["cpu_temp"] = tempFile.absolutePath
                                Log.d(TAG, "Found CPU temp (fallback): ${tempFile.absolutePath} (type=$type)")
                                return tempFile.absolutePath
                            }
                        }
                    } catch (e: Exception) {
                        // Continue to next file on error
                    }
                }
            }
        }

        Log.w(TAG, "No suitable CPU temperature node found")
        detectedPaths["cpu_temp"] = null
        return null
    }

    /**
     * Gets CPU temperature path and divider information
     * 
     * @return Pair containing path and divider, or (null, 1000) if not found
     */
    fun getCpuTempInfo(): Pair<String?, Int> {
        val path = getCpuTempPath()
        if (path == null) return Pair(null, 1000)

        // Return cached divider if available
        if (detectedDividers.containsKey("cpu_temp")) {
            return Pair(path, detectedDividers["cpu_temp"]!!)
        }

        // Auto-detect divider and cache it
        val divider = detectTemperatureDivider(path)
        detectedDividers["cpu_temp"] = divider
        return Pair(path, divider)
    }

    // Possible paths for each hardware component
    private val BATTERY_TEMP_PATHS = arrayOf(
        "/sys/class/power_supply/battery/temp",
        "/sys/class/power_supply/battery/batt_temp",
        "/sys/class/power_supply/bms/temp",
        "/sys/class/oplus_chg/battery/temp",
        "/sys/class/oplus_chg/battery/batt_temp",
        "/sys/class/oplus_chg/battery/temperature",
        "/sys/class/oplus_chg/bq27541/temp",
        "/sys/class/thermal/thermal_zone0/temp"
    )
    
    private val FPS_PATHS = arrayOf(
        "/sys/class/drm/card0/sde_crtc_fps",
        "/sys/class/graphics/fb0/fps",
        "/sys/class/graphics/fb0/measured_fps",
        "/sys/class/drm/sde-crtc-0/measured_fps"
    )

    /**
     * Detect a valid sysfs path from a list of possible paths
     */
    private fun detectPath(pathType: String, possiblePaths: Array<String>): String? {
        // Check cache first
        detectedPaths[pathType]?.let { return it }
        
        for (path in possiblePaths) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                Log.d(TAG, "Detected $pathType: $path")
                detectedPaths[pathType] = path
                return path
            }
        }
        
        Log.w(TAG, "No valid path found for $pathType")
        detectedPaths[pathType] = null
        return null
    }

    // Public API for each hardware component
    
    fun getFpsPath(): String? = detectPath("fps", FPS_PATHS)
    
    fun getBatteryTempPath(): String? = detectPath("battery_temp", BATTERY_TEMP_PATHS)

    /** @return CPU temperature sysfs path or null if not supported */
    fun getCpuTempPath(): String? = findCpuTempPath()

    /** @return CPU base sysfs path*/
    fun getCpuBasePath(): String? {
        // Check cache first
        detectedPaths["cpu_base"]?.let { return it }
        
        val path = "/sys/devices/system/cpu"
        val file = File(path)
        if (file.exists() && file.isDirectory()) {
            detectedPaths["cpu_base"] = path
            Log.d(TAG, "Using CPU base path: $path")
            return path
        }
        
        Log.w(TAG, "CPU base path not found: $path")
        detectedPaths["cpu_base"] = null
        return null
    }

    /**
     * Clear cache and re-detect all paths (useful for debugging)
     */
    fun resetDetection() {
        detectedPaths.clear()
        Log.d(TAG, "Sysfs detection cache cleared")
    }
    
    /**
     * Check if a specific hardware component is supported
     * for future usage
     */
    fun isComponentSupported(component: String): Boolean {
        return when (component) {
            "fps" -> getFpsPath() != null
            "battery_temp" -> getBatteryTempPath() != null
            "cpu_temp" -> getCpuTempPath() != null
            "cpu_base" -> getCpuBasePath() != null
            else -> false
        }
    }
}