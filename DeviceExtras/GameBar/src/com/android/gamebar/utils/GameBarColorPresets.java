/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar.utils;

import androidx.annotation.ColorInt;

/**
 * Centralized GameBar color presets for title and value colors
 */
public class GameBarColorPresets {
    
    // Default colors
    @ColorInt
    public static final int DEFAULT_TITLE_COLOR = 0xFFFFFFFF; // White
    
    @ColorInt
    public static final int DEFAULT_VALUE_COLOR = 0xFF4CAF50; // Green
    
    // GameBar preset colors
    @ColorInt
    public static final int[] GAMEBAR_PRESETS = new int[] {
        0xFFFFFFFF, // White
        0xFFDC143C, // Crimson
        0xFF4CAF50, // Fruit Salad (Green)
        0xFF4169E1, // Royal Blue
        0xFFFFBF00, // Amber
        0xFF008080, // Teal
        0xFF8A2BE2, // Electric Violet
        0xFFFF1493  // Magenta
    };
    
    private GameBarColorPresets() {
        // Utility class - no instantiation
    }
}
