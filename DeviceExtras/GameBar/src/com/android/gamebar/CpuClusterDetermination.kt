/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import java.io.File
import android.util.Log
import kotlin.math.abs
import kotlin.math.max

object CpuClusterDetermination {
    private const val TAG = "CpuClusterDetermination"
    @Volatile
    private var cachedPolicyClusters: List<List<Int>>? = null

    fun resolveClusters(
        cpuClockTimeData: Map<Int, List<Pair<Long, Double>>>
    ): List<List<Int>> {
        if (cpuClockTimeData.isEmpty()) return emptyList()
        val recordedCoreIds = cpuClockTimeData.keys.sorted()
        if (recordedCoreIds.isEmpty()) return emptyList()

        val policyClusters = getPolicyClusters()
        if (policyClusters.isNotEmpty()) {
            val filtered = policyClusters
                .map { cluster -> cluster.filter { core -> core in recordedCoreIds } }
                .filter { it.isNotEmpty() }

            if (filtered.isNotEmpty()) {
                val used = filtered.flatten().toSet()
                val leftovers = recordedCoreIds.filter { it !in used }
                if (leftovers.isEmpty()) return filtered
                return filtered + groupContiguous(leftovers)
            }
        }

        return fallbackByFrequencyGap(cpuClockTimeData, recordedCoreIds)
    }

    private fun getPolicyClusters(): List<List<Int>> {
        cachedPolicyClusters?.let { return it }

        val cpufreqDir = File("/sys/devices/system/cpu/cpufreq")
        if (!cpufreqDir.exists() || !cpufreqDir.isDirectory) return emptyList()

        val clusters = runCatching {
            cpufreqDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("policy") }
                ?.sortedBy { it.name.removePrefix("policy").toIntOrNull() ?: Int.MAX_VALUE }
                ?.mapNotNull { policyDir ->
                    val relatedCpus = File(policyDir, "related_cpus")
                    readRelatedCoresSafely(relatedCpus)
                }
                .orEmpty()
        }.getOrElse {
            Log.w(TAG, "Failed to discover policy clusters", it)
            emptyList()
        }

        if (clusters.isNotEmpty()) {
            cachedPolicyClusters = clusters
        }
        return clusters
    }

    private fun readRelatedCoresSafely(file: File): List<Int>? {
        if (!file.exists() || !file.canRead()) return null

        val content = runCatching { file.bufferedReader().use { it.readLine() ?: "" } }
            .getOrElse {
                Log.w(TAG, "Skipping busy/unreadable node: ${file.absolutePath}", it)
                return null
            }

        val cores = content.trim()
            .split(Regex("\\s+"))
            .mapNotNull { it.toIntOrNull() }
            .sorted()

        return if (cores.isEmpty()) null else cores
    }

    private fun fallbackByFrequencyGap(
        cpuClockTimeData: Map<Int, List<Pair<Long, Double>>>,
        sortedCoreIds: List<Int>,
    ): List<List<Int>> {
        val averages = sortedCoreIds.associateWith { coreId ->
            val values = cpuClockTimeData[coreId].orEmpty().map { it.second.toFloat() }.filter { it > 0f }
            if (values.isEmpty()) 0f else values.average().toFloat()
        }

        val groups = mutableListOf<MutableList<Int>>()
        for (coreId in sortedCoreIds) {
            if (groups.isEmpty()) {
                groups.add(mutableListOf(coreId))
                continue
            }
            val current = groups.last()
            val prevCoreId = current.last()
            val prevAvg = averages[prevCoreId] ?: 0f
            val nowAvg = averages[coreId] ?: 0f
            val base = max(prevAvg, nowAvg).coerceAtLeast(1f)
            val ratioDelta = abs(nowAvg - prevAvg) / base

            if (coreId == prevCoreId + 1 && ratioDelta <= 0.18f) {
                current.add(coreId)
            } else {
                groups.add(mutableListOf(coreId))
            }
        }
        return groups
    }

    private fun groupContiguous(coreIds: List<Int>): List<List<Int>> {
        if (coreIds.isEmpty()) return emptyList()
        val sorted = coreIds.sorted()
        val groups = mutableListOf<MutableList<Int>>()
        for (id in sorted) {
            if (groups.isEmpty()) {
                groups.add(mutableListOf(id))
                continue
            }
            val g = groups.last()
            if (id == g.last() + 1) g.add(id) else groups.add(mutableListOf(id))
        }
        return groups
    }
}
