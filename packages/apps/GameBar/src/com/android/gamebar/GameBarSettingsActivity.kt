/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.android.gamebar.R
import com.android.gamebar.ui.theme.GameBarComposeTheme

class GameBarSettingsActivity : FragmentActivity() {

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1234
        private const val LAUNCHER_ALIAS_NAME = "com.android.gamebar.GameBarLauncher"
        const val PREFS_NAME = "GameBarSettings"
        const val KEY_SHOW_LAUNCHER_ICON = "show_launcher_icon"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GameBarComposeTheme {
                GameBarSettingsScreen(
                    onOpenPerAppConfig = {
                        startActivity(Intent(this, GameBarPerAppConfigActivity::class.java))
                    },
                    onOpenFpsRecord = {
                        startActivity(Intent(this, FpsRecordActivity::class.java))
                    },
                    onOpenFontSelector = {
                        startActivity(Intent(this, GameBarFontSelectorActivity::class.java))
                    },
                    onOpenPresetManager = {
                        startActivity(Intent(this, PresetManagementActivity::class.java))
                    },
                    onToggleLauncherIcon = { enabled -> setLauncherIconEnabled(enabled) }
                )
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            OVERLAY_PERMISSION_REQUEST_CODE -> {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, R.string.overlay_permission_granted, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setLauncherIconEnabled(enabled: Boolean) {
        val componentName = ComponentName(this, LAUNCHER_ALIAS_NAME)
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(componentName, state, PackageManager.DONT_KILL_APP)
    }

}
