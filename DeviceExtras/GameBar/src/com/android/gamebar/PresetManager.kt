/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages GameBar configuration presets
 * Allows saving, loading, exporting, and importing preset configurations
 */
class PresetManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: PresetManager? = null

        fun getInstance(context: Context): PresetManager {
            return instance ?: synchronized(this) {
                instance ?: PresetManager(context.applicationContext).also { instance = it }
            }
        }

        private const val PREF_PRESETS = "gamebar_presets"
        private const val PREF_PRESET_LIST = "gamebar_preset_list"
        
        // Keys for settings to save in presets
        private val PRESET_KEYS = listOf(
            // Overlay Features
            "game_bar_fps_enable",
            "game_bar_fps_display_mode",
            "game_bar_frame_time_enable",
            "game_bar_temp_enable",
            "game_bar_cpu_usage_enable",
            "game_bar_cpu_clock_enable",
            "game_bar_cpu_temp_enable",
            "game_bar_ram_enable",
            "game_bar_ram_speed_enable",
            "game_bar_ram_temp_enable",
            "game_bar_gpu_usage_enable",
            "game_bar_gpu_clock_enable",
            "game_bar_gpu_temp_enable",
            
            // FPS Measurement Method
            "game_bar_fps_method",
            
            // Customization
            "game_bar_text_size",
            "game_bar_background_alpha",
            "game_bar_background_color",
            "game_bar_corner_radius",
            "game_bar_padding",
            "game_bar_item_spacing",
            "game_bar_update_interval",
            "game_bar_title_color",
            "game_bar_value_color",
            "game_bar_text_color",  // Legacy text color if exists
            "game_bar_font_path",
            "game_bar_position",
            "game_bar_format",
            
            // Split Config
            "game_bar_split_mode",
            
            // Gesture Controls
            "game_bar_single_tap_enable",
            "game_bar_single_tap_function",
            "game_bar_doubletap_enable",
            "game_bar_doubletap_function",
            "game_bar_longpress_enable",
            "game_bar_longpress_function",
            "game_bar_longpress_timeout",
            
            // Per-App Settings
            "game_bar_auto_enable"
        )
    }

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    data class Preset(
        val id: String,
        val name: String,
        val createdDate: String,
        val settings: Map<String, Any>
    )

    /**
     * Get list of all saved presets
     */
    fun getAllPresets(): List<Preset> {
        val presetList = mutableListOf<Preset>()
        val presetIds = getPresetIds()
        
        for (id in presetIds) {
            val presetJson = prefs.getString("${PREF_PRESETS}_$id", null)
            presetJson?.let {
                try {
                    val preset = parsePresetFromJson(it)
                    presetList.add(preset)
                } catch (e: Exception) {
                    android.util.Log.e("PresetManager", "Failed to parse preset $id", e)
                }
            }
        }
        
        return presetList.sortedByDescending { it.createdDate }
    }

    /**
     * Save current settings as a new preset
     */
    fun savePreset(name: String): Boolean {
        try {
            val id = UUID.randomUUID().toString()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val createdDate = dateFormat.format(Date())
            
            val settings = mutableMapOf<String, Any>()
            for (key in PRESET_KEYS) {
                val value = prefs.all[key]
                value?.let { settings[key] = it }
            }
            
            val preset = Preset(id, name, createdDate, settings)
            val json = presetToJson(preset)
            
            // Save preset
            prefs.edit().putString("${PREF_PRESETS}_$id", json).apply()
            
            // Add to preset list
            val presetIds = getPresetIds().toMutableSet()
            presetIds.add(id)
            savePresetIds(presetIds)
            
            return true
        } catch (e: Exception) {
            android.util.Log.e("PresetManager", "Failed to save preset", e)
            return false
        }
    }

    /**
     * Load a preset and apply its settings
     */
    fun loadPreset(presetId: String): Boolean {
        try {
            val presetJson = prefs.getString("${PREF_PRESETS}_$presetId", null) ?: return false
            val preset = parsePresetFromJson(presetJson)
            
            val editor = prefs.edit()
            for ((key, value) in preset.settings) {
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                }
            }
            editor.apply()
            
            // Notify GameBar to reload settings
            if (GameBar.isInstanceCreated()) {
                val gameBar = GameBar.getInstance(context)
                gameBar.applyPreferences()
                if (GameBar.isShowing()) {
                    gameBar.hide()
                    gameBar.show()
                }
            }
            
            return true
        } catch (e: Exception) {
            android.util.Log.e("PresetManager", "Failed to load preset", e)
            return false
        }
    }

    /**
     * Delete a preset
     */
    fun deletePreset(presetId: String): Boolean {
        try {
            prefs.edit().remove("${PREF_PRESETS}_$presetId").apply()
            
            val presetIds = getPresetIds().toMutableSet()
            presetIds.remove(presetId)
            savePresetIds(presetIds)
            
            return true
        } catch (e: Exception) {
            android.util.Log.e("PresetManager", "Failed to delete preset", e)
            return false
        }
    }

    /**
     * Export preset to JSON file
     */
    fun exportPreset(presetId: String, outputFile: File): Boolean {
        try {
            val presetJson = prefs.getString("${PREF_PRESETS}_$presetId", null) ?: return false
            
            FileOutputStream(outputFile).use { fos ->
                fos.write(presetJson.toByteArray())
            }
            
            return true
        } catch (e: Exception) {
            android.util.Log.e("PresetManager", "Failed to export preset", e)
            return false
        }
    }

    /**
     * Import preset from JSON file
     */
    fun importPreset(inputFile: File): Boolean {
        try {
            val json = FileInputStream(inputFile).use { fis ->
                fis.readBytes().toString(Charsets.UTF_8)
            }
            
            // Parse to validate
            val preset = parsePresetFromJson(json)
            
            // Generate new ID for imported preset
            val newId = UUID.randomUUID().toString()
            val newPreset = preset.copy(id = newId)
            val newJson = presetToJson(newPreset)
            
            // Save imported preset
            prefs.edit().putString("${PREF_PRESETS}_$newId", newJson).apply()
            
            // Add to preset list
            val presetIds = getPresetIds().toMutableSet()
            presetIds.add(newId)
            savePresetIds(presetIds)
            
            return true
        } catch (e: Exception) {
            android.util.Log.e("PresetManager", "Failed to import preset", e)
            return false
        }
    }

    /**
     * Rename a preset
     */
    fun renamePreset(presetId: String, newName: String): Boolean {
        try {
            val presetJson = prefs.getString("${PREF_PRESETS}_$presetId", null) ?: return false
            val preset = parsePresetFromJson(presetJson)
            val updatedPreset = preset.copy(name = newName)
            val updatedJson = presetToJson(updatedPreset)
            
            prefs.edit().putString("${PREF_PRESETS}_$presetId", updatedJson).apply()
            return true
        } catch (e: Exception) {
            android.util.Log.e("PresetManager", "Failed to rename preset", e)
            return false
        }
    }

    /**
     * Reset all GameBar settings to factory defaults
     */
    fun resetToDefaults(): Boolean {
        try {
            val editor = prefs.edit()
            
            // Clear all preset-related settings
            for (key in PRESET_KEYS) {
                editor.remove(key)
            }
            
            // Clear warning dialog preference
            editor.remove("dont_show_longpress_warning")
            
            editor.apply()
            
            // Reload GameBar to apply defaults
            if (GameBar.isInstanceCreated()) {
                GameBar.getInstance(context).applyPreferences()
            }
            
            return true
        } catch (e: Exception) {
            android.util.Log.e("PresetManager", "Failed to reset to defaults", e)
            return false
        }
    }

    private fun getPresetIds(): Set<String> {
        val idsJson = prefs.getString(PREF_PRESET_LIST, null) ?: return emptySet()
        return try {
            val jsonArray = JSONArray(idsJson)
            val ids = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                ids.add(jsonArray.getString(i))
            }
            ids
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun savePresetIds(ids: Set<String>) {
        val jsonArray = JSONArray(ids.toList())
        prefs.edit().putString(PREF_PRESET_LIST, jsonArray.toString()).apply()
    }

    private fun presetToJson(preset: Preset): String {
        val json = JSONObject()
        json.put("id", preset.id)
        json.put("name", preset.name)
        json.put("createdDate", preset.createdDate)
        
        val settingsJson = JSONObject()
        for ((key, value) in preset.settings) {
            settingsJson.put(key, value)
        }
        json.put("settings", settingsJson)
        
        return json.toString()
    }

    private fun parsePresetFromJson(jsonString: String): Preset {
        val json = JSONObject(jsonString)
        val id = json.getString("id")
        val name = json.getString("name")
        val createdDate = json.getString("createdDate")
        
        val settingsJson = json.getJSONObject("settings")
        val settings = mutableMapOf<String, Any>()
        
        val keys = settingsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = settingsJson.get(key)
            settings[key] = value
        }
        
        return Preset(id, name, createdDate, settings)
    }
}
