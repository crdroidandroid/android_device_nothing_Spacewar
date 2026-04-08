/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

object FpsRecordImageGenerator {

    private const val WIDTH = 1080
    private const val PADDING = 40f
    private const val CARD_PADDING = 30f
    private const val CARD_SPACING = 40f
    private const val CHART_HEIGHT = 400f
    private const val CHART_CARD_HEIGHT = CHART_HEIGHT + 300f

    private data class Palette(
        val bg: Int,
        val card: Int,
        val text: Int,
        val sub: Int,
        val grid: Int,
        val label: Int,
        val blue: Int,
        val orange: Int,
        val cyan: Int,
        val fpsGreen: Int,
        val cpuTempCyan: Int,
        val gpuTempPink: Int,
        val ramUsageYellow: Int,
    )

    private data class Series(
        val label: String,
        val color: Int,
        val values: List<Float?>,
    )

    fun generateAndShareImage(context: Context, appName: String, analytics: LogAnalytics) {
        generateAndShareChartsImage(context, appName, analytics)
    }

    fun generateAndShareChartsImage(context: Context, appName: String, analytics: LogAnalytics) {
        val bitmap = generateChartsImage(context, appName, analytics)
        val file = File(context.externalCacheDir ?: context.cacheDir, "fps_record_${System.currentTimeMillis()}.png")
        runCatching {
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "FPS Record: $appName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share FPS graphics"))
        }
    }

    fun generateAndShareStatsImage(context: Context, appName: String, analytics: LogAnalytics) {
        val bitmap = generateStatsImage(context, appName, analytics)
        val file = File(context.externalCacheDir ?: context.cacheDir, "fps_record_stats_${System.currentTimeMillis()}.png")
        runCatching {
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "FPS Record Stats: $appName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share FPS stats graphics"))
        }
    }

    fun saveGraphics(context: Context, appName: String, analytics: LogAnalytics): Boolean {
        return saveChartsGraphics(context, appName, analytics)
    }

    fun saveChartsGraphics(context: Context, appName: String, analytics: LogAnalytics): Boolean {
        val bitmap = generateChartsImage(context, appName, analytics)
        val fileName = buildImageName(appName)

        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            true
        }.getOrElse { false }
    }

    fun saveStatsGraphics(context: Context, appName: String, analytics: LogAnalytics): Boolean {
        val bitmap = generateStatsImage(context, appName, analytics)
        val fileName = buildImageName("${appName}_stats")

        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            true
        }.getOrElse { false }
    }

    private fun generateChartsImage(context: Context, appName: String, analytics: LogAnalytics): Bitmap {
        val dark = isSystemDark(context)
        val p = palette(dark)
        val chartCount = 12
        val headerHeight = 220f
        val summaryHeight = 300f
        val totalHeight = (headerHeight + summaryHeight + (chartCount * (CHART_CARD_HEIGHT + CARD_SPACING)) + 120f).toInt()

        val bitmap = Bitmap.createBitmap(WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(p.bg)
        var y = PADDING + 20f

        y = drawHeader(canvas, appName, analytics, y, p)
        drawSummary(canvas, analytics, y, p)
        y += summaryHeight + CARD_SPACING

        val fpsValues = analytics.fpsTimeData.map { it.second.toFloat() }
        val batteryTemp = analytics.batteryTempTimeData.map { it.second.toFloat() }
        val frameTime = analytics.frameTimeData.map { it.second.toFloat() }
        val cpuUsage = analytics.cpuUsageTimeData.map { it.second.toFloat() }
        val cpuTemp = analytics.cpuTempTimeData.map { it.second.toFloat() }
        val gpuFreq = analytics.gpuClockTimeData.map { it.second.toFloat() }
        val gpuUsage = analytics.gpuUsageTimeData.map { it.second.toFloat() }
        val gpuTemp = analytics.gpuTempTimeData.map { it.second.toFloat() }
        val ramUsage = analytics.ramUsageTimeData.map { it.second.toFloat() }
        val ramFreq = analytics.ramSpeedTimeData.map { it.second.toFloat() }
        val ramTemp = analytics.ramTempTimeData.map { it.second.toFloat() }
        val power = analytics.powerTimeData.map { it.second.toFloat() }
        val capacity = analytics.batteryLevelTimeData.map { it.second.toFloat() }
        val cpuFreqSeries = buildCpuClusterSeries(analytics.cpuClockTimeData, dark)

        drawFixedAxisLineChartCard(
            canvas, "FPS", listOf(Series("FPS", p.fpsGreen, fpsValues)),
            y, 0f, (((fpsValues.maxOrNull() ?: 0f) / 30f).toInt() + 1).coerceAtLeast(2) * 30f, 30f, "", p,
            fillUnderFirstSeries = true, fillGradientColors = intArrayOf(0x804CAF50.toInt(), 0x104CAF50.toInt())
        )
        y += CHART_CARD_HEIGHT + CARD_SPACING

        drawFixedAxisLineChartCard(
            canvas, "Battery Temp (°C)", listOf(Series("Battery Temp", p.orange, batteryTemp)),
            y, 0f, (((batteryTemp.maxOrNull() ?: 0f) / 10f).toInt() + 1).coerceAtLeast(4) * 10f, 10f, "°C", p,
            fillUnderFirstSeries = true
        )
        y += CHART_CARD_HEIGHT + CARD_SPACING

        drawAreaChartCard(canvas, "Frame Time (ms)", frameTime, y, p)
        y += CHART_CARD_HEIGHT + CARD_SPACING

        drawFixedAxisLineChartCard(
            canvas, "CPU Usage (%)", listOf(Series("Total", p.blue, cpuUsage)),
            y, 0f, 100f, 10f, "%", p,
            fillUnderFirstSeries = true
        )
        y += CHART_CARD_HEIGHT + CARD_SPACING

        drawFixedAxisLineChartCard(
            canvas, "CPU Freq (MHz)", cpuFreqSeries,
            y, 0f, (((cpuFreqSeries.flatMap { it.values }.filterNotNull().maxOrNull() ?: 0f) / 300f).toInt() + 1).coerceAtLeast(4) * 300f, 300f, "MHz", p
        )
        y += CHART_CARD_HEIGHT + CARD_SPACING

        drawFixedAxisLineChartCard(
            canvas, "CPU Temp (°C)", listOf(Series("Temp", p.cpuTempCyan, cpuTemp)),
            y, 0f, (((cpuTemp.maxOrNull() ?: 0f) / 10f).toInt() + 1).coerceAtLeast(4) * 10f, 10f, "°C", p,
            fillUnderFirstSeries = true
        )
        y += CHART_CARD_HEIGHT + CARD_SPACING

        drawGpuDualAxisChartCard(canvas, "GPU Frequency (MHz)", "Usage(%)", gpuFreq, gpuUsage, y, p)
        y += CHART_CARD_HEIGHT + CARD_SPACING

        drawFixedAxisLineChartCard(
            canvas, "GPU Temp (°C)", listOf(Series("Temp", p.gpuTempPink, gpuTemp)),
            y, 0f, (((gpuTemp.maxOrNull() ?: 0f) / 10f).toInt() + 1).coerceAtLeast(4) * 10f, 10f, "°C", p,
            fillUnderFirstSeries = true
        )
        y += CHART_CARD_HEIGHT + CARD_SPACING

        drawFixedAxisLineChartCard(
            canvas, "RAM Usage (MB)", listOf(Series("RAM Usage", p.ramUsageYellow, ramUsage)),
            y, 0f, (((ramUsage.maxOrNull() ?: 0f) / 512f).toInt() + 1).coerceAtLeast(4) * 512f, 512f, "MB", p,
            fillUnderFirstSeries = true, fillGradientColors = intArrayOf(0x80FFC107.toInt(), 0x10FFC107.toInt())
        )
        y += CHART_CARD_HEIGHT + CARD_SPACING

        drawFixedAxisLineChartCard(
            canvas, "RAM Frequency (MHz)", listOf(Series("RAM Freq", p.cyan, ramFreq)),
            y, 0f, (((ramFreq.maxOrNull() ?: 0f) / 200f).toInt() + 1).coerceAtLeast(4) * 200f, 200f, "MHz", p
        )
        y += CHART_CARD_HEIGHT + CARD_SPACING

        drawFixedAxisLineChartCard(
            canvas, "RAM Temp (°C)", listOf(Series("RAM Temp", p.orange, ramTemp)),
            y, 0f, (((ramTemp.maxOrNull() ?: 0f) / 10f).toInt() + 1).coerceAtLeast(4) * 10f, 10f, "°C", p,
            fillUnderFirstSeries = true
        )
        y += CHART_CARD_HEIGHT + CARD_SPACING

        if (power.isNotEmpty()) {
            drawPowerCapacityChartCard(canvas, "Power(W)", "Capacity %", power, capacity, y, p)
        } else {
            drawFixedAxisLineChartCard(
                canvas, "Battery Level (%)", listOf(Series("Capacity(%)", p.orange, capacity)),
                y, 0f, 100f, 10f, "%", p,
                fillUnderFirstSeries = true
            )
        }
        return bitmap
    }

    private data class StatEntry(
        val label: String,
        val value: String,
        val color: Int,
    )

    private data class StatSection(
        val title: String,
        val rows: List<StatEntry>,
    )

    private fun generateStatsImage(context: Context, appName: String, analytics: LogAnalytics): Bitmap {
        val dark = isSystemDark(context)
        val p = palette(dark)
        val sections = buildDetailedStatsSections(analytics, p)
        val sectionHeight = 56f
        val titleHeight = 36f
        val sectionPadding = 20f
        val totalRows = sections.sumOf { it.rows.size }
        val totalHeight = (PADDING + 200f + (sections.size * (titleHeight + sectionPadding)) + (totalRows * sectionHeight) + 120f).toInt()

        val bitmap = Bitmap.createBitmap(WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(p.bg)

        var y = PADDING + 20f
        y = drawHeader(canvas, appName, analytics, y, p)

        val cardRect = RectF(PADDING, y, WIDTH - PADDING, totalHeight - PADDING)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.card }
        canvas.drawRoundRect(cardRect, 30f, 30f, cardPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.sub
            textSize = 23f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.sub
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }

        var rowY = y + 46f
        sections.forEach { section ->
            canvas.drawText(section.title, PADDING + 24f, rowY, sectionPaint)
            rowY += 30f
            section.rows.forEach { row ->
                canvas.drawText(row.label, PADDING + 24f, rowY + 18f, labelPaint)
                valuePaint.color = row.color
                canvas.drawText(row.value, WIDTH - PADDING - 24f, rowY + 18f, valuePaint)
                rowY += sectionHeight
            }
            rowY += sectionPadding
        }

        return bitmap
    }

    private fun buildDetailedStatsSections(analytics: LogAnalytics, p: Palette): List<StatSection> {
        val fps = analytics.fpsTimeData.map { it.second.toFloat() }
        val fpsSorted = fps.sorted()
        val low5 = averageSlowestPercent(fpsSorted, 0.05f)
        val frame = analytics.frameTimeData.map { it.second.toFloat() }
        val frameSortedDesc = frame.sortedDescending()
        val frameBudget = 16.67f
        val frameVariance = variance(frame)
        val frameStd = sqrt(frameVariance.toDouble()).toFloat()
        val frameSpikes = frame.count { it > frameBudget * 2f }
        val droppedPct = if (frame.isEmpty()) 0f else (frame.count { it > frameBudget }.toFloat() / frame.size) * 100f

        val cpuUsage = analytics.cpuUsageTimeData.map { it.second.toFloat() }
        val cpuTemp = analytics.cpuTempTimeData.map { it.second.toFloat() }
        val batteryTemp = analytics.batteryTempTimeData.map { it.second.toFloat() }
        val gpuUsage = analytics.gpuUsageTimeData.map { it.second.toFloat() }
        val gpuFreq = analytics.gpuClockTimeData.map { it.second.toFloat() }
        val gpuTemp = analytics.gpuTempTimeData.map { it.second.toFloat() }
        val ram = analytics.ramUsageTimeData.map { it.second.toFloat() }
        val power = analytics.powerTimeData.map { it.second.toFloat() }
        val battery = analytics.batteryLevelTimeData.map { it.second.toFloat() }

        val gpuMax = gpuFreq.maxOrNull() ?: 0f
        val gpuMaxHit = if (gpuFreq.isNotEmpty() && gpuMax > 0f) (gpuFreq.count { it >= gpuMax }.toFloat() / gpuFreq.size) * 100f else 0f
        val hours = sessionHours(analytics)
        val drop = batteryDropPercent(battery)
        val drainRate = if (hours > 0f) drop / hours else 0f
        val totalWh = totalPowerWh(analytics)
        val clusterRows = buildClusterFreqRows(analytics.cpuClockTimeData)

        return listOf(
            StatSection(
                "FPS METRICS",
                listOf(
                    StatEntry("Avg FPS", formatValue(analytics.fpsStats.avgFps.toFloat()), toneFps(analytics.fpsStats.avgFps.toFloat())),
                    StatEntry("Min FPS", formatValue(analytics.fpsStats.minFps.toFloat()), toneFps(analytics.fpsStats.minFps.toFloat())),
                    StatEntry("Max FPS", formatValue(analytics.fpsStats.maxFps.toFloat()), p.blue),
                    StatEntry("1% Low FPS", formatValue(analytics.fpsStats.fps1PercentLow.toFloat()), toneFps(analytics.fpsStats.fps1PercentLow.toFloat())),
                    StatEntry("0.1% Low FPS", formatValue(analytics.fpsStats.fps0_1PercentLow.toFloat()), toneFps(analytics.fpsStats.fps0_1PercentLow.toFloat())),
                    StatEntry("5% Low FPS", formatValue(low5), toneFps(low5)),
                    StatEntry("FPS Variance", formatValue(analytics.fpsStats.variance.toFloat()), p.blue),
                    StatEntry("FPS Std Deviation", formatValue(analytics.fpsStats.standardDeviation.toFloat()), p.blue),
                    StatEntry("Smoothness", "${formatValue(analytics.fpsStats.smoothnessPercentage.toFloat())}%", toneSmooth(analytics.fpsStats.smoothnessPercentage.toFloat())),
                )
            ),
            StatSection(
                "FRAMETIME METRICS (ms)",
                listOf(
                    StatEntry("Avg Frametime", formatValue(avg(frame)), toneFrame(avg(frame))),
                    StatEntry("1% High Frametime", formatValue(topPercent(frameSortedDesc, 0.01f)), toneFrame(topPercent(frameSortedDesc, 0.01f))),
                    StatEntry("0.1% High Frametime", formatValue(topPercent(frameSortedDesc, 0.001f)), toneFrame(topPercent(frameSortedDesc, 0.001f))),
                    StatEntry("Frametime Variance", formatValue(frameVariance), p.blue),
                    StatEntry("Frametime Std Deviation", formatValue(frameStd), p.blue),
                    StatEntry("Frame Spikes (>33.3ms)", frameSpikes.toString(), if (frameSpikes == 0) Color.parseColor("#3FB950") else if (frameSpikes < 10) Color.parseColor("#FFB347") else Color.parseColor("#FF6B6B")),
                    StatEntry("Dropped Frames", "${formatValue(droppedPct)}%", if (droppedPct < 1f) Color.parseColor("#3FB950") else if (droppedPct < 5f) Color.parseColor("#FFB347") else Color.parseColor("#FF6B6B")),
                )
            ),
            StatSection(
                "CPU METRICS",
                buildList {
                    add(StatEntry("Total CPU Usage (%)", rangeTriple(cpuUsage), toneCpu(avg(cpuUsage))))
                    addAll(clusterRows)
                    add(StatEntry("CPU Temperature (°C)", rangeTriple(cpuTemp), toneTemp(avg(cpuTemp))))
                    add(StatEntry("Device Temp (Battery °C)", rangeTriple(batteryTemp), toneTemp(avg(batteryTemp))))
                }
            ),
            StatSection(
                "GPU METRICS",
                listOf(
                    StatEntry("GPU Usage (%)", rangeTriple(gpuUsage), toneCpu(avg(gpuUsage))),
                    StatEntry("GPU Frequency (MHz)", rangeTriple(gpuFreq), p.blue),
                    StatEntry("GPU Max Frequency Hit", "${formatValue(gpuMaxHit)}%", if (gpuMaxHit > 40f) Color.parseColor("#3FB950") else if (gpuMaxHit > 15f) Color.parseColor("#FFB347") else Color.parseColor("#FF6B6B")),
                    StatEntry("GPU Temperature (°C)", rangeTriple(gpuTemp), toneTemp(avg(gpuTemp))),
                )
            ),
            StatSection(
                "RAM / MEMORY METRICS",
                listOf(
                    StatEntry("RAM Usage (MB)", rangeTriple(ram), p.blue),
                )
            ),
            StatSection(
                "POWER METRICS",
                listOf(
                    StatEntry("Total Power Consumption", "${formatValue(totalWh)} Wh", p.blue),
                    StatEntry("Total Battery % Dropped", "${formatValue(drop)}%", if (drop < 3f) Color.parseColor("#3FB950") else if (drop < 8f) Color.parseColor("#FFB347") else Color.parseColor("#FF6B6B")),
                    StatEntry("Average Power Consumption", "${formatValue(analytics.powerStats.avgPower.toFloat())} W", if (analytics.powerStats.avgPower < 3.0) Color.parseColor("#3FB950") else if (analytics.powerStats.avgPower < 6.0) Color.parseColor("#FFB347") else Color.parseColor("#FF6B6B")),
                    StatEntry("Battery Drain Rate", "${formatValue(drainRate)} %/h", if (drainRate < 5f) Color.parseColor("#3FB950") else if (drainRate < 12f) Color.parseColor("#FFB347") else Color.parseColor("#FF6B6B")),
                )
            )
        )
    }

    private fun drawHeader(canvas: Canvas, appName: String, analytics: LogAnalytics, startY: Float, p: Palette): Float {
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.text
            textSize = 50f
            typeface = Typeface.DEFAULT_BOLD
        }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.sub
            textSize = 30f
            typeface = Typeface.DEFAULT
        }
        var y = startY + 36f
        canvas.drawText(appName, PADDING, y, title)
        y += 50f
        if (analytics.resolution != "Unknown") {
            val resText = "Res: ${analytics.resolution}"
            val resWidth = sub.measureText(resText)
            canvas.drawText(resText, WIDTH - PADDING - resWidth, y, sub)
        }
        canvas.drawText("${analytics.sessionDate} • ${analytics.sessionDuration}", PADDING, y, sub)
        return y + 40f
    }

    private fun drawSummary(canvas: Canvas, analytics: LogAnalytics, startY: Float, p: Palette) {
        val rect = RectF(PADDING, startY, WIDTH - PADDING, startY + 300f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.card }
        canvas.drawRoundRect(rect, 30f, 30f, paint)

        val maxTemp = max(analytics.cpuStats.maxTemp.toFloat(), analytics.gpuStats.maxTemp.toFloat())
        val fpsValues = analytics.fpsTimeData.map { it.second.toFloat() }.sorted()
        val low5 = averageSlowestPercent(fpsValues, 0.05f)
        val items = listOf(
            Triple("MAX", formatValue(analytics.fpsStats.maxFps.toFloat()), "FPS"),
            Triple("MIN", formatValue(analytics.fpsStats.minFps.toFloat()), "FPS"),
            Triple("AVERAGE", formatValue(analytics.fpsStats.avgFps.toFloat()), "FPS"),
            Triple("1% LOW", formatValue(analytics.fpsStats.fps1PercentLow.toFloat()), "FPS"),
            Triple("0.1% LOW", formatValue(analytics.fpsStats.fps0_1PercentLow.toFloat()), "FPS"),
            Triple("5% LOW", formatValue(low5), "FPS"),
            Triple("MAX", formatValue(maxTemp), "Temperature"),
            Triple("AVERAGE", formatValue(analytics.powerStats.avgPower.toFloat()), "Power(Watt)"),
        )
        val colW = rect.width() / 4f
        var rowY = rect.top + 56f
        items.chunked(4).forEach { row ->
            row.forEachIndexed { index, (label, value, unit) ->
                val x = rect.left + (index * colW) + colW / 2f
                paint.color = p.sub
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 24f
                paint.typeface = Typeface.DEFAULT
                canvas.drawText(label, x, rowY, paint)
                paint.color = p.text
                paint.textSize = 42f
                paint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText(value, x, rowY + 48f, paint)
                paint.color = p.sub
                paint.textSize = 22f
                paint.typeface = Typeface.DEFAULT
                canvas.drawText(unit, x, rowY + 84f, paint)
            }
            rowY += 138f
        }
    }

    private fun drawCardBackground(canvas: Canvas, title: String, rightTitle: String?, startY: Float, p: Palette) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.card }
        val rect = RectF(PADDING, startY, WIDTH - PADDING, startY + CHART_CARD_HEIGHT)
        canvas.drawRoundRect(rect, 30f, 30f, paint)

        paint.color = p.sub
        paint.textSize = 32f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(title, PADDING + CARD_PADDING, startY + 50f, paint)
        if (!rightTitle.isNullOrBlank()) {
            paint.textSize = 24f
            paint.typeface = Typeface.DEFAULT
            val w = paint.measureText(rightTitle)
            canvas.drawText(rightTitle, WIDTH - PADDING - CARD_PADDING - w, startY + 45f, paint)
        }
    }

    private fun drawLegend(canvas: Canvas, series: List<Series>, startY: Float, p: Palette) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var x = PADDING + CARD_PADDING
        val y = startY + CHART_HEIGHT + 220f
        series.forEach { s ->
            paint.color = s.color
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x + 10f, y - 8f, 8f, paint)
            paint.color = p.sub
            paint.textSize = 24f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(s.label, x + 30f, y, paint)
            x += paint.measureText(s.label) + 60f
        }
    }

    private fun drawFooter(canvas: Canvas, series: List<Series>, startY: Float, unit: String, p: Palette) {
        val values = series.flatMap { it.values }.filterNotNull()
        val maxText = if (values.isEmpty()) "--" else formatValue(values.maxOrNull() ?: 0f) + unit
        val minText = if (values.isEmpty()) "--" else formatValue(values.minOrNull() ?: 0f) + unit
        val avgText = if (values.isEmpty()) "--" else formatValue(values.average().toFloat()) + unit
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.sub
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        val colW = (WIDTH - 2 * PADDING) / 3f
        val y = startY + CHART_HEIGHT + 280f
        canvas.drawText("MAX: $maxText", PADDING + colW / 2f, y, paint)
        canvas.drawText("MIN: $minText", PADDING + colW * 1.5f, y, paint)
        canvas.drawText("AVG: $avgText", PADDING + colW * 2.5f, y, paint)
    }

    private fun drawFixedAxisLineChartCard(
        canvas: Canvas,
        title: String,
        series: List<Series>,
        startY: Float,
        yMin: Float,
        yMax: Float,
        yStep: Float,
        unit: String,
        p: Palette,
        fillUnderFirstSeries: Boolean = false,
        fillGradientColors: IntArray? = null,
    ) {
        drawCardBackground(canvas, title, null, startY, p)
        val plotLeft = PADDING + CARD_PADDING + 44f
        val plotRight = WIDTH - PADDING - CARD_PADDING - 16f
        val plotTop = startY + 130f
        val plotBottom = plotTop + CHART_HEIGHT
        val plotWidth = plotRight - plotLeft
        val range = (yMax - yMin).coerceAtLeast(1f)
        val dash = DashPathEffect(floatArrayOf(8f, 8f), 0f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val steps = ((yMax - yMin) / yStep).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val y = plotTop + CHART_HEIGHT * i / steps
            paint.color = p.grid
            paint.pathEffect = dash
            canvas.drawLine(plotLeft, y, plotRight, y, paint)
            paint.pathEffect = null
            paint.color = p.label
            paint.style = Paint.Style.FILL
            paint.textSize = 22f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatAxis(yMax - yStep * i), plotLeft - 10f, y - 6f, paint)
            paint.style = Paint.Style.STROKE
        }

        val samples = series.maxOfOrNull { it.values.size } ?: 0
        val xSteps = 4
        for (i in 0..xSteps) {
            val x = plotLeft + plotWidth * i / xSteps
            paint.color = p.grid
            paint.pathEffect = dash
            canvas.drawLine(x, plotTop, x, plotBottom, paint)
            paint.pathEffect = null
            paint.color = p.label
            paint.style = Paint.Style.FILL
            paint.textSize = 22f
            paint.textAlign = when (i) {
                0 -> Paint.Align.LEFT
                xSteps -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText(formatTimeLabel(i, xSteps, samples), x, plotBottom + 30f, paint)
            paint.style = Paint.Style.STROKE
        }

        series.forEachIndexed { seriesIndex, s ->
            val values = s.values
            if (values.size <= 1) return@forEachIndexed
            val path = Path()
            val fillPath = Path()
            var started = false
            values.forEachIndexed { index, value ->
                val v = value ?: return@forEachIndexed
                val x = plotLeft + plotWidth * index / (values.size - 1).coerceAtLeast(1)
                val y = plotBottom - ((v.coerceIn(yMin, yMax) - yMin) / range) * CHART_HEIGHT
                if (!started) {
                    path.moveTo(x, y)
                    if (fillUnderFirstSeries && seriesIndex == 0) {
                        fillPath.moveTo(x, plotBottom)
                        fillPath.lineTo(x, y)
                    }
                    started = true
                } else {
                    path.lineTo(x, y)
                    if (fillUnderFirstSeries && seriesIndex == 0) {
                        fillPath.lineTo(x, y)
                    }
                }
            }
            if (started) {
                if (fillUnderFirstSeries && seriesIndex == 0) {
                    fillPath.lineTo(plotLeft + plotWidth, plotBottom)
                    fillPath.close()
                    val gradientColors = fillGradientColors ?: intArrayOf(
                        (s.color and 0x00FFFFFF) or (115 shl 24),
                        (s.color and 0x00FFFFFF) or (20 shl 24)
                    )
                    paint.style = Paint.Style.FILL
                    paint.shader = android.graphics.LinearGradient(
                        0f, plotTop, 0f, plotBottom,
                        gradientColors, null, android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawPath(fillPath, paint)
                    paint.shader = null
                }
                paint.color = s.color
                paint.strokeWidth = 3f
                paint.style = Paint.Style.STROKE
                canvas.drawPath(path, paint)
            }
        }
        drawLegend(canvas, series, startY, p)
        drawFooter(canvas, series, startY, unit, p)
    }

    private fun drawAreaChartCard(
        canvas: Canvas,
        title: String,
        values: List<Float>,
        startY: Float,
        p: Palette,
    ) {
        drawCardBackground(canvas, title, null, startY, p)
        val plotLeft = PADDING + CARD_PADDING + 44f
        val plotRight = WIDTH - PADDING - CARD_PADDING - 16f
        val plotTop = startY + 130f
        val plotBottom = plotTop + CHART_HEIGHT
        val plotWidth = plotRight - plotLeft
        val v = if (values.isEmpty()) listOf(0f) else values
        val maxY = (v.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val dash = DashPathEffect(floatArrayOf(8f, 8f), 0f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val steps = 6
        for (i in 0..steps) {
            val y = plotTop + CHART_HEIGHT * i / steps
            paint.color = p.grid
            paint.pathEffect = dash
            canvas.drawLine(plotLeft, y, plotRight, y, paint)
            paint.pathEffect = null
            paint.color = p.label
            paint.style = Paint.Style.FILL
            paint.textSize = 22f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatAxis(maxY - (maxY * i / steps)), plotLeft - 10f, y - 6f, paint)
            paint.style = Paint.Style.STROKE
        }

        val xSteps = 4
        for (i in 0..xSteps) {
            val x = plotLeft + plotWidth * i / xSteps
            paint.color = p.grid
            paint.pathEffect = dash
            canvas.drawLine(x, plotTop, x, plotBottom, paint)
            paint.pathEffect = null
            paint.color = p.label
            paint.style = Paint.Style.FILL
            paint.textSize = 22f
            paint.textAlign = when (i) {
                0 -> Paint.Align.LEFT
                xSteps -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText(formatTimeLabel(i, xSteps, v.size), x, plotBottom + 30f, paint)
            paint.style = Paint.Style.STROKE
        }

        val line = Path()
        val fill = Path()
        v.forEachIndexed { index, value ->
            val x = plotLeft + plotWidth * index / (v.size - 1).coerceAtLeast(1)
            val y = plotBottom - (value / maxY) * CHART_HEIGHT
            if (index == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, plotBottom)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(plotLeft + plotWidth, plotBottom)
        fill.close()
        paint.style = Paint.Style.FILL
        paint.color = p.blue
        paint.alpha = 90
        canvas.drawPath(fill, paint)
        paint.style = Paint.Style.STROKE
        paint.color = p.blue
        paint.alpha = 255
        paint.strokeWidth = 3f
        canvas.drawPath(line, paint)

        drawFooter(canvas, listOf(Series("Frame Time", p.blue, values)), startY, "ms", p)
    }

    private fun drawGpuDualAxisChartCard(
        canvas: Canvas,
        title: String,
        rightTitle: String,
        freq: List<Float>,
        usage: List<Float>,
        startY: Float,
        p: Palette,
    ) {
        drawCardBackground(canvas, title, rightTitle, startY, p)
        val plotLeft = PADDING + CARD_PADDING + 70f
        val plotRight = WIDTH - PADDING - CARD_PADDING - 58f
        val plotTop = startY + 130f
        val plotBottom = plotTop + CHART_HEIGHT
        val plotWidth = plotRight - plotLeft
        val dash = DashPathEffect(floatArrayOf(8f, 8f), 0f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val f = if (freq.isEmpty()) listOf(0f) else freq
        val u = if (usage.isEmpty()) listOf(0f) else usage
        val freqUpper = ((f.maxOrNull() ?: 0f) / 100f).toInt().plus(1).coerceAtLeast(1) * 100f
        val usageSteps = listOf(0f, 50f, 75f, 90f, 100f)
        val steps = 5

        for (i in 0..steps) {
            val y = plotTop + CHART_HEIGHT * i / steps
            paint.color = p.grid
            paint.pathEffect = dash
            canvas.drawLine(plotLeft, y, plotRight, y, paint)
            paint.pathEffect = null
            paint.color = p.blue
            paint.style = Paint.Style.FILL
            paint.textSize = 22f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatAxis(freqUpper - (freqUpper * i / steps)), plotLeft - 10f, y - 6f, paint)
            paint.style = Paint.Style.STROKE
        }

        usageSteps.forEach { value ->
            val y = plotBottom - (value / 100f) * CHART_HEIGHT
            paint.color = p.orange
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 22f
            canvas.drawText(formatAxis(value), plotRight + 8f, y - 6f, paint)
            paint.style = Paint.Style.STROKE
        }

        val xSteps = 4
        for (i in 0..xSteps) {
            val x = plotLeft + plotWidth * i / xSteps
            paint.color = p.grid
            paint.pathEffect = dash
            canvas.drawLine(x, plotTop, x, plotBottom, paint)
            paint.pathEffect = null
            paint.color = p.label
            paint.style = Paint.Style.FILL
            paint.textSize = 22f
            paint.textAlign = when (i) {
                0 -> Paint.Align.LEFT
                xSteps -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText(formatTimeLabel(i, xSteps, max(f.size, u.size)), x, plotBottom + 30f, paint)
            paint.style = Paint.Style.STROKE
        }

        if (f.size > 1) {
            val path = Path()
            f.forEachIndexed { index, value ->
                val x = plotLeft + plotWidth * index / (f.size - 1).coerceAtLeast(1)
                val y = plotBottom - (value / freqUpper).coerceIn(0f, 1f) * CHART_HEIGHT
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paint.color = p.blue
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawPath(path, paint)
        }

        if (u.size > 1) {
            val path = Path()
            u.forEachIndexed { index, value ->
                val x = plotLeft + plotWidth * index / (u.size - 1).coerceAtLeast(1)
                val y = plotBottom - (value.coerceIn(0f, 100f) / 100f) * CHART_HEIGHT
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paint.color = p.orange
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawPath(path, paint)
        }

        drawLegend(
            canvas,
            listOf(Series("Frequency(MHz)", p.blue, emptyList()), Series("Usage(%)", p.orange, emptyList())),
            startY,
            p
        )
        drawFooter(canvas, listOf(Series("Frequency", p.blue, freq)), startY, "MHz", p)
    }

    private fun drawPowerCapacityChartCard(
        canvas: Canvas,
        title: String,
        rightTitle: String,
        power: List<Float>,
        capacity: List<Float>,
        startY: Float,
        p: Palette,
    ) {
        drawCardBackground(canvas, title, rightTitle, startY, p)
        val plotLeft = PADDING + CARD_PADDING + 70f
        val plotRight = WIDTH - PADDING - CARD_PADDING - 58f
        val plotTop = startY + 130f
        val plotBottom = plotTop + CHART_HEIGHT
        val plotWidth = plotRight - plotLeft
        val dash = DashPathEffect(floatArrayOf(8f, 8f), 0f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val pwr = if (power.isEmpty()) listOf(0f) else power
        val cap = if (capacity.isEmpty()) listOf(0f) else capacity
        val powerUpper = ceil((pwr.maxOrNull() ?: 1f).toDouble()).toFloat().coerceAtLeast(1f)
        val steps = 5
        val capSteps = listOf(0f, 20f, 40f, 60f, 80f, 100f)

        for (i in 0..steps) {
            val y = plotTop + CHART_HEIGHT * i / steps
            paint.color = p.grid
            paint.pathEffect = dash
            canvas.drawLine(plotLeft, y, plotRight, y, paint)
            paint.pathEffect = null
            paint.color = p.blue
            paint.style = Paint.Style.FILL
            paint.textSize = 22f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatAxis(powerUpper - (powerUpper * i / steps)), plotLeft - 10f, y - 6f, paint)
            paint.style = Paint.Style.STROKE
        }

        capSteps.forEach { value ->
            val y = plotBottom - (value / 100f) * CHART_HEIGHT
            paint.color = p.orange
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 22f
            canvas.drawText(formatAxis(value), plotRight + 8f, y - 6f, paint)
            paint.style = Paint.Style.STROKE
        }

        val xSteps = 4
        for (i in 0..xSteps) {
            val x = plotLeft + plotWidth * i / xSteps
            paint.color = p.grid
            paint.pathEffect = dash
            canvas.drawLine(x, plotTop, x, plotBottom, paint)
            paint.pathEffect = null
            paint.color = p.label
            paint.style = Paint.Style.FILL
            paint.textSize = 22f
            paint.textAlign = when (i) {
                0 -> Paint.Align.LEFT
                xSteps -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText(formatTimeLabel(i, xSteps, max(pwr.size, cap.size)), x, plotBottom + 30f, paint)
            paint.style = Paint.Style.STROKE
        }

        if (pwr.size > 1) {
            val path = Path()
            pwr.forEachIndexed { index, value ->
                val x = plotLeft + plotWidth * index / (pwr.size - 1).coerceAtLeast(1)
                val y = plotBottom - (value / powerUpper).coerceIn(0f, 1f) * CHART_HEIGHT
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paint.color = p.blue
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawPath(path, paint)
        }

        if (cap.size > 1) {
            val path = Path()
            cap.forEachIndexed { index, value ->
                val x = plotLeft + plotWidth * index / (cap.size - 1).coerceAtLeast(1)
                val y = plotBottom - (value.coerceIn(0f, 100f) / 100f) * CHART_HEIGHT
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paint.color = p.orange
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawPath(path, paint)
        }

        drawLegend(
            canvas,
            listOf(Series("Power(W)", p.blue, emptyList()), Series("Capacity(%)", p.orange, emptyList())),
            startY,
            p
        )
        drawFooter(canvas, listOf(Series("Power", p.blue, power)), startY, "W", p)
    }

    private fun buildCpuClusterSeries(
        cpuClockTimeData: Map<Int, List<Pair<Long, Double>>>,
        dark: Boolean,
    ): List<Series> {
        val groups = CpuClusterDetermination.resolveClusters(cpuClockTimeData)
        return groups.mapIndexed { index, cores ->
            Series(
                label = "Cluster ${index + 1} (${cores.joinToString(" ")})",
                color = cpuClusterColor(index, dark),
                values = mergeCoreSeries(cores, cpuClockTimeData),
            )
        }
    }

    private fun mergeCoreSeries(
        cores: List<Int>,
        cpuClockTimeData: Map<Int, List<Pair<Long, Double>>>,
    ): List<Float?> {
        val series = cores.map { core -> cpuClockTimeData[core].orEmpty().map { it.second.toFloat() } }
        val maxSamples = series.maxOfOrNull { it.size } ?: 0
        return List(maxSamples) { i ->
            val values = series.mapNotNull { it.getOrNull(i) }.filter { it > 0f }
            if (values.isEmpty()) null else values.average().toFloat()
        }
    }

    private fun cpuClusterColor(index: Int, dark: Boolean): Int {
        return if (dark) {
            when (index % 4) {
                0 -> Color.parseColor("#8774E7")
                1 -> Color.parseColor("#08D2D4")
                2 -> Color.parseColor("#FF8626")
                else -> Color.parseColor("#A78BFA")
            }
        } else {
            when (index % 4) {
                0 -> Color.parseColor("#5B21B6")
                1 -> Color.parseColor("#007A99")
                2 -> Color.parseColor("#B54D00")
                else -> Color.parseColor("#4338CA")
            }
        }
    }

    private fun averageSlowestPercent(values: List<Float>, percent: Float): Float {
        if (values.isEmpty()) return 0f
        val count = (values.size * percent).toInt().coerceAtLeast(1)
        return values.take(count).average().toFloat()
    }

    private fun variance(values: List<Float>): Float {
        if (values.size <= 1) return 0f
        val avg = values.average().toFloat()
        return values.sumOf { ((it - avg) * (it - avg)).toDouble() }.toFloat() / values.size
    }

    private fun topPercent(sortedDesc: List<Float>, percent: Float): Float {
        if (sortedDesc.isEmpty()) return 0f
        val count = (sortedDesc.size * percent).toInt().coerceAtLeast(1)
        return sortedDesc.take(count).average().toFloat()
    }

    private fun avg(values: List<Float>): Float = if (values.isEmpty()) 0f else values.average().toFloat()

    private fun rangeTriple(values: List<Float>): String {
        if (values.isEmpty()) return "--"
        val max = values.maxOrNull() ?: 0f
        val min = values.minOrNull() ?: 0f
        val avg = avg(values)
        return "Max ${formatValue(max)} / Min ${formatValue(min)} / Avg ${formatValue(avg)}"
    }

    private fun buildClusterFreqRows(cpuClockTimeData: Map<Int, List<Pair<Long, Double>>>): List<StatEntry> {
        if (cpuClockTimeData.isEmpty()) return listOf(StatEntry("Per-cluster Metrics", "N/A", Color.parseColor("#58A6FF")))
        val groups = CpuClusterDetermination.resolveClusters(cpuClockTimeData)
        val labels = listOf("Little", "Mid", "Big")
        return groups.mapIndexed { index, cores ->
            val merged = mergeCoreSeries(cores, cpuClockTimeData).mapNotNull { it }.filter { it > 0f }
            if (merged.isEmpty()) {
                StatEntry("${labels.getOrElse(index) { "Cluster ${index + 1}" }}", "N/A", Color.parseColor("#58A6FF"))
            } else {
                val avg = avg(merged)
                val max = merged.maxOrNull() ?: 0f
                val est = if (max > 0f) ((avg / max) * 100f).coerceIn(0f, 100f) else 0f
                StatEntry(
                    "${labels.getOrElse(index) { "Cluster ${index + 1}" }} (Est / Avg / Max)",
                    "${formatValue(est)}% / ${formatValue(avg)} MHz / ${formatValue(max)} MHz",
                    toneCpu(est)
                )
            }
        }
    }

    private fun sessionHours(analytics: LogAnalytics): Float {
        val maxMs = analytics.fpsTimeData.maxOfOrNull { it.first } ?: 0L
        if (maxMs > 0L) return (maxMs / 1000f) / 3600f
        val parts = analytics.sessionDuration.lowercase(Locale.getDefault())
        val mins = Regex("(\\d+)m").find(parts)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val secs = Regex("(\\d+)s").find(parts)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        return (mins * 60f + secs) / 3600f
    }

    private fun batteryDropPercent(levels: List<Float>): Float {
        if (levels.size < 2) return 0f
        return (levels.first() - levels.last()).coerceAtLeast(0f)
    }

    private fun totalPowerWh(analytics: LogAnalytics): Float {
        val data = analytics.powerTimeData
        if (data.isEmpty()) return 0f
        if (data.size == 1) return data.first().second.toFloat() / 3600f
        var wh = 0f
        for (i in 1 until data.size) {
            val prev = data[i - 1]
            val cur = data[i]
            val dtHours = ((cur.first - prev.first).coerceAtLeast(0L) / 1000f) / 3600f
            val avgW = ((prev.second + cur.second) / 2.0).toFloat()
            wh += avgW * dtHours
        }
        return wh
    }

    private fun toneFps(value: Float): Int =
        if (value >= 55f) Color.parseColor("#3FB950") else if (value >= 35f) Color.parseColor("#FFB347") else Color.parseColor("#FF6B6B")

    private fun toneSmooth(value: Float): Int =
        if (value >= 95f) Color.parseColor("#3FB950") else if (value >= 80f) Color.parseColor("#FFB347") else Color.parseColor("#FF6B6B")

    private fun toneFrame(ms: Float): Int =
        if (ms <= 16.67f) Color.parseColor("#3FB950") else if (ms <= 25f) Color.parseColor("#FFB347") else Color.parseColor("#FF6B6B")

    private fun toneTemp(c: Float): Int =
        if (c < 45f) Color.parseColor("#3FB950") else if (c < 55f) Color.parseColor("#FFB347") else Color.parseColor("#FF6B6B")

    private fun toneCpu(usage: Float): Int =
        if (usage < 55f) Color.parseColor("#3FB950") else if (usage < 80f) Color.parseColor("#FFB347") else Color.parseColor("#FF6B6B")

    private fun formatValue(value: Float): String =
        if (value <= 0f) "0.0" else String.format(Locale.getDefault(), "%.1f", value)

    private fun formatAxis(value: Float): String =
        if (value >= 1000f) String.format(Locale.getDefault(), "%.0f", value)
        else String.format(Locale.getDefault(), "%.1f", value)

    private fun formatTimeLabel(index: Int, steps: Int, samples: Int): String {
        if (samples <= 1) return "0s"
        val sec = ((samples - 1) * index / steps)
        val mins = sec / 60
        val secs = sec % 60
        return if (mins > 0) "${mins}m${secs}s" else "${secs}s"
    }

    private fun buildImageName(appNameRaw: String): String {
        val appName = appNameRaw.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "FpsRecord_${appName}_$ts.png"
    }

    private fun isSystemDark(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun palette(dark: Boolean): Palette {
        return if (dark) {
            Palette(
                bg = Color.parseColor("#000000"),
                card = Color.parseColor("#000000"),
                text = Color.parseColor("#E6EDF3"),
                sub = Color.parseColor("#8B949E"),
                grid = Color.parseColor("#30FFFFFF"),
                label = Color.parseColor("#B3FFFFFF"),
                blue = Color.parseColor("#58A6FF"),
                orange = Color.parseColor("#FF8626"),
                cyan = Color.parseColor("#3FB950"),
                fpsGreen = Color.parseColor("#4CAF50"),
                cpuTempCyan = Color.parseColor("#00BCD4"),
                gpuTempPink = Color.parseColor("#E91E63"),
                ramUsageYellow = Color.parseColor("#FFC107"),
            )
        } else {
            Palette(
                bg = Color.parseColor("#F2F4F7"),
                card = Color.parseColor("#FFFFFF"),
                text = Color.parseColor("#111827"),
                sub = Color.parseColor("#8B949E"),
                grid = Color.parseColor("#30000000"),
                label = Color.parseColor("#99000000"),
                blue = Color.parseColor("#58A6FF"),
                orange = Color.parseColor("#FF8626"),
                cyan = Color.parseColor("#00BCD4"),
                fpsGreen = Color.parseColor("#4CAF50"),
                cpuTempCyan = Color.parseColor("#00BCD4"),
                gpuTempPink = Color.parseColor("#E91E63"),
                ramUsageYellow = Color.parseColor("#FFC107"),
            )
        }
    }
}
