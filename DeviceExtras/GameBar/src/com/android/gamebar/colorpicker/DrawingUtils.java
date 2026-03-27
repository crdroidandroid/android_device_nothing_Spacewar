/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-FileCopyrightText: 2017 Jared Rummler
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar.colorpicker;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.TypedValue;

final class DrawingUtils {

  static int dpToPx(Context c, float dipValue) {
    DisplayMetrics metrics = c.getResources().getDisplayMetrics();
    float val = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
    int res = (int) (val + 0.5); // Round
    // Ensure at least 1 pixel if val was > 0
    return res == 0 && val > 0 ? 1 : res;
  }
}
