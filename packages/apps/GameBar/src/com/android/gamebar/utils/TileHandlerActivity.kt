/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar.utils

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log

import com.android.gamebar.GameBarSettingsActivity
import com.android.gamebar.GameBarTileService

class TileHandlerActivity : Activity() {

    companion object {
        private const val TAG = "TileHandlerActivity"
        private val TILE_ACTIVITY_MAP = mapOf(
            GameBarTileService::class.java.name to GameBarSettingsActivity::class.java
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activeIntent = intent
        if (activeIntent == null || TileService.ACTION_QS_TILE_PREFERENCES != activeIntent.action) {
            Log.e(TAG, "Invalid or null intent received")
            finish()
            return
        }

        val qsTile: ComponentName? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            activeIntent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            activeIntent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME)
        }

        if (qsTile == null) {
            Log.e(TAG, "No QS tile component found in intent")
            finish()
            return
        }

        val qsName = qsTile.className
        val targetIntent = Intent()

        if (TILE_ACTIVITY_MAP.containsKey(qsName)) {
            targetIntent.setClass(this, TILE_ACTIVITY_MAP[qsName]!!)
            Log.d(TAG, "Launching settings activity for QS tile: $qsName")
        } else {
            val packageName = qsTile.packageName
            if (packageName == null) {
                Log.e(TAG, "QS tile package name is null")
                finish()
                return
            }
            targetIntent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            targetIntent.data = Uri.fromParts("package", packageName, null)
            Log.d(TAG, "Opening app info for package: $packageName")
        }

        targetIntent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TASK or
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        startActivity(targetIntent)
        finish()
    }
}
