/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

private data class FpsSessionItem(
    val packageName: String,
    val appName: String,
    val file: File,
    val dateText: String,
    val avgFps: Float,
    val avgPower: Float,
    val duration: String,
    val resolution: String
)

private enum class SessionDetailTab {
    CHARTS,
    STATS,
}

@Composable
fun FpsRecordScreen(
    onBack: () -> Unit
) {
    var sessions by remember { mutableStateOf<List<FpsSessionItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selectedSession by remember { mutableStateOf<FpsSessionItem?>(null) }
    var selectedAnalytics by remember { mutableStateOf<LogAnalytics?>(null) }
    var loadingAnalytics by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    suspend fun refreshSessions() {
        sessions = withContext(Dispatchers.IO) { loadSessions(context.packageManager) }
    }
    LaunchedEffect(Unit) {
        refreshSessions()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    refreshSessions()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (selectedSession != null) {
        if (loadingAnalytics) {
            LaunchedEffect(selectedSession!!.file.absolutePath) {
                selectedAnalytics = withContext(Dispatchers.IO) {
                    PerAppLogReader().analyzeLogFile(selectedSession!!.file.absolutePath)
                }
                loadingAnalytics = false
            }
        }

        FpsRecordDetailScreen(
            session = selectedSession!!,
            analytics = selectedAnalytics,
            loading = loadingAnalytics,
            onBack = {
                selectedSession = null
                selectedAnalytics = null
                loadingAnalytics = false
                scope.launch {
                    refreshSessions()
                }
            }
        )
        return
    }

    val filtered = remember(sessions, query) {
        if (query.isBlank()) {
            sessions
        } else {
            val q = query.trim().lowercase(Locale.getDefault())
            sessions.filter { item ->
                item.appName.lowercase(Locale.getDefault()).contains(q) ||
                    item.packageName.lowercase(Locale.getDefault()).contains(q) ||
                    item.file.name.lowercase(Locale.getDefault()).contains(q)
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.fps_record_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = stringResource(R.string.fps_record_summary),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search sessions") }
        )

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.fps_record_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(filtered, key = { it.file.absolutePath }) { session ->
                FpsRecordSessionRow(
                    session = session,
                    onOpen = {
                        selectedSession = session
                        selectedAnalytics = null
                        loadingAnalytics = true
                    },
                    onDelete = {
                        if (session.file.delete()) {
                            descriptionFileFor(session.file).delete()
                            sessions = sessions.filter { it.file.absolutePath != session.file.absolutePath }
                            scope.launch {
                                refreshSessions()
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FpsRecordSessionRow(
    session: FpsSessionItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val iconBitmap =
        remember(session.packageName) {
            runCatching { pm.getApplicationIcon(session.packageName).toBitmap() }.getOrNull()
        }

    val avgFps = if (session.avgFps > 0f) formatValue(session.avgFps) else "--"
    val power = if (session.avgPower > 0f) "${formatValue(session.avgPower)}W" else "--"
    val subTextColor = Color(0xFF8B949E)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).background(Color.Transparent, CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = session.appName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val metaLine =
                        if (session.resolution != "Unknown") {
                            "${session.dateText}   Res: ${session.resolution}"
                        } else {
                            session.dateText
                        }
                    Text(
                        text = metaLine,
                        color = subTextColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Power: $power   Time: ${session.duration}",
                        color = subTextColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val chartColors = rememberChartColors()
                Text(
                    text = "AVG FPS",
                    color = subTextColor,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Text(
                    text = avgFps,
                    color = chartColors.blue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun FpsRecordDetailScreen(
    session: FpsSessionItem,
    analytics: LogAnalytics?,
    loading: Boolean,
    onBack: () -> Unit,
) {
    if (loading || analytics == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val dateText = analytics.sessionDate

    val fpsValues = analytics.fpsTimeData.map { it.second.toFloat() }
    val frameTimeValues = analytics.frameTimeData.map { it.second.toFloat() }
    val cpuUsageValues = analytics.cpuUsageTimeData.map { it.second.toFloat() }
    val cpuTempValues = analytics.cpuTempTimeData.map { it.second.toFloat() }
    val gpuUsageValues = analytics.gpuUsageTimeData.map { it.second.toFloat() }
    val gpuClockValues = analytics.gpuClockTimeData.map { it.second.toFloat() }
    val gpuTempValues = analytics.gpuTempTimeData.map { it.second.toFloat() }
    val powerValues = analytics.powerTimeData.map { it.second.toFloat() }
    val capacityValues = analytics.batteryLevelTimeData.map { it.second.toFloat() }
    val batteryTempValues = analytics.batteryTempTimeData.map { it.second.toFloat() }
    val ramUsageValues = analytics.ramUsageTimeData.map { it.second.toFloat() }
    val ramSpeedValues = analytics.ramSpeedTimeData.map { it.second.toFloat() }
    val ramTempValues = analytics.ramTempTimeData.map { it.second.toFloat() }
    val appRamUsageValues = analytics.appRamUsageTimeData.map { it.second.toFloat() }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cpuClockSeries = remember(analytics.cpuClockTimeData, isDark) {
        buildCpuClusterSeries(analytics.cpuClockTimeData, isDark)
    }

    val subTextColor = Color(0xFF8B949E)
    val context = LocalContext.current
    val iconBitmap = remember(session.packageName) {
        runCatching { context.packageManager.getApplicationIcon(session.packageName).toBitmap() }.getOrNull()
    }
    var showShareMenu by remember { mutableStateOf(false) }
    var showSaveMenu by remember { mutableStateOf(false) }
    var selectedDetailTab by remember(session.file.absolutePath) { mutableStateOf(SessionDetailTab.CHARTS) }
    var descriptionText by remember(session.file.absolutePath) { mutableStateOf("") }
    var descriptionLoaded by remember(session.file.absolutePath) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val createCsvDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch(Dispatchers.IO) {
                val saved = writeFileToUri(context, session.file, uri)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (saved) "Session CSV saved" else "Failed to save CSV",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    LaunchedEffect(session.file.absolutePath) {
        descriptionText = withContext(Dispatchers.IO) { loadSessionDescription(session.file) }
        descriptionLoaded = true
    }
    LaunchedEffect(session.file.absolutePath, descriptionText, descriptionLoaded) {
        if (!descriptionLoaded) return@LaunchedEffect
        delay(400)
        withContext(Dispatchers.IO) {
            saveSessionDescription(session.file, descriptionText)
        }
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp).background(Color.Transparent, CircleShape),
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = session.appName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row {
                    Box {
                        IconButton(onClick = { showShareMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share Session Log (CSV)") },
                                onClick = {
                                    showShareMenu = false
                                    shareSessionFile(context, session.file)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Graphics Charts (PNG)") },
                                onClick = {
                                    showShareMenu = false
                                    FpsRecordImageGenerator.generateAndShareChartsImage(context, session.appName, analytics)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Graphics Stats (PNG)") },
                                onClick = {
                                    showShareMenu = false
                                    FpsRecordImageGenerator.generateAndShareStatsImage(context, session.appName, analytics)
                                }
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showSaveMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showSaveMenu,
                            onDismissRequest = { showSaveMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save as CSV") },
                                onClick = {
                                    showSaveMenu = false
                                    createCsvDocumentLauncher.launch(session.file.name)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save Graphics Charts (PNG)") },
                                onClick = {
                                    showSaveMenu = false
                                    val ok = FpsRecordImageGenerator.saveChartsGraphics(context, session.appName, analytics)
                                    Toast.makeText(
                                        context,
                                        if (ok) "Graphics saved to Downloads" else "Failed to save graphics",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save Graphics Stats (PNG)") },
                                onClick = {
                                    showSaveMenu = false
                                    val ok = FpsRecordImageGenerator.saveStatsGraphics(context, session.appName, analytics)
                                    Toast.makeText(
                                        context,
                                        if (ok) "Graphics saved to Downloads" else "Failed to save graphics",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    minLines = 1,
                    maxLines = 6,
                    placeholder = { Text("Description") },
                )
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(dateText, color = subTextColor, fontSize = 12.sp)
                        if (analytics.resolution != "Unknown") {
                            Text("Res: ${analytics.resolution}", color = subTextColor, fontSize = 12.sp)
                        }
                    }
                    Text(
                        text = stringResource(R.string.fps_record_stats_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    StatsGrid(analytics = analytics)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = chartBackgroundColor()),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SessionTabChip(
                        label = "Charts",
                        selected = selectedDetailTab == SessionDetailTab.CHARTS,
                        modifier = Modifier.weight(1f)
                    ) { selectedDetailTab = SessionDetailTab.CHARTS }
                    SessionTabChip(
                        label = "Detailed Stats",
                        selected = selectedDetailTab == SessionDetailTab.STATS,
                        modifier = Modifier.weight(1f)
                    ) { selectedDetailTab = SessionDetailTab.STATS }
                }
            }
        }

        if (selectedDetailTab == SessionDetailTab.CHARTS) item {
            val fpsColor = Color(0xFF4CAF50)
            val fpsMax = fpsValues.maxOrNull() ?: 0f
            val fpsStep = 30f
            val yMax = ((fpsMax / fpsStep).toInt() + 1).coerceAtLeast(2) * fpsStep

            LineChartCard(
                title = "FPS",
                series = listOf(ChartSeries("FPS", fpsColor, fpsValues)),
                chartContent = {
                    FixedAxisLineChart(
                        series = listOf(ChartSeries("FPS", fpsColor, fpsValues)),
                        yMin = 0f,
                        yMax = yMax,
                        yStep = fpsStep,
                        fillUnderFirstSeries = true,
                        fillGradientColors = listOf(
                            Color(0x804CAF50),
                            Color(0x104CAF50),
                        ),
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                },
                footerStats = ChartFooter(
                    max = fpsMax,
                    min = fpsValues.minOrNull(),
                    avg = avgOrNull(fpsValues),
                    unit = "",
                )
            )
        }

        if (selectedDetailTab == SessionDetailTab.CHARTS && batteryTempValues.isNotEmpty()) {
            item {
                val chartColors = rememberChartColors()
                val maxTemp = batteryTempValues.maxOrNull() ?: 0f
                val yMax = (((maxTemp / 10f).toInt() + 1).coerceAtLeast(4) * 10f)
                LineChartCard(
                    title = "Battery Temp (°C)",
                    series = listOf(ChartSeries("Battery Temp", chartColors.orange, batteryTempValues)),
                    chartContent = {
                        FixedAxisLineChart(
                            series = listOf(ChartSeries("Battery Temp", chartColors.orange, batteryTempValues)),
                            yMin = 0f,
                            yMax = yMax,
                            yStep = 10f,
                            fillUnderFirstSeries = true,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                    },
                    footerStats = ChartFooter(
                        max = maxTemp,
                        min = batteryTempValues.minOrNull(),
                        avg = avgOrNull(batteryTempValues),
                        unit = "°C",
                    )
                )
            }
        }

        if (selectedDetailTab == SessionDetailTab.CHARTS) item {
            val chartColors = rememberChartColors()
            LineChartCard(
                title = "Frame Time (ms)",
                series = listOf(ChartSeries("Frame Time", chartColors.blue, frameTimeValues)),
                chartContent = {
                    AreaChart(
                        values = frameTimeValues,
                        color = chartColors.blue,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                },
                footerStats = ChartFooter(
                    max = frameTimeValues.maxOrNull(),
                    min = frameTimeValues.minOrNull(),
                    avg = avgOrNull(frameTimeValues),
                    unit = "ms",
                )
            )
        }

        if (selectedDetailTab == SessionDetailTab.CHARTS) item {
            val chartColors = rememberChartColors()
            LineChartCard(
                title = "CPU Usage (%)",
                series = listOf(ChartSeries("Total", chartColors.blue, cpuUsageValues)),
                chartContent = {
                    FixedAxisLineChart(
                        series = listOf(ChartSeries("Total", chartColors.blue, cpuUsageValues)),
                        yMin = 0f,
                        yMax = 100f,
                        yStep = 10f,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                },
                footerStats = ChartFooter(
                    max = cpuUsageValues.maxOrNull(),
                    min = cpuUsageValues.minOrNull(),
                    avg = avgOrNull(cpuUsageValues),
                    unit = "%",
                )
            )
        }

        if (selectedDetailTab == SessionDetailTab.CHARTS && cpuClockSeries.isNotEmpty()) {
            item {
                val cpuClockFlatValues = cpuClockSeries.flatMap { it.values }.mapNotNull { it }
                val maxFreq = cpuClockFlatValues.maxOrNull() ?: 0f
                val freqUpper = (((maxFreq / 300f).toInt() + 1).coerceAtLeast(4)) * 300f
                LineChartCard(
                    title = "CPU Freq (MHz)",
                    series = cpuClockSeries,
                    chartContent = {
                        FixedAxisLineChart(
                            series = cpuClockSeries,
                            yMin = 0f,
                            yMax = freqUpper,
                            yStep = 300f,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                    },
                    footerStats = ChartFooter(
                        max = maxFreq,
                        min = cpuClockFlatValues.filter { it > 0f }.minOrNull(),
                        avg = avgOrNull(cpuClockFlatValues.filter { it > 0f }),
                        unit = "MHz",
                    )
                )
            }
        }

        if (selectedDetailTab == SessionDetailTab.CHARTS && cpuTempValues.isNotEmpty()) {
            item {
                val cpuTempColor = Color(0xFF00BCD4)
                val maxTemp = cpuTempValues.maxOrNull() ?: 0f
                val yMax = (((maxTemp / 10f).toInt() + 1).coerceAtLeast(4) * 10f)

                LineChartCard(
                    title = "CPU Temp (°C)",
                    series = listOf(ChartSeries("Temp", cpuTempColor, cpuTempValues)),
                    chartContent = {
                        FixedAxisLineChart(
                            series = listOf(ChartSeries("Temp", cpuTempColor, cpuTempValues)),
                            yMin = 0f,
                            yMax = yMax,
                            yStep = 10f,
                            fillUnderFirstSeries = true,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                    },
                    footerStats = ChartFooter(
                        max = maxTemp,
                        min = cpuTempValues.minOrNull(),
                        avg = avgOrNull(cpuTempValues),
                        unit = "°C",
                    )
                )
            }
        }

        if (selectedDetailTab == SessionDetailTab.CHARTS && (gpuClockValues.isNotEmpty() || gpuUsageValues.isNotEmpty())) {
            item {
                val chartColors = rememberChartColors()
                LineChartCard(
                    title = "GPU Frequency (MHz)",
                    rightTitle = "Usage(%)",
                    series =
                        listOf(
                            ChartSeries("Frequency(MHz)", chartColors.blue, gpuClockValues),
                            ChartSeries("Usage(%)", chartColors.orange, gpuUsageValues),
                        ),
                    chartContent = {
                        GpuDualAxisChart(
                            freq = gpuClockValues,
                            usage = gpuUsageValues,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            colors = chartColors
                        )
                    },
                    footerStats = ChartFooter(
                        max = gpuClockValues.maxOrNull(),
                        min = gpuClockValues.filter { it > 0f }.minOrNull(),
                        avg = avgOrNull(gpuClockValues.filter { it > 0f }),
                        unit = "MHz",
                    )
                )
            }
        }

        if (selectedDetailTab == SessionDetailTab.CHARTS && gpuTempValues.isNotEmpty()) {
            item {
                val gpuTempColor = Color(0xFFE91E63)
                val maxTemp = gpuTempValues.maxOrNull() ?: 0f
                val yMax = (((maxTemp / 10f).toInt() + 1).coerceAtLeast(4) * 10f)

                LineChartCard(
                    title = "GPU Temp (°C)",
                    series = listOf(ChartSeries("Temp", gpuTempColor, gpuTempValues)),
                    chartContent = {
                        FixedAxisLineChart(
                            series = listOf(ChartSeries("Temp", gpuTempColor, gpuTempValues)),
                            yMin = 0f,
                            yMax = yMax,
                            yStep = 10f,
                            fillUnderFirstSeries = true,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                    },
                    footerStats = ChartFooter(
                        max = maxTemp,
                        min = gpuTempValues.minOrNull(),
                        avg = avgOrNull(gpuTempValues),
                        unit = "°C",
                    )
                )
            }
        }

        if (selectedDetailTab == SessionDetailTab.CHARTS && ramUsageValues.isNotEmpty()) {
            item {
                val ramUsageColor = Color(0xFFFFC107)
                val maxUsage = ramUsageValues.maxOrNull() ?: 0f
                val yMax = (((maxUsage / 512f).toInt() + 1).coerceAtLeast(4) * 512f)
                LineChartCard(
                    title = "RAM Usage (MB)",
                    series = listOf(ChartSeries("RAM Usage", ramUsageColor, ramUsageValues)),
                    chartContent = {
                        FixedAxisLineChart(
                            series = listOf(ChartSeries("RAM Usage", ramUsageColor, ramUsageValues)),
                            yMin = 0f,
                            yMax = yMax,
                            yStep = 512f,
                            fillUnderFirstSeries = true,
                            fillGradientColors = listOf(
                                Color(0x80FFC107),
                                Color(0x10FFC107),
                            ),
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                    },
                    footerStats = ChartFooter(
                        max = maxUsage,
                        min = ramUsageValues.minOrNull(),
                        avg = avgOrNull(ramUsageValues),
                        unit = "MB",
                    )
                )
            }
        }

        if (selectedDetailTab == SessionDetailTab.CHARTS && appRamUsageValues.isNotEmpty()) {
            item {
                val appRamUsageColor = Color(0xFFE91E63) // A pleasing Pink shade for App RAM differential emphasis
                val maxUsage = appRamUsageValues.maxOrNull() ?: 0f
                val yMax = (((maxUsage / 256f).toInt() + 1).coerceAtLeast(4) * 256f)
                LineChartCard(
                    title = "App RAM Usage (MB)",
                    series = listOf(ChartSeries("App RAM", appRamUsageColor, appRamUsageValues)),
                    chartContent = {
                        FixedAxisLineChart(
                            series = listOf(ChartSeries("App RAM", appRamUsageColor, appRamUsageValues)),
                            yMin = 0f,
                            yMax = yMax,
                            yStep = 256f,
                            fillUnderFirstSeries = true,
                            fillGradientColors = listOf(
                                Color(0x80E91E63),
                                Color(0x10E91E63),
                            ),
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                    },
                    footerStats = ChartFooter(
                        max = maxUsage,
                        min = appRamUsageValues.minOrNull(),
                        avg = avgOrNull(appRamUsageValues),
                        unit = "MB",
                    )
                )
            }
        }

        if (selectedDetailTab == SessionDetailTab.CHARTS && ramSpeedValues.isNotEmpty()) {
            item {
                val chartColors = rememberChartColors()
                val maxFreq = ramSpeedValues.maxOrNull() ?: 0f
                val yMax = (((maxFreq / 200f).toInt() + 1).coerceAtLeast(4) * 200f)
                LineChartCard(
                    title = "RAM Frequency (MHz)",
                    series = listOf(ChartSeries("RAM Freq", chartColors.cyan, ramSpeedValues)),
                    chartContent = {
                        FixedAxisLineChart(
                            series = listOf(ChartSeries("RAM Freq", chartColors.cyan, ramSpeedValues)),
                            yMin = 0f,
                            yMax = yMax,
                            yStep = 200f,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                    },
                    footerStats = ChartFooter(
                        max = maxFreq,
                        min = ramSpeedValues.filter { it > 0f }.minOrNull(),
                        avg = avgOrNull(ramSpeedValues.filter { it > 0f }),
                        unit = "MHz",
                    )
                )
            }
        }

        if (selectedDetailTab == SessionDetailTab.CHARTS && ramTempValues.isNotEmpty()) {
            item {
                val chartColors = rememberChartColors()
                val maxTemp = ramTempValues.maxOrNull() ?: 0f
                val yMax = (((maxTemp / 10f).toInt() + 1).coerceAtLeast(4) * 10f)
                LineChartCard(
                    title = "RAM Temp (°C)",
                    series = listOf(ChartSeries("RAM Temp", chartColors.orange, ramTempValues)),
                    chartContent = {
                        FixedAxisLineChart(
                            series = listOf(ChartSeries("RAM Temp", chartColors.orange, ramTempValues)),
                            yMin = 0f,
                            yMax = yMax,
                            yStep = 10f,
                            fillUnderFirstSeries = true,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                    },
                    footerStats = ChartFooter(
                        max = maxTemp,
                        min = ramTempValues.minOrNull(),
                        avg = avgOrNull(ramTempValues),
                        unit = "°C",
                    )
                )
            }
        }

        if (selectedDetailTab == SessionDetailTab.CHARTS && powerValues.isNotEmpty()) {
            item {
                val chartColors = rememberChartColors()
                LineChartCard(
                    title = "Power(W)",
                    rightTitle = "Capacity %",
                    series =
                        listOf(
                            ChartSeries("Power(W)", chartColors.blue, powerValues),
                            ChartSeries("Capacity(%)", chartColors.orange, capacityValues),
                        ),
                    chartContent = {
                        PowerCapacityDualAxisChart(
                            power = powerValues,
                            capacity = capacityValues,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            colors = chartColors
                        )
                    },
                    footerStats = ChartFooter(
                        max = powerValues.maxOrNull(),
                        min = powerValues.minOrNull(),
                        avg = avgOrNull(powerValues),
                        unit = "W",
                    )
                )
            }
        }

        if (selectedDetailTab == SessionDetailTab.CHARTS && capacityValues.isNotEmpty() && powerValues.isEmpty()) {
            item {
                val chartColors = rememberChartColors()
                LineChartCard(
                    title = "Battery Level (%)",
                    series = listOf(ChartSeries("Capacity(%)", chartColors.orange, capacityValues)),
                    chartContent = {
                        FixedAxisLineChart(
                            series = listOf(ChartSeries("Capacity(%)", chartColors.orange, capacityValues)),
                            yMin = 0f,
                            yMax = 100f,
                            yStep = 10f,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                    },
                    footerStats = ChartFooter(
                        max = capacityValues.maxOrNull(),
                        min = capacityValues.minOrNull(),
                        avg = avgOrNull(capacityValues),
                        unit = "%",
                    )
                )
            }
        }

        if (selectedDetailTab == SessionDetailTab.STATS) {
            item { DetailedStatsCard(analytics = analytics) }
        }
    }
}

@Composable
private fun StatsGrid(analytics: LogAnalytics) {
    val maxTemp = max(analytics.cpuStats.maxTemp.toFloat(), analytics.gpuStats.maxTemp.toFloat())
    val fpsValues = analytics.fpsTimeData.map { it.second.toFloat() }.sorted()
    val low5 = averageSlowestPercent(fpsValues, 0.05f)

    val items =
        listOf(
            SummaryStat("MAX", formatValue(analytics.fpsStats.maxFps.toFloat()), "FPS"),
            SummaryStat("MIN", formatValue(analytics.fpsStats.minFps.toFloat()), "FPS"),
            SummaryStat("AVERAGE", formatValue(analytics.fpsStats.avgFps.toFloat()), "FPS"),
            SummaryStat("1% LOW", formatValue(analytics.fpsStats.fps1PercentLow.toFloat()), "FPS"),
            SummaryStat("0.1% LOW", formatValue(analytics.fpsStats.fps0_1PercentLow.toFloat()), "FPS"),
            SummaryStat("5% LOW", formatValue(low5), "FPS"),
            SummaryStat("MAX", formatValue(maxTemp), "Temperature"),
            SummaryStat("AVERAGE", formatValue(analytics.powerStats.avgPower.toFloat()), "Power(Watt)"),
        )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in items.chunked(4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                row.forEach { stat ->
                    SummaryStatTile(stat = stat, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class SummaryStat(
    val label: String,
    val value: String,
    val unit: String,
)

@Composable
private fun SummaryStatTile(
    stat: SummaryStat,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stat.label,
            color = Color(0xFF8B949E),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stat.value,
            color = Color(0xFFE6EDF3),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = stat.unit,
            color = Color(0xFF8B949E),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SessionTabChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val fg = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private data class MetricRow(
    val label: String,
    val value: String,
    val tone: MetricTone = MetricTone.NEUTRAL,
)

private enum class MetricTone {
    GOOD,
    WARN,
    BAD,
    NEUTRAL
}

@Composable
private fun DetailedStatsCard(analytics: LogAnalytics) {
    val fps = analytics.fpsTimeData.map { it.second.toFloat() }
    val frameTime = analytics.frameTimeData.map { it.second.toFloat() }
    val cpuUsage = analytics.cpuUsageTimeData.map { it.second.toFloat() }
    val cpuTemp = analytics.cpuTempTimeData.map { it.second.toFloat() }
    val batteryTemp = analytics.batteryTempTimeData.map { it.second.toFloat() }
    val gpuUsage = analytics.gpuUsageTimeData.map { it.second.toFloat() }
    val gpuClock = analytics.gpuClockTimeData.map { it.second.toFloat() }
    val gpuTemp = analytics.gpuTempTimeData.map { it.second.toFloat() }
    val ramUsage = analytics.ramUsageTimeData.map { it.second.toFloat() }
    val power = analytics.powerTimeData.map { it.second.toFloat() }
    val batteryLevel = analytics.batteryLevelTimeData.map { it.second.toFloat() }
    val fpsSorted = fps.sorted()
    val low5 = averageSlowestPercent(fpsSorted, 0.05f)
    val frameSortedDesc = frameTime.sortedDescending()
    val frameBudgetMs = 16.67f
    val spikes = frameTime.count { it > frameBudgetMs * 2f }
    val droppedPct = if (frameTime.isNotEmpty()) (frameTime.count { it > frameBudgetMs }.toFloat() / frameTime.size) * 100f else 0f
    val frameVariance = variance(frameTime)
    val frameStdDev = kotlin.math.sqrt(frameVariance.toDouble()).toFloat()
    val frame1High = averageTopPercent(frameSortedDesc, 0.01f)
    val frame01High = averageTopPercent(frameSortedDesc, 0.001f)

    val sessionHours = sessionHours(analytics)
    val batteryDrop = batteryDropPercent(batteryLevel)
    val totalPower = totalPowerConsumptionWh(analytics)
    val avgPower = analytics.powerStats.avgPower.toFloat()
    val drainRate = if (sessionHours > 0f) batteryDrop / sessionHours else 0f

    val cpuClusterRows = buildClusterRows(analytics.cpuClockTimeData)
    val gpuMax = gpuClock.maxOrNull() ?: 0f
    val gpuMaxHit = if (gpuClock.isNotEmpty() && gpuMax > 0f) (gpuClock.count { it >= gpuMax }.toFloat() / gpuClock.size) * 100f else 0f

    val sections = listOf(
        "FPS METRICS" to listOf(
            MetricRow("Avg FPS", formatValue(analytics.fpsStats.avgFps.toFloat()), scoreFps(analytics.fpsStats.avgFps.toFloat())),
            MetricRow("Min FPS", formatValue(analytics.fpsStats.minFps.toFloat()), scoreFps(analytics.fpsStats.minFps.toFloat())),
            MetricRow("Max FPS", formatValue(analytics.fpsStats.maxFps.toFloat()), MetricTone.NEUTRAL),
            MetricRow("1% Low FPS", formatValue(analytics.fpsStats.fps1PercentLow.toFloat()), scoreFps(analytics.fpsStats.fps1PercentLow.toFloat())),
            MetricRow("0.1% Low FPS", formatValue(analytics.fpsStats.fps0_1PercentLow.toFloat()), scoreFps(analytics.fpsStats.fps0_1PercentLow.toFloat())),
            MetricRow("5% Low FPS", formatValue(low5), scoreFps(low5)),
            MetricRow("FPS Variance", formatValue(analytics.fpsStats.variance.toFloat()), MetricTone.NEUTRAL),
            MetricRow("FPS Standard Deviation", formatValue(analytics.fpsStats.standardDeviation.toFloat()), MetricTone.NEUTRAL),
            MetricRow("Smoothness", "${formatValue(analytics.fpsStats.smoothnessPercentage.toFloat())}%", scoreSmoothness(analytics.fpsStats.smoothnessPercentage.toFloat())),
        ),
        "FRAMETIME METRICS (ms)" to listOf(
            MetricRow("Avg Frametime", formatValue(avgOrNull(frameTime) ?: 0f), scoreFrameTime(avgOrNull(frameTime) ?: 0f)),
            MetricRow("1% High Frametime", formatValue(frame1High), scoreFrameTime(frame1High)),
            MetricRow("0.1% High Frametime", formatValue(frame01High), scoreFrameTime(frame01High)),
            MetricRow("Frametime Variance", formatValue(frameVariance), MetricTone.NEUTRAL),
            MetricRow("Frametime Std Deviation", formatValue(frameStdDev), MetricTone.NEUTRAL),
            MetricRow("Frame Spikes (>33.3ms)", spikes.toString(), if (spikes == 0) MetricTone.GOOD else if (spikes < 10) MetricTone.WARN else MetricTone.BAD),
            MetricRow("Dropped Frames", "${formatValue(droppedPct)}%", if (droppedPct < 1f) MetricTone.GOOD else if (droppedPct < 5f) MetricTone.WARN else MetricTone.BAD),
        ),
        "CPU METRICS" to buildList {
            add(MetricRow("Total CPU Usage (%)", formatRangeTriple(cpuUsage), scoreCpu(avgOrNull(cpuUsage) ?: 0f)))
            addAll(cpuClusterRows)
            add(MetricRow("CPU Temperature (°C)", formatRangeTriple(cpuTemp), scoreTemp(avgOrNull(cpuTemp) ?: 0f)))
            add(MetricRow("Device Temp (Battery °C)", formatRangeTriple(batteryTemp), scoreTemp(avgOrNull(batteryTemp) ?: 0f)))
        },
        "GPU METRICS" to listOf(
            MetricRow("GPU Usage (%)", formatRangeTriple(gpuUsage), scoreCpu(avgOrNull(gpuUsage) ?: 0f)),
            MetricRow("GPU Frequency (MHz)", formatRangeTriple(gpuClock), MetricTone.NEUTRAL),
            MetricRow("GPU Max Frequency Hit", "${formatValue(gpuMaxHit)}%", if (gpuMaxHit > 40f) MetricTone.GOOD else if (gpuMaxHit > 15f) MetricTone.WARN else MetricTone.BAD),
            MetricRow("GPU Temperature (°C)", formatRangeTriple(gpuTemp), scoreTemp(avgOrNull(gpuTemp) ?: 0f)),
        ),
        "RAM / MEMORY METRICS" to listOf(
            MetricRow("RAM Usage (MB)", formatRangeTriple(ramUsage), MetricTone.NEUTRAL),
        ),
        "POWER METRICS" to listOf(
            MetricRow("Total Power Consumption", "${formatValue(totalPower)} Wh", MetricTone.NEUTRAL),
            MetricRow("Total Battery % Dropped", "${formatValue(batteryDrop)}%", if (batteryDrop < 3f) MetricTone.GOOD else if (batteryDrop < 8f) MetricTone.WARN else MetricTone.BAD),
            MetricRow("Average Power Consumption", "${formatValue(avgPower)} W", if (avgPower < 3f) MetricTone.GOOD else if (avgPower < 6f) MetricTone.WARN else MetricTone.BAD),
            MetricRow("Battery Drain Rate", "${formatValue(drainRate)} %/h", if (drainRate < 5f) MetricTone.GOOD else if (drainRate < 12f) MetricTone.WARN else MetricTone.BAD),
        )
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = chartBackgroundColor()),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            sections.forEach { (sectionTitle, rows) ->
                Text(
                    text = sectionTitle,
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.label,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = row.value,
                            color = toneColor(row.tone),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun toneColor(tone: MetricTone): Color = when (tone) {
    MetricTone.GOOD -> Color(0xFF3FB950)
    MetricTone.WARN -> Color(0xFFFFB347)
    MetricTone.BAD -> Color(0xFFFF6B6B)
    MetricTone.NEUTRAL -> Color(0xFF58A6FF)
}

private fun variance(values: List<Float>): Float {
    if (values.size <= 1) return 0f
    val avg = values.average().toFloat()
    return values.sumOf { ((it - avg) * (it - avg)).toDouble() }.toFloat() / values.size
}

private fun averageTopPercent(sortedDescending: List<Float>, percent: Float): Float {
    if (sortedDescending.isEmpty()) return 0f
    val count = (sortedDescending.size * percent).toInt().coerceAtLeast(1)
    return sortedDescending.take(count).average().toFloat()
}

private fun formatRangeTriple(values: List<Float>): String {
    if (values.isEmpty()) return "--"
    val max = values.maxOrNull() ?: 0f
    val min = values.minOrNull() ?: 0f
    val avg = avgOrNull(values) ?: 0f
    return "Max ${formatValue(max)} / Min ${formatValue(min)} / Avg ${formatValue(avg)}"
}

private fun buildClusterRows(cpuClockTimeData: Map<Int, List<Pair<Long, Double>>>): List<MetricRow> {
    if (cpuClockTimeData.isEmpty()) return listOf(MetricRow("Per-cluster Metrics", "N/A", MetricTone.NEUTRAL))
    val groups = CpuClusterDetermination.resolveClusters(cpuClockTimeData)
    val labels = listOf("Little", "Mid", "Big")
    return groups.mapIndexed { index, cores ->
        val merged = mergeCoreSeries(cores, cpuClockTimeData).mapNotNull { it }.filter { it > 0f }
        if (merged.isEmpty()) {
            MetricRow("${labels.getOrElse(index) { "Cluster ${index + 1}" }} Cluster", "N/A", MetricTone.NEUTRAL)
        } else {
            val avg = avgOrNull(merged) ?: 0f
            val max = merged.maxOrNull() ?: 0f
            val estUsage = if (max > 0f) ((avg / max) * 100f).coerceIn(0f, 100f) else 0f
            MetricRow(
                "${labels.getOrElse(index) { "Cluster ${index + 1}" }} (Est. Usage / Avg Freq / Max Freq)",
                "${formatValue(estUsage)}% / ${formatValue(avg)} MHz / ${formatValue(max)} MHz",
                scoreCpu(estUsage)
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

private fun totalPowerConsumptionWh(analytics: LogAnalytics): Float {
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

private fun scoreFps(fps: Float): MetricTone =
    if (fps >= 55f) MetricTone.GOOD else if (fps >= 35f) MetricTone.WARN else MetricTone.BAD

private fun scoreSmoothness(value: Float): MetricTone =
    if (value >= 95f) MetricTone.GOOD else if (value >= 80f) MetricTone.WARN else MetricTone.BAD

private fun scoreFrameTime(ms: Float): MetricTone =
    if (ms <= 16.67f) MetricTone.GOOD else if (ms <= 25f) MetricTone.WARN else MetricTone.BAD

private fun scoreTemp(celsius: Float): MetricTone =
    if (celsius < 45f) MetricTone.GOOD else if (celsius < 55f) MetricTone.WARN else MetricTone.BAD

private fun scoreCpu(usage: Float): MetricTone =
    if (usage < 55f) MetricTone.GOOD else if (usage < 80f) MetricTone.WARN else MetricTone.BAD

private data class ChartSeries(
    val label: String,
    val color: Color,
    val values: List<Float?>,
)

@Composable
private fun LineChartCard(
    title: String,
    series: List<ChartSeries>,
    rightTitle: String? = null,
    footerStats: ChartFooter? = null,
    chartHeight: Int = 180,
    chartContent: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = chartBackgroundColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = Color(0xFF8B949E), fontWeight = FontWeight.SemiBold)
                if (!rightTitle.isNullOrBlank()) {
                    Text(rightTitle, color = Color(0xFF8B949E), fontSize = 12.sp)
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(6.dp)
            ) {
                if (chartContent != null) {
                    chartContent()
                } else {
                    LineChart(series = series, modifier = Modifier.fillMaxWidth().height(chartHeight.dp))
                }
            }
            ChartLegend(series = series)
            if (footerStats != null) {
                ChartFooterRow(footerStats)
            }
        }
    }
}


@Composable
private fun chartBackgroundColor(): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
}

@Composable
private fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
) {
    val samplesCount = series.maxOfOrNull { it.values.size } ?: 0
    if (samplesCount == 0) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data", color = Color(0xFF8B949E))
        }
        return
    }
    val step = max(1, samplesCount / 220)
    val filteredSeries =
        series.map { s ->
            s.copy(values = s.values.filterIndexed { index, _ -> index % step == 0 })
        }

    val allValues = filteredSeries.flatMap { s -> s.values.filterNotNull() }
    val minY = allValues.minOrNull() ?: 0f
    val maxY = allValues.maxOrNull() ?: 1f
    val range = if (maxY - minY <= 0f) 1f else maxY - minY
    val pad = range * 0.1f
    val yMin = minY - pad
    val yMax = maxY + pad

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val gridColor = if (isDark) Color(0x30FFFFFF) else Color(0x30000000)
    val labelColor = if (isDark) Color(0xB3FFFFFF) else Color(0x99000000)
    val steps = 5
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val leftPad = 70f
        val rightPad = 16f
        val topPad = 8f
        val bottomPad = 26f
        val plotW = (w - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)
        for (i in 0..steps) {
            val y = topPad + plotH * i / steps
            drawLine(
                gridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1f,
                pathEffect = dash
            )
            val value = yMax - (range + pad * 2f) * (i / steps.toFloat())
            drawContext.canvas.nativeCanvas.drawText(
                formatAxis(value),
                leftPad - 10f,
                y - 6f,
                android.graphics.Paint().apply {
                    setColor(labelColor.toArgb())
                    textSize = 22f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        val xSteps = 4
        for (i in 0..xSteps) {
            val x = leftPad + plotW * i / xSteps
            drawLine(
                gridColor,
                start = Offset(x, topPad),
                end = Offset(x, topPad + plotH),
                strokeWidth = 1f,
                pathEffect = dash
            )
            val label = formatTimeLabel(i, xSteps, samplesCount)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                topPad + plotH + 24f,
                android.graphics.Paint().apply {
                    setColor(labelColor.toArgb())
                    textSize = 22f
                    isAntiAlias = true
                    textAlign =
                        when (i) {
                            0 -> android.graphics.Paint.Align.LEFT
                            xSteps -> android.graphics.Paint.Align.RIGHT
                            else -> android.graphics.Paint.Align.CENTER
                        }
                }
            )
        }

        filteredSeries.forEach { s ->
            val values = s.values
            if (values.size <= 1) return@forEach
            val path = Path()
            var hasStarted = false
            values.forEachIndexed { index, value ->
                val v = value ?: return@forEachIndexed
                val x = leftPad + plotW * index / (values.size - 1).coerceAtLeast(1)
                val y = topPad + plotH - ((v - yMin) / (yMax - yMin)) * plotH
                if (!hasStarted) {
                    path.moveTo(leftPad, y)
                    path.lineTo(x, y)
                    hasStarted = true
                } else {
                    path.lineTo(x, y)
                }
            }
            if (hasStarted) {
                drawPath(
                    path,
                    color = s.color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f)
                )
            }
        }
    }
}

@Composable
private fun ChartLegend(series: List<ChartSeries>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        series.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).background(s.color, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(s.label, color = Color(0xFF8B949E), fontSize = 11.sp)
            }
        }
    }
}

private data class ChartFooter(
    val max: Float?,
    val min: Float?,
    val avg: Float?,
    val unit: String,
)

@Composable
private fun ChartFooterRow(stats: ChartFooter) {
    val maxText = stats.max?.let { formatValue(it) + stats.unit } ?: "--"
    val minText = stats.min?.let { formatValue(it) + stats.unit } ?: "--"
    val avgText = stats.avg?.let { formatValue(it) + stats.unit } ?: "--"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        val textColor = Color(0xFF8B949E)
        Text("MAX: $maxText", color = textColor, fontSize = 12.sp)
        Text("MIN: $minText", color = textColor, fontSize = 12.sp)
        Text("AVG: $avgText", color = textColor, fontSize = 12.sp)
    }
}

private fun formatValue(value: Float): String {
    return if (value <= 0f) "0.0" else String.format(Locale.getDefault(), "%.1f", value)
}

private fun buildCpuClusterSeries(
    cpuClockTimeData: Map<Int, List<Pair<Long, Double>>>,
    isDark: Boolean,
): List<ChartSeries> {
    if (cpuClockTimeData.isEmpty()) return emptyList()
    val groups = CpuClusterDetermination.resolveClusters(cpuClockTimeData)
    return groups.mapIndexed { index, cores ->
        val values = mergeCoreSeries(cores, cpuClockTimeData)
        ChartSeries(
            label = "Cluster ${index + 1} ${formatCoreRange(cores)}",
            color = cpuClusterColor(index, isDark),
            values = values,
        )
    }
}

private fun mergeCoreSeries(
    coreIds: List<Int>,
    cpuClockTimeData: Map<Int, List<Pair<Long, Double>>>,
): List<Float?> {
    val coreSeries = coreIds.map { coreId ->
        cpuClockTimeData[coreId].orEmpty().map { it.second.toFloat() }
    }
    val maxSamples = coreSeries.maxOfOrNull { it.size } ?: 0
    if (maxSamples == 0) return emptyList()

    return List(maxSamples) { sampleIndex ->
        val sampleValues = coreSeries.mapNotNull { series -> series.getOrNull(sampleIndex) }.filter { it > 0f }
        if (sampleValues.isEmpty()) null else sampleValues.average().toFloat()
    }
}

private fun formatCoreRange(coreIds: List<Int>): String {
    if (coreIds.isEmpty()) return ""
    return "(${coreIds.joinToString(" ")})"
}

private fun cpuClusterColor(index: Int, isDark: Boolean): Color {
    return if (isDark) {
        when (index) {
            0 -> Color(0xFF8774E7)
            1 -> Color(0xFF08D2D4)
            else -> Color(0xFFFF8626)
        }
    } else {
        when (index) {
            0 -> Color(0xFF5B21B6)
            1 -> Color(0xFF007A99)
            else -> Color(0xFFB54D00)
        }
    }
}

private fun formatAxis(value: Float): String {
    return if (value >= 1000f) {
        String.format(Locale.getDefault(), "%.0f", value)
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }
}

private fun formatTimeLabel(index: Int, steps: Int, samples: Int): String {
    if (samples <= 1) return "0s"
    val seconds = ((samples - 1) * index / steps)
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}m${secs}s" else "${secs}s"
}

private fun avgOrNull(values: List<Float>): Float? {
    if (values.isEmpty()) return null
    return values.average().toFloat()
}

private fun shareSessionFile(context: Context, file: File) {
    if (!file.exists() || !file.canRead()) {
        Toast.makeText(context, "Session log is not readable", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val shareSource =
            File(context.externalCacheDir ?: context.cacheDir, "share_${System.currentTimeMillis()}_${file.name}").apply {
                file.copyTo(this, overwrite = true)
            }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareSource
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "FPS Session Log")
            putExtra(Intent.EXTRA_TEXT, "FPS session log: ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share session log"))
    }.onFailure {
        Toast.makeText(context, "Failed to share session log", Toast.LENGTH_SHORT).show()
    }
}

private fun descriptionFileFor(sessionFile: File): File {
    val parent = sessionFile.parentFile ?: return File(sessionFile.absolutePath + ".desc")
    return File(parent, "${sessionFile.name}.desc")
}

private fun loadSessionDescription(sessionFile: File): String {
    val descFile = descriptionFileFor(sessionFile)
    return runCatching { if (descFile.exists()) descFile.readText() else "" }.getOrDefault("")
}

private fun saveSessionDescription(sessionFile: File, description: String) {
    val descFile = descriptionFileFor(sessionFile)
    runCatching {
        if (description.isBlank()) {
            if (descFile.exists()) descFile.delete()
        } else {
            descFile.writeText(description)
        }
    }
}

private fun writeFileToUri(context: Context, source: File, targetUri: Uri): Boolean {
    if (!source.exists() || !source.canRead()) return false
    return runCatching {
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
        } ?: return false
        true
    }.getOrElse { false }
}

@Composable
private fun GpuDualAxisChart(
    freq: List<Float>,
    usage: List<Float?>,
    modifier: Modifier = Modifier,
    colors: ChartColors,
) {
    val freqMax = freq.maxOrNull() ?: 0f
    val freqStep = 100f
    val freqUpper = ((freqMax / freqStep).toInt() + 1).coerceAtLeast(1) * freqStep
    val usageSteps = listOf(0f, 50f, 75f, 90f, 100f)
    val steps = 5
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val gridColor = if (isDark) Color(0x30FFFFFF) else Color(0x30000000)
    val labelColor = if (isDark) Color(0xB3FFFFFF) else Color(0x99000000)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val leftPad = 70f
        val rightPad = 58f
        val topPad = 8f
        val bottomPad = 26f
        val plotW = (w - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)

        for (i in 0..steps) {
            val y = topPad + plotH * i / steps
            drawLine(
                gridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
            )
            val value = freqUpper - (freqUpper * i / steps)
            drawContext.canvas.nativeCanvas.drawText(
                formatAxis(value),
                leftPad - 10f,
                y - 6f,
                android.graphics.Paint().apply {
                    setColor(Color(0xFF86D3FF).toArgb())
                    textSize = 22f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        usageSteps.forEach { u ->
            val y = topPad + plotH - (u / 100f) * plotH
            drawContext.canvas.nativeCanvas.drawText(
                formatAxis(u),
                leftPad + plotW + 8f,
                y - 6f,
                android.graphics.Paint().apply {
                    setColor(Color(0xFFFF8626).toArgb())
                    textSize = 22f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.LEFT
                }
            )
        }

        val xSteps = 4
        for (i in 0..xSteps) {
            val x = leftPad + plotW * i / xSteps
            drawLine(
                gridColor,
                start = Offset(x, topPad),
                end = Offset(x, topPad + plotH),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
            )
            val label = formatTimeLabel(i, xSteps, freq.size)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                topPad + plotH + 24f,
                android.graphics.Paint().apply {
                    setColor(labelColor.toArgb())
                    textSize = 22f
                    isAntiAlias = true
                    textAlign =
                        when (i) {
                            0 -> android.graphics.Paint.Align.LEFT
                            xSteps -> android.graphics.Paint.Align.RIGHT
                            else -> android.graphics.Paint.Align.CENTER
                        }
                }
            )
        }

        if (freq.size > 1) {
            val path = Path()
            freq.forEachIndexed { index, value ->
                val x = leftPad + plotW * index / (freq.size - 1).coerceAtLeast(1)
                val y = topPad + plotH - (value / freqUpper).coerceIn(0f, 1f) * plotH
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = colors.blue, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f))
        }

        if (usage.isNotEmpty()) {
            val path = Path()
            var hasMove = false
            usage.forEachIndexed { index, value ->
                val v = value ?: run {
                    hasMove = false
                    return@forEachIndexed
                }
                val x = leftPad + plotW * index / (usage.size - 1).coerceAtLeast(1)
                val y = topPad + plotH - (v.coerceIn(0f, 100f) / 100f) * plotH
                if (!hasMove) {
                    path.moveTo(x, y)
                    hasMove = true
                } else {
                    path.lineTo(x, y)
                }
            }
            if (hasMove) {
                drawPath(path, color = colors.orange, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f))
            }
        }
    }
}

@Composable
private fun PowerCapacityDualAxisChart(
    power: List<Float>,
    capacity: List<Float?>,
    modifier: Modifier = Modifier,
    colors: ChartColors,
) {
    val powerMax = power.maxOrNull() ?: 0f
    val powerUpper = ceil(powerMax.toDouble()).toFloat().coerceAtLeast(1f)
    val steps = 5
    val capacitySteps = listOf(0f, 20f, 40f, 60f, 80f, 100f)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val gridColor = if (isDark) Color(0x30FFFFFF) else Color(0x30000000)
    val labelColor = if (isDark) Color(0xB3FFFFFF) else Color(0x99000000)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val leftPad = 70f
        val rightPad = 58f
        val topPad = 8f
        val bottomPad = 26f
        val plotW = (w - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)

        for (i in 0..steps) {
            val y = topPad + plotH * i / steps
            drawLine(
                gridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
            )
            val value = powerUpper - (powerUpper * i / steps)
            drawContext.canvas.nativeCanvas.drawText(
                formatAxis(value),
                leftPad - 10f,
                y - 6f,
                android.graphics.Paint().apply {
                    setColor(colors.blue.toArgb())
                    textSize = 22f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        capacitySteps.forEach { u ->
            val y = topPad + plotH - (u / 100f) * plotH
            drawContext.canvas.nativeCanvas.drawText(
                formatAxis(u),
                leftPad + plotW + 8f,
                y - 6f,
                android.graphics.Paint().apply {
                    setColor(colors.orange.toArgb())
                    textSize = 22f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.LEFT
                }
            )
        }

        val xSteps = 4
        for (i in 0..xSteps) {
            val x = leftPad + plotW * i / xSteps
            drawLine(
                gridColor,
                start = Offset(x, topPad),
                end = Offset(x, topPad + plotH),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
            )
            val label = formatTimeLabel(i, xSteps, power.size)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                topPad + plotH + 24f,
                android.graphics.Paint().apply {
                    setColor(labelColor.toArgb())
                    textSize = 22f
                    isAntiAlias = true
                    textAlign =
                        when (i) {
                            0 -> android.graphics.Paint.Align.LEFT
                            xSteps -> android.graphics.Paint.Align.RIGHT
                            else -> android.graphics.Paint.Align.CENTER
                        }
                }
            )
        }

        if (power.size > 1) {
            val path = Path()
            power.forEachIndexed { index, value ->
                val x = leftPad + plotW * index / (power.size - 1).coerceAtLeast(1)
                val y = topPad + plotH - (value / powerUpper) * plotH
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = colors.blue, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f))
        }

        if (capacity.isNotEmpty()) {
            val path = Path()
            var hasMove = false
            capacity.forEachIndexed { index, value ->
                val v = value ?: run {
                    hasMove = false
                    return@forEachIndexed
                }
                val x = leftPad + plotW * index / (capacity.size - 1).coerceAtLeast(1)
                val y = topPad + plotH - (v.coerceIn(0f, 100f) / 100f) * plotH
                if (!hasMove) {
                    path.moveTo(x, y)
                    hasMove = true
                } else {
                    path.lineTo(x, y)
                }
            }
            if (hasMove) {
                drawPath(path, color = colors.orange, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f))
            }
        }
    }
}

data class ChartColors(
    val blue: Color,
    val orange: Color,
    val cyan: Color
)

@Composable
private fun rememberChartColors(): ChartColors {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(isDark) {
        if (isDark) {
            ChartColors(
                blue = Color(0xFF58A6FF),
                orange = Color(0xFFFF8626),
                cyan = Color(0xFF3FB950)
            )
        } else {
            ChartColors(
                blue = Color(0xFF0969DA),
                orange = Color(0xFF9A6700),
                cyan = Color(0xFF1A7F37)
            )
        }
    }
}

@Composable
private fun AreaChart(
    values: List<Float?>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val points = values.filterNotNull()
    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data", color = Color(0xFF8B949E))
        }
        return
    }
    val maxY = points.maxOrNull() ?: 1f
    val steps = 6
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val gridColor = if (isDark) Color(0x30FFFFFF) else Color(0x30000000)
    val labelColor = if (isDark) Color(0xB3FFFFFF) else Color(0x99000000)
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val leftPad = 44f
        val rightPad = 16f
        val topPad = 8f
        val bottomPad = 26f
        val plotW = (w - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)
        for (i in 0..steps) {
            val y = topPad + plotH * i / steps
            drawLine(gridColor, start = Offset(leftPad, y), end = Offset(leftPad + plotW, y), strokeWidth = 1f, pathEffect = dash)
            val value = maxY - (maxY * i / steps)
            drawContext.canvas.nativeCanvas.drawText(
                formatAxis(value),
                leftPad - 6f,
                y - 6f,
                android.graphics.Paint().apply {
                    setColor(labelColor.toArgb())
                    textSize = 22f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }
        val xSteps = 4
        for (i in 0..xSteps) {
            val x = leftPad + plotW * i / xSteps
            drawLine(gridColor, start = Offset(x, topPad), end = Offset(x, topPad + plotH), strokeWidth = 1f, pathEffect = dash)
            val label = formatTimeLabel(i, xSteps, values.size)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                topPad + plotH + 24f,
                android.graphics.Paint().apply {
                    setColor(labelColor.toArgb())
                    textSize = 22f
                    isAntiAlias = true
                    textAlign =
                        when (i) {
                            0 -> android.graphics.Paint.Align.LEFT
                            xSteps -> android.graphics.Paint.Align.RIGHT
                            else -> android.graphics.Paint.Align.CENTER
                        }
                }
            )
        }

        val path = Path()
        val fillPath = Path()
        values.filterNotNull().forEachIndexed { index, value ->
            val x = leftPad + plotW * index / (values.size - 1).coerceAtLeast(1)
            val y = topPad + plotH - (value / maxY) * plotH
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, topPad + plotH)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(leftPad + plotW, topPad + plotH)
        fillPath.close()
        drawPath(fillPath, color = color.copy(alpha = 0.35f))
        drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f))
    }
}

@Composable
private fun FixedAxisLineChart(
    series: List<ChartSeries>,
    yMin: Float,
    yMax: Float,
    yStep: Float,
    fillUnderFirstSeries: Boolean = false,
    fillGradientColors: List<Color>? = null,
    modifier: Modifier = Modifier,
) {
    if (series.isEmpty()) return
    val samplesCount = series.maxOfOrNull { it.values.size } ?: 0
    if (samplesCount == 0) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data", color = Color(0xFF8B949E))
        }
        return
    }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val gridColor = if (isDark) Color(0x30FFFFFF) else Color(0x30000000)
    val labelColor = if (isDark) Color(0xB3FFFFFF) else Color(0x99000000)
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val leftPad = 44f
        val rightPad = 16f
        val topPad = 8f
        val bottomPad = 26f
        val plotW = (w - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)
        val steps = ((yMax - yMin) / yStep).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val y = topPad + plotH * i / steps
            drawLine(gridColor, start = Offset(leftPad, y), end = Offset(leftPad + plotW, y), strokeWidth = 1f, pathEffect = dash)
            val value = yMax - yStep * i
            drawContext.canvas.nativeCanvas.drawText(
                formatAxis(value),
                leftPad - 6f,
                y - 6f,
                android.graphics.Paint().apply {
                    setColor(labelColor.toArgb())
                    textSize = 22f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }
        val xSteps = 4
        for (i in 0..xSteps) {
            val x = leftPad + plotW * i / xSteps
            drawLine(gridColor, start = Offset(x, topPad), end = Offset(x, topPad + plotH), strokeWidth = 1f, pathEffect = dash)
            val label = formatTimeLabel(i, xSteps, samplesCount)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                topPad + plotH + 24f,
                android.graphics.Paint().apply {
                    setColor(labelColor.toArgb())
                    textSize = 22f
                    isAntiAlias = true
                    textAlign =
                        when (i) {
                            0 -> android.graphics.Paint.Align.LEFT
                            xSteps -> android.graphics.Paint.Align.RIGHT
                            else -> android.graphics.Paint.Align.CENTER
                        }
                }
            )
        }

        series.forEachIndexed { seriesIndex, s ->
            val values = s.values
            if (values.size <= 1) return@forEachIndexed
            val path = Path()
            val fillPath = Path()
            var hasStarted = false
            values.forEachIndexed { index, value ->
                val v = value ?: return@forEachIndexed
                val x = leftPad + plotW * index / (values.size - 1).coerceAtLeast(1)
                val clamped = v.coerceIn(yMin, yMax)
                val y = topPad + plotH - ((clamped - yMin) / (yMax - yMin)) * plotH
                if (!hasStarted) {
                    path.moveTo(x, y)
                    if (fillUnderFirstSeries && seriesIndex == 0) {
                        fillPath.moveTo(x, topPad + plotH)
                        fillPath.lineTo(x, y)
                    }
                    hasStarted = true
                } else {
                    path.lineTo(x, y)
                    if (fillUnderFirstSeries && seriesIndex == 0) {
                        fillPath.lineTo(x, y)
                    }
                }
            }
            if (hasStarted) {
                if (fillUnderFirstSeries && seriesIndex == 0) {
                    fillPath.lineTo(leftPad + plotW, topPad + plotH)
                    fillPath.close()
                    val gradient = fillGradientColors ?: listOf(
                        s.color.copy(alpha = 0.45f),
                        s.color.copy(alpha = 0.08f),
                    )
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = gradient,
                            startY = topPad,
                            endY = topPad + plotH,
                        )
                    )
                }
                drawPath(path, color = s.color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f))
            }
        }
    }
}

private fun averageSlowestPercent(sortedAscendingFps: List<Float>, percent: Float): Float {
    if (sortedAscendingFps.isEmpty()) return 0f
    val sampleCount = ceil(sortedAscendingFps.size * percent).toInt().coerceAtLeast(1)
    return sortedAscendingFps.take(sampleCount).average().toFloat()
}

private fun loadSessions(pm: android.content.pm.PackageManager): List<FpsSessionItem> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val reader = PerAppLogReader()

    return PerAppLogManager.getInstance().getAllPerAppLogFiles()
        .flatMap { (packageName, files) ->
            files.map { file ->
                val appName = try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    packageName
                }

                val analytics = runCatching { reader.analyzeLogFile(file.absolutePath) }.getOrNull()
                val avgFps = analytics?.fpsStats?.avgFps?.toFloat() ?: 0f
                val avgPower = analytics?.powerStats?.avgPower?.toFloat() ?: 0f
                val duration = analytics?.sessionDuration ?: "--"

                FpsSessionItem(
                    packageName = packageName,
                    appName = appName,
                    file = file,
                    dateText = dateFormat.format(Date(file.lastModified())),
                    avgFps = avgFps,
                    avgPower = avgPower,
                    duration = duration,
                    resolution = analytics?.resolution ?: "Unknown"
                )
            }
        }
        .sortedByDescending { it.file.lastModified() }
}
