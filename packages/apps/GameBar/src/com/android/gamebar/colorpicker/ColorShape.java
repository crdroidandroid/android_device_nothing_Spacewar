/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-FileCopyrightText: 2017 Jared Rummler
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar.colorpicker;

import androidx.annotation.IntDef;

/**
 * The shape of the color preview
 */
@IntDef({ ColorShape.SQUARE, ColorShape.CIRCLE }) public @interface ColorShape {

  int SQUARE = 0;

  int CIRCLE = 1;
}
