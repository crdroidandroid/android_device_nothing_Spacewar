/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UiStyleController {
    private const val PREFS = "GameBarUiStyle"
    private const val KEY_AMOLED_BLACK = "amoled_black_enabled"

    private val _amoledBlackEnabled = MutableStateFlow(false)
    val amoledBlackEnabled: StateFlow<Boolean> = _amoledBlackEnabled.asStateFlow()

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _amoledBlackEnabled.value = prefs.getBoolean(KEY_AMOLED_BLACK, false)
            initialized = true
        }
    }

    fun setAmoledBlackEnabled(context: Context, enabled: Boolean) {
        ensureInitialized(context)
        if (_amoledBlackEnabled.value == enabled) return
        _amoledBlackEnabled.value = enabled
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AMOLED_BLACK, enabled)
            .apply()
    }
}
