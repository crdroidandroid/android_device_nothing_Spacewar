/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

data class FontItem(
    val name: String,
    val displayName: String,
    val path: String,
    var isSelected: Boolean = false
)
