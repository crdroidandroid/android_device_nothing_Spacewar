/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.preference.PreferenceManager
import com.android.gamebar.R

class GameBarTileService : TileService() {
    
    private lateinit var gameBar: GameBar

    override fun onCreate() {
        super.onCreate()
        gameBar = GameBar.getInstance(this)
    }
    
    private fun ensureGameBarInstance() {
        if (!GameBar.isInstanceCreated()) {
            gameBar = GameBar.getInstance(this)
        }
    }

    override fun onStartListening() {
        val enabled = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("game_bar_enable", false)
        updateTileState(enabled)
    }

    override fun onClick() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentlyEnabled = prefs.getBoolean("game_bar_enable", false)
        val newState = !currentlyEnabled

        // Update preference with apply() instead of commit() for better performance
        prefs.edit()
                .putBoolean("game_bar_enable", newState)
                .apply()

        updateTileState(newState)

        if (newState) {
            // Ensure we have overlay permission before showing
            if (android.provider.Settings.canDrawOverlays(this)) {
                android.util.Log.d("GameBarTileService", "Enabling GameBar from tile")
                
                // Ensure we have a GameBar instance
                ensureGameBarInstance()
                
                // Force cleanup any existing overlay first
                gameBar.hide()
                
                gameBar.applyPreferences()
                gameBar.show()
                
                // Start monitor service if needed
                val autoEnabled = prefs.getBoolean("game_bar_auto_enable", false)
                if (autoEnabled) {
                    startService(android.content.Intent(this, GameBarMonitorService::class.java))
                }
            } else {
                // Revert the preference if we don't have permission
                prefs.edit()
                        .putBoolean("game_bar_enable", false)
                        .apply()
                updateTileState(false)
                
                // Show permission request intent
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName"))
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } else {
            android.util.Log.d("GameBarTileService", "Disabling GameBar from tile")
            
            // Ensure we have a GameBar instance to hide
            ensureGameBarInstance()
            
            // Force hide the overlay
            gameBar.hide()
            
            // Also destroy the singleton to ensure clean state
            GameBar.destroyInstance()
            
            // Stop monitor service if auto-enable is also disabled
            val autoEnabled = prefs.getBoolean("game_bar_auto_enable", false)
            if (!autoEnabled) {
                stopService(android.content.Intent(this, GameBarMonitorService::class.java))
            }
        }
    }

    private fun updateTileState(enabled: Boolean) {
        val tile = qsTile ?: return
        
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.game_bar_tile_label)
        tile.contentDescription = getString(R.string.game_bar_tile_description)
        tile.updateTile()
    }
}
