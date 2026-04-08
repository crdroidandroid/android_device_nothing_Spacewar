/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.android.gamebar.colorpicker.ColorPickerDialog
import com.android.gamebar.colorpicker.ColorPickerDialogListener
import com.android.gamebar.colorpicker.ColorShape
import com.android.gamebar.ui.components.GameBarFloatingBottomNavBar
import com.android.gamebar.ui.components.GameBarLottieCard
import com.android.gamebar.ui.components.GameBarNavTab
import com.android.gamebar.ui.components.HeaderCard
import com.android.gamebar.ui.components.HomeMenuCard
import com.android.gamebar.ui.components.SelectOption
import com.android.gamebar.ui.components.SettingsActionRow
import com.android.gamebar.ui.components.SettingsCustomSliderRow
import com.android.gamebar.ui.components.SettingsSectionCard
import com.android.gamebar.ui.components.SettingsSelectRow
import com.android.gamebar.ui.components.SettingsSwitchRow
import com.android.gamebar.ui.theme.UiStyleController
import java.io.File

@Composable
fun GameBarSettingsScreen(
    onOpenPerAppConfig: () -> Unit,
    onOpenFpsRecord: () -> Unit,
    onOpenFontSelector: () -> Unit,
    onOpenPresetManager: () -> Unit,
    onToggleLauncherIcon: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    UiStyleController.ensureInitialized(context)
    val amoledBlackEnabled by UiStyleController.amoledBlackEnabled.collectAsState()
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val presetManager = remember { PresetManager.getInstance(context) }
    var selectedTab by remember { mutableStateOf(GameBarNavTab.HOME) }

    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun readGestureAction(key: String, defaultValue: String): String {
        val raw = prefs.getString(key, defaultValue) ?: defaultValue
        return if (raw == "capture_logs") {
            putString(key, "no_action")
            "no_action"
        } else {
            raw
        }
    }

    fun applyPrefs(rebuild: Boolean = false) {
        if (!GameBar.isInstanceCreated()) return
        val gameBar = GameBar.getInstance(context)
        gameBar.applyPreferences()
        if (rebuild && GameBar.isShowing()) {
            gameBar.hide()
            gameBar.show()
        }
    }

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var savePresetName by remember { mutableStateOf("") }
    var showResetDefaultsDialog by remember { mutableStateOf(false) }
    var fpsRecordControlEnabled by remember {
        mutableStateOf(prefs.getBoolean("game_bar_fps_record_control_enabled", false))
    }

    val importPresetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = runCatching {
            val tmp = File(context.cacheDir, "gamebar_import_${System.currentTimeMillis()}.json")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching false
            val result = presetManager.importPreset(tmp)
            tmp.delete()
            result
        }.getOrDefault(false)
        Toast.makeText(
            context,
            if (imported) context.getString(R.string.toast_preset_imported) else context.getString(R.string.toast_preset_import_failed),
            Toast.LENGTH_SHORT
        ).show()
    }

    var gameBarEnabled by remember { mutableStateOf(prefs.getBoolean("game_bar_enable", false)) }
    var autoEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_auto_enable", false)) }

    androidx.compose.runtime.DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            when (key) {
                "game_bar_enable" -> gameBarEnabled = sharedPrefs.getBoolean(key, false)
                "game_bar_auto_enable" -> autoEnable = sharedPrefs.getBoolean(key, false)
                "game_bar_fps_record_control_enabled" -> fpsRecordControlEnabled = sharedPrefs.getBoolean(key, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    var showLauncherIcon by remember {
        mutableStateOf(
            context.getSharedPreferences(GameBarSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(GameBarSettingsActivity.KEY_SHOW_LAUNCHER_ICON, true)
        )
    }

    var fpsEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_fps_enable", true)) }
    var frameTimeEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_frame_time_enable", false)) }
    var tempEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_temp_enable", false)) }
    var cpuUsageEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_cpu_usage_enable", true)) }
    var cpuClockEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_cpu_clock_enable", false)) }
    var cpuTempEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_cpu_temp_enable", false)) }
    var ramEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_ram_enable", false)) }
    var ramSpeedEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_ram_speed_enable", false)) }
    var ramTempEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_ram_temp_enable", false)) }
    var gpuUsageEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_gpu_usage_enable", true)) }
    var gpuClockEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_gpu_clock_enable", false)) }
    var gpuTempEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_gpu_temp_enable", false)) }

    var fpsDisplayMode by remember { mutableStateOf(prefs.getString("game_bar_fps_display_mode", "basic") ?: "basic") }
    var fpsMethod by remember { mutableStateOf(prefs.getString("game_bar_fps_method", "new") ?: "new") }

    var textSize by remember { mutableStateOf(prefs.getInt("game_bar_text_size", 15)) }
    var bgAlpha by remember { mutableStateOf(prefs.getInt("game_bar_background_alpha", 255)) }
    var bgColor by remember { mutableStateOf(prefs.getInt("game_bar_background_color", 0xFF0D1117.toInt())) }
    var cornerRadius by remember { mutableStateOf(prefs.getInt("game_bar_corner_radius", 30)) }
    var paddingValue by remember { mutableStateOf(prefs.getInt("game_bar_padding", 4)) }
    var itemSpacing by remember { mutableStateOf(prefs.getInt("game_bar_item_spacing", 8)) }
    var overlayWidthDp by remember { mutableStateOf(prefs.getInt("game_bar_overlay_width_dp", 280)) }
    var updateInterval by remember { mutableStateOf(prefs.getString("game_bar_update_interval", "1000") ?: "1000") }
    var titleColor by remember { mutableStateOf(prefs.getInt("game_bar_title_color", 0xFF9FCEDE.toInt())) }
    var valueColor by remember { mutableStateOf(prefs.getInt("game_bar_value_color", 0xFFFFB347.toInt())) }
    var overlayFormat by remember { mutableStateOf(prefs.getString("game_bar_format", "full") ?: "full") }
    var splitMode by remember { mutableStateOf(prefs.getString("game_bar_split_mode", "side_by_side") ?: "side_by_side") }

    var singleTapEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_single_tap_enable", true)) }
    var singleTapFunction by remember { mutableStateOf(readGestureAction("game_bar_single_tap_function", "toggle_format")) }
    var doubleTapEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_doubletap_enable", true)) }
    var doubleTapFunction by remember { mutableStateOf(readGestureAction("game_bar_doubletap_function", "adjust_length")) }
    var longPressEnable by remember { mutableStateOf(prefs.getBoolean("game_bar_longpress_enable", true)) }
    var longPressFunction by remember { mutableStateOf(readGestureAction("game_bar_longpress_function", "load_preset")) }
    var longPressTimeout by remember { mutableStateOf(prefs.getString("game_bar_longpress_timeout", "500") ?: "500") }

    val fpsDisplayOptions = listOf(
        SelectOption("basic", "Basic (Current FPS only)"),
        SelectOption("advanced", "Advanced (Current + 1% + 0.1% Low)"),
    )
    val fpsMethodOptions = listOf(
        SelectOption("new", "New API (Default)"),
        SelectOption("legacy", "Legacy Sysfs"),
    )
    val intervalOptions = listOf(
        SelectOption("500", "Every 500ms"),
        SelectOption("1000", "Every second"),
        SelectOption("2000", "Every 2 seconds"),
        SelectOption("5000", "Every 5 seconds"),
    )
    val overlayFormatOptions = listOf(SelectOption("full", "Full"), SelectOption("minimal", "Minimal"))
    val splitModeOptions = listOf(SelectOption("side_by_side", "Side-by-Side"), SelectOption("stacked", "Stacked"))
    val gestureOptions = listOf(
        SelectOption("no_action", "No Action"),
        SelectOption("adjust_length", "Length Adjustment Mode"),
        SelectOption("toggle_format", "Toggle Full/Minimal Format"),
        SelectOption("open_settings", "Open GameBar Settings"),
        SelectOption("take_screenshot", "Take Screenshot"),
        SelectOption("screen_record", "Start/Stop Screen Record"),
        SelectOption("load_preset", "Load Preset (Quick)"),
    )
    val longPressOptions = listOf(
        SelectOption("500", "0.5 seconds"),
        SelectOption("1000", "1 second"),
        SelectOption("2000", "2 seconds"),
        SelectOption("3000", "3 seconds"),
    )
    fun colorLabel(color: Int): String = String.format("#%08X", color)
    fun openColorPicker(dialogId: Int, currentColor: Int, showAlpha: Boolean, onColor: (Int) -> Unit) {
        val activity = context as? FragmentActivity ?: return
        val picker = ColorPickerDialog.newBuilder()
            .setDialogId(dialogId)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setDialogTitle(R.string.gamebar_default_title)
            .setColorShape(ColorShape.CIRCLE)
            .setAllowPresets(true)
            .setAllowCustom(true)
            .setShowAlphaSlider(showAlpha)
            .setShowColorShades(true)
            .setColor(currentColor)
            .create()
        picker.setColorPickerDialogListener(object : ColorPickerDialogListener {
            override fun onColorSelected(dialogId: Int, color: Int) {
                onColor(color)
            }
            override fun onDialogDismissed(dialogId: Int) {}
        })
        picker.show(activity.supportFragmentManager, "gamebar-color-$dialogId")
    }

    val pageTitle = when (selectedTab) {
        GameBarNavTab.HOME -> stringResource(R.string.game_bar_title)
        GameBarNavTab.FEATURES -> "Overlay Features"
        GameBarNavTab.CUSTOMIZATION -> "Customization"
        GameBarNavTab.FPS_RECORD -> stringResource(R.string.fps_record_title)
        GameBarNavTab.PRESETS -> stringResource(R.string.preset_category_title)
    }
    val pageSummary = when (selectedTab) {
        GameBarNavTab.HOME -> stringResource(R.string.game_bar_summary)
        GameBarNavTab.FEATURES -> "Overlay features and FPS method"
        GameBarNavTab.CUSTOMIZATION -> "Configure overlay customization, split mode and gesture behavior"
        GameBarNavTab.FPS_RECORD -> stringResource(R.string.fps_record_summary)
        GameBarNavTab.PRESETS -> stringResource(R.string.preset_category_summary)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
                        )
                    )
                )
                .padding(scaffoldPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { HeaderCard(pageTitle, pageSummary) }
                when (selectedTab) {
                    GameBarNavTab.HOME -> {
                        item { GameBarLottieCard() }
                        item {
                            SettingsSectionCard(title = "Main Switch", showHeader = false) {
                                SettingsSwitchRow(
                                    title = "Enable GameBar Overlay",
                                    summary = stringResource(R.string.game_bar_summary),
                                    checked = gameBarEnabled
                                ) { checked ->
                                    if (checked && !Settings.canDrawOverlays(context)) {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                        gameBarEnabled = false
                                        putBoolean("game_bar_enable", false)
                                        return@SettingsSwitchRow
                                    }
                                    gameBarEnabled = checked
                                    putBoolean("game_bar_enable", checked)
                                    if (checked) {
                                        val gameBar = GameBar.getInstance(context)
                                        gameBar.hide()
                                        gameBar.applyPreferences()
                                        gameBar.show()
                                        context.startService(Intent(context, GameBarMonitorService::class.java))
                                    } else {
                                        if (GameBar.isInstanceCreated()) {
                                            GameBar.getInstance(context).hide()
                                            GameBar.destroyInstance()
                                        }
                                        if (!autoEnable) context.stopService(Intent(context, GameBarMonitorService::class.java))
                                    }
                                }
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    HomeMenuCard(
                                        title = "Features",
                                        summary = "Stats & methods",
                                        iconRes = GameBarNavTab.FEATURES.iconRes
                                    ) {
                                        selectedTab = GameBarNavTab.FEATURES
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    HomeMenuCard(
                                        title = "UI \u0026 Style",
                                        summary = "Customizations",
                                        iconRes = GameBarNavTab.CUSTOMIZATION.iconRes
                                    ) {
                                        selectedTab = GameBarNavTab.CUSTOMIZATION
                                    }
                                }
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    HomeMenuCard(
                                        title = "Records",
                                        summary = "FPS tracking logs",
                                        iconRes = GameBarNavTab.FPS_RECORD.iconRes
                                    ) {
                                        selectedTab = GameBarNavTab.FPS_RECORD
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    HomeMenuCard(
                                        title = "Settings",
                                        summary = "App configs",
                                        iconRes = GameBarNavTab.PRESETS.iconRes
                                    ) {
                                        selectedTab = GameBarNavTab.PRESETS
                                    }
                                }
                            }
                        }
                    }

                    GameBarNavTab.FEATURES -> {
                        item {
                            SettingsSectionCard(title = "Overlay Features") {
                                SettingsSwitchRow("FPS Overlay", "Show current FPS", fpsEnable) { fpsEnable = it; putBoolean("game_bar_fps_enable", it); applyPrefs() }
                                SettingsSelectRow("FPS Display Mode", fpsDisplayMode, fpsDisplayOptions) { fpsDisplayMode = it; putString("game_bar_fps_display_mode", it); applyPrefs(rebuild = true) }
                                SettingsSelectRow("Select FPS Method", fpsMethod, fpsMethodOptions) {
                                    fpsMethod = it
                                    putString("game_bar_fps_method", it)
                                    applyPrefs(rebuild = true)
                                }
                                SettingsSwitchRow("Frame Time", "Show frame time in milliseconds", frameTimeEnable) { frameTimeEnable = it; putBoolean("game_bar_frame_time_enable", it); applyPrefs() }
                                SettingsSwitchRow("Device Temperature", "Show device temperature", tempEnable) { tempEnable = it; putBoolean("game_bar_temp_enable", it); applyPrefs() }
                                SettingsSwitchRow("CPU Usage", "Show current CPU usage", cpuUsageEnable) { cpuUsageEnable = it; putBoolean("game_bar_cpu_usage_enable", it); applyPrefs() }
                                SettingsSwitchRow("CPU Clock Speeds", "Show current CPU clock speeds for each core", cpuClockEnable) { cpuClockEnable = it; putBoolean("game_bar_cpu_clock_enable", it); applyPrefs() }
                                SettingsSwitchRow("CPU Temperature", "Show CPU temperature", cpuTempEnable) { cpuTempEnable = it; putBoolean("game_bar_cpu_temp_enable", it); applyPrefs() }
                                SettingsSwitchRow("RAM Usage", "Show current RAM usage in MB", ramEnable) { ramEnable = it; putBoolean("game_bar_ram_enable", it); applyPrefs() }
                                SettingsSwitchRow("RAM Frequency", "Show current RAM frequency(bus) in GHz", ramSpeedEnable) { ramSpeedEnable = it; putBoolean("game_bar_ram_speed_enable", it); applyPrefs() }
                                SettingsSwitchRow("RAM Temperature", "Show current RAM temperature", ramTempEnable) { ramTempEnable = it; putBoolean("game_bar_ram_temp_enable", it); applyPrefs() }
                                SettingsSwitchRow("GPU Usage", "Show GPU usage percentage", gpuUsageEnable) { gpuUsageEnable = it; putBoolean("game_bar_gpu_usage_enable", it); applyPrefs() }
                                SettingsSwitchRow("GPU Clock Speed", "Show current GPU clock frequency", gpuClockEnable) { gpuClockEnable = it; putBoolean("game_bar_gpu_clock_enable", it); applyPrefs() }
                                SettingsSwitchRow("GPU Temperature", "Show current GPU temperature", gpuTempEnable) { gpuTempEnable = it; putBoolean("game_bar_gpu_temp_enable", it); applyPrefs() }
                            }
                        }
                    }

                    GameBarNavTab.CUSTOMIZATION -> {
                        item {
                            SettingsSectionCard(title = "Customization") {
                                SettingsCustomSliderRow("Text Size", textSize, 5, 50, defaultValue = 15) { textSize = it; putInt("game_bar_text_size", it); applyPrefs() }
                                SettingsCustomSliderRow("Background Transparency", bgAlpha, 0, 255, defaultValue = 255) { bgAlpha = it; putInt("game_bar_background_alpha", it); applyPrefs() }
                                SettingsActionRow("Overlay Background Color", colorLabel(bgColor)) {
                                    openColorPicker(dialogId = 1001, currentColor = bgColor, showAlpha = true) { picked ->
                                        bgColor = picked
                                        putInt("game_bar_background_color", picked)
                                        applyPrefs()
                                    }
                                }
                                SettingsCustomSliderRow("Overlay Corner Radius", cornerRadius, 0, 100, defaultValue = 30) { cornerRadius = it; putInt("game_bar_corner_radius", it); applyPrefs() }
                                SettingsCustomSliderRow("Overlay Padding", paddingValue, 0, 64, defaultValue = 4) { paddingValue = it; putInt("game_bar_padding", it); applyPrefs() }
                                SettingsCustomSliderRow("Item Spacing", itemSpacing, 0, 50, defaultValue = 8) { itemSpacing = it; putInt("game_bar_item_spacing", it); applyPrefs() }
                                SettingsCustomSliderRow("Overlay Length", overlayWidthDp, 160, 720, defaultValue = 280, units = "dp") {
                                    overlayWidthDp = it
                                    putInt("game_bar_overlay_width_dp", it)
                                    applyPrefs()
                                }
                                SettingsSelectRow("Update Interval", updateInterval, intervalOptions) { updateInterval = it; putString("game_bar_update_interval", it); applyPrefs() }
                                SettingsActionRow("Stat Title Color", colorLabel(titleColor)) {
                                    openColorPicker(dialogId = 1002, currentColor = titleColor, showAlpha = false) { picked ->
                                        titleColor = picked
                                        putInt("game_bar_title_color", picked)
                                        applyPrefs()
                                    }
                                }
                                SettingsActionRow("Stat Value Color", colorLabel(valueColor)) {
                                    openColorPicker(dialogId = 1003, currentColor = valueColor, showAlpha = false) { picked ->
                                        valueColor = picked
                                        putInt("game_bar_value_color", picked)
                                        applyPrefs()
                                    }
                                }
                                SettingsActionRow("Overlay Font Style", "Choose custom font for overlay text", onOpenFontSelector)
                                SettingsSelectRow("Overlay Format", overlayFormat, overlayFormatOptions) { overlayFormat = it; putString("game_bar_format", it); applyPrefs() }
                            }
                        }
                        item {
                            SettingsSectionCard(title = "Split Config") {
                                SettingsSelectRow("Split Mode", splitMode, splitModeOptions) {
                                    splitMode = it; putString("game_bar_split_mode", it); applyPrefs()
                                }
                            }
                        }
                        item {
                            SettingsSectionCard(title = "Overlay Gesture Controls") {
                                SettingsSwitchRow("Enable Single Tap", "Tap once to perform an action", singleTapEnable) { singleTapEnable = it; putBoolean("game_bar_single_tap_enable", it); applyPrefs() }
                                SettingsSelectRow("Single Tap Function", singleTapFunction, gestureOptions, singleTapEnable) { singleTapFunction = it; putString("game_bar_single_tap_function", it); applyPrefs() }
                                SettingsSwitchRow("Enable Double Tap", "Double-tap to perform an action", doubleTapEnable) { doubleTapEnable = it; putBoolean("game_bar_doubletap_enable", it); applyPrefs() }
                                SettingsSelectRow("Double Tap Function", doubleTapFunction, gestureOptions, doubleTapEnable) { doubleTapFunction = it; putString("game_bar_doubletap_function", it); applyPrefs() }
                                SettingsSwitchRow("Enable Long Press", "Long-press to perform an action", longPressEnable) { longPressEnable = it; putBoolean("game_bar_longpress_enable", it); applyPrefs() }
                                SettingsSelectRow("Long Press Function", longPressFunction, gestureOptions, longPressEnable) { longPressFunction = it; putString("game_bar_longpress_function", it); applyPrefs() }
                                SettingsSelectRow("Long Press Duration", longPressTimeout, longPressOptions, longPressEnable) { longPressTimeout = it; putString("game_bar_longpress_timeout", it); applyPrefs() }
                            }
                        }
                    }

                    GameBarNavTab.FPS_RECORD -> {
                        item {
                            SettingsSectionCard(title = stringResource(R.string.fps_record_title), summary = stringResource(R.string.fps_record_summary)) {
                                SettingsSwitchRow(
                                    "Floating Recording Control",
                                    "Show draggable play/record control bubble and record without main overlay",
                                    fpsRecordControlEnabled
                                ) { enabled ->
                                    fpsRecordControlEnabled = enabled
                                    putBoolean("game_bar_fps_record_control_enabled", enabled)
                                    val svcIntent = Intent(context, FpsRecordControlOverlayService::class.java).apply {
                                        action = FpsRecordControlOverlayService.ACTION_SET_ENABLED
                                        putExtra(FpsRecordControlOverlayService.EXTRA_ENABLED, enabled)
                                    }
                                    context.startService(svcIntent)
                                }
                                SettingsActionRow(stringResource(R.string.fps_record_title), stringResource(R.string.fps_record_summary), onOpenFpsRecord)
                            }
                        }
                    }

                    GameBarNavTab.PRESETS -> {
                        item {
                            SettingsSectionCard(title = "Theme and Appearance") {
                                SettingsSwitchRow(
                                    title = "AMOLED Black Theme",
                                    summary = "Use deeper black surfaces for GameBar cards and pages",
                                    checked = amoledBlackEnabled
                                ) {
                                    UiStyleController.setAmoledBlackEnabled(context, it)
                                }
                            }
                        }
                        item {
                            SettingsSectionCard(title = "Per-App GameBar") {
                                SettingsSwitchRow(
                                    "Auto-Enable GameBar for Selected Apps",
                                    "If turned on, selected apps will auto-enable GameBar even if the main switch is off",
                                    autoEnable
                                ) {
                                    autoEnable = it
                                    putBoolean("game_bar_auto_enable", it)
                                    if (it || gameBarEnabled) {
                                        context.startService(Intent(context, GameBarMonitorService::class.java))
                                    } else {
                                        context.stopService(Intent(context, GameBarMonitorService::class.java))
                                    }
                                }
                                SettingsActionRow("Configure Apps", "Choose which apps will auto-enable GameBar", onOpenPerAppConfig)
                            }
                        }
                        item {
                            SettingsSectionCard(title = "Launcher Icon", showHeader = false) {
                                SettingsSwitchRow(
                                    "Show launcher icon",
                                    "Show or hide GameBar icon in launcher",
                                    showLauncherIcon
                                ) {
                                    showLauncherIcon = it
                                    context.getSharedPreferences(GameBarSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
                                        .edit().putBoolean(GameBarSettingsActivity.KEY_SHOW_LAUNCHER_ICON, it).apply()
                                    onToggleLauncherIcon(it)
                                }
                            }
                        }
                        item {
                            SettingsSectionCard(title = stringResource(R.string.preset_category_title), summary = stringResource(R.string.preset_category_summary)) {
                                SettingsActionRow(
                                    title = stringResource(R.string.preset_save_current),
                                    summary = stringResource(R.string.preset_save_current_summary)
                                ) { showSavePresetDialog = true }
                                SettingsActionRow(
                                    title = stringResource(R.string.preset_import),
                                    summary = stringResource(R.string.preset_import_summary)
                                ) { importPresetLauncher.launch("application/json") }
                                SettingsActionRow(
                                    title = stringResource(R.string.preset_reset_default),
                                    summary = stringResource(R.string.preset_reset_default_summary)
                                ) { showResetDefaultsDialog = true }
                                SettingsActionRow(
                                    title = stringResource(R.string.preset_saved_presets),
                                    summary = "View and manage your saved presets",
                                    onClick = onOpenPresetManager
                                )
                            }
                        }
                        item {
                            AboutGameBarCard()
                        }
                    }
                }
            }

            GameBarFloatingBottomNavBar(
                selected = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text(stringResource(R.string.dialog_title_save_preset)) },
            text = {
                OutlinedTextField(
                    value = savePresetName,
                    onValueChange = { savePresetName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.hint_preset_name)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = savePresetName.trim()
                    if (name.isEmpty()) {
                        Toast.makeText(context, R.string.toast_preset_name_empty, Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val ok = presetManager.savePreset(name)
                    Toast.makeText(
                        context,
                        if (ok) context.getString(R.string.toast_preset_saved, name) else context.getString(R.string.toast_preset_save_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (ok) {
                        savePresetName = ""
                        showSavePresetDialog = false
                    }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }

    if (showResetDefaultsDialog) {
        AlertDialog(
            onDismissRequest = { showResetDefaultsDialog = false },
            title = { Text(stringResource(R.string.preset_reset_default)) },
            text = { Text(stringResource(R.string.dialog_message_reset_default)) },
            confirmButton = {
                TextButton(onClick = {
                    val ok = presetManager.resetToDefaults()
                    Toast.makeText(
                        context,
                        if (ok) context.getString(R.string.toast_preset_reset_success) else context.getString(R.string.toast_preset_load_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    showResetDefaultsDialog = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDefaultsDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AboutGameBarCard() {
    val context = LocalContext.current
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            val versionName = packageInfo?.versionName ?: "1.0"
            
            Text(
                text = "GameBar v$versionName",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Universal real-time performance overlay toolkit for Android devices",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.widget.ImageView(ctx).apply {
                        setImageDrawable(ctx.packageManager.getApplicationIcon(ctx.packageName))
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    }
                },
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            Text(
                text = "Developed by kenway214",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                androidx.compose.material3.Button(
                    onClick = { 
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kenway214/packages_apps_GameBar")))
                    },
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    androidx.compose.material3.Icon(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.brand_github),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GitHub")
                }
                
                androidx.compose.material3.Button(
                    onClick = { 
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kenway214/packages_apps_GameBar/issues")))
                    },
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Issues")
                }

                androidx.compose.material3.Button(
                    onClick = { 
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/pandemonium_haydn")))
                    },
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    androidx.compose.material3.Icon(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.brand_telegram),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Support")
                }

                androidx.compose.material3.Button(
                    onClick = {
                        val url = context.getString(R.string.game_bar_user_guide_url)
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guide")
                }
            }
        }
    }
}
