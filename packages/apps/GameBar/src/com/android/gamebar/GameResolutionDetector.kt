/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min

object GameResolutionDetector {
    private const val TAG = "GameResolutionDetector"

    fun detectGameRenderResolution(context: Context, packageName: String): String {
        val metrics = context.resources.displayMetrics
        val fallback = "${metrics.widthPixels}x${metrics.heightPixels}"
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "gfxinfo", packageName))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val content = reader.readText()
            process.waitFor()
            
            if (content.isBlank()) {
                return@runCatching detectRenderResolutionFromSurfaceFlinger(packageName) ?: fallback
            }

            val regex = Regex("""(\d+)\s*\(\s*\d+\s*\)\s*x\s*(\d+)""")
            val matches = regex.findAll(content)
                .mapNotNull { match ->
                    val w = match.groupValues[1].toIntOrNull()
                    val h = match.groupValues[2].toIntOrNull()
                    if (w == null || h == null || w <= 0 || h <= 0) null else (w to h)
                }
                .toList()
                
            if (matches.isEmpty()) {
                return@runCatching detectRenderResolutionFromSurfaceFlinger(packageName) ?: fallback
            }

            val nativeLong = max(metrics.widthPixels, metrics.heightPixels)
            val nativeShort = min(metrics.widthPixels, metrics.heightPixels)
            val filtered = matches.filterNot { (w, h) ->
                (h == nativeLong && w == nativeShort) || (w == nativeLong && h == nativeShort) || h == nativeLong
            }
            val picked = filtered.firstOrNull() ?: matches.first()
            "${picked.first}x${picked.second}"
        }.getOrDefault(fallback)
    }

    private fun detectRenderResolutionFromSurfaceFlinger(packageName: String): String? {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "SurfaceFlinger", "--layers"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val out = reader.readText()
            process.waitFor()
            
            if (out.isBlank()) return@runCatching null

            val lines = out.lineSequence()
                .filter { line ->
                    line.contains(packageName, ignoreCase = true) &&
                        line.contains("(BLAST Consumer)", ignoreCase = true)
                }
                .toList()
            if (lines.isEmpty()) return@runCatching null

            val regex = Regex("""w/h:(\d+)x(\d+)""")
            val match = lines.asSequence()
                .mapNotNull { line -> regex.find(line) }
                .firstOrNull()
                ?: return@runCatching null
            val w = match.groupValues[1].toIntOrNull() ?: return@runCatching null
            val h = match.groupValues[2].toIntOrNull() ?: return@runCatching null
            if (w <= 0 || h <= 0) return@runCatching null
            "${w}x${h}"
        }.getOrNull()
    }
}
