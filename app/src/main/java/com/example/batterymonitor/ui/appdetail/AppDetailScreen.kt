package com.example.batterymonitor.ui.appdetail

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.batterymonitor.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.launch

@Composable
fun AppDetailScreen(
    packageName: String,
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    
    var appLabel by remember { mutableStateOf(packageName.substringAfterLast(".")) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    var detailedStats by remember { mutableStateOf<com.example.batterymonitor.ui.dashboard.DetailedAppStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(packageName) {
        try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            appLabel = packageManager.getApplicationLabel(info).toString()
            appIcon = packageManager.getApplicationIcon(info)
        } catch (e: Exception) {}
        
        detailedStats = viewModel.getDetailedStatsForApp(packageName)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        if (appIcon != null) {
            AndroidView(
                factory = { ctx ->
                    android.widget.ImageView(ctx).apply {
                        setImageDrawable(appIcon)
                    }
                },
                modifier = Modifier.size(80.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(appLabel, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(packageName, fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading || detailedStats == null) {
            Box(Modifier.height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val stats = detailedStats!!
            
            // 0. Anomaly Alert & Status
            if (stats.hasAnomaly) {
                AnomalyWarning()
            } else {
                NormalStatus()
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            // 1. Time & Impact Analysis
            Text("Activity & Drain Split", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            AnalysisCard {
                TimeSplitContent(stats)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 2. Hardware Resources
            Text("Hardware Resource Impact", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            AnalysisCard {
                HardwareStatsContent(stats)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 3. Historical Trends
            Text("7-Day Consumption Trend", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            AnalysisCard {
                TrendContent(stats)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Open System App Info", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AnalysisCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun TimeSplitContent(stats: com.example.batterymonitor.ui.dashboard.DetailedAppStats) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            StatItemSmall("Foreground Time", formatTime(stats.foregroundTimeMs))
            StatItemSmall("Foreground Drain", String.format("%.1f%%", stats.foregroundDrainPct))
        }
        Divider(modifier = Modifier.width(1.dp).height(60.dp).padding(vertical = 4.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            StatItemSmall("Background Time", formatTime(stats.backgroundTimeMs))
            StatItemSmall("Background Drain", String.format("%.1f%%", stats.backgroundDrainPct))
        }
    }
}

@Composable
fun HardwareStatsContent(stats: com.example.batterymonitor.ui.dashboard.DetailedAppStats) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatItemSmall("CPU Intensity", String.format("%.1f%%", stats.cpuIntensityScore))
            StatItemSmall("Network Usage", formatDynamicBytes(stats.networkUsageBytes))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val wakeLockText = if (stats.wakeLockEstimate == 0) "0 (App allows deep sleep)" else "${stats.wakeLockEstimate}"
            StatItemSmall("Est. Wake Locks", wakeLockText)
            StatItemSmall("SOT Impact", String.format("%.1f%%", stats.sotImpactPct))
        }
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = (stats.cpuIntensityScore / 100f).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().height(8.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        Text("CPU utilization estimation (Active sessions)", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun AnomalyWarning() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "High Consumption Alert",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
                Text(
                    "This app is consuming battery significantly above normal thresholds.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun NormalStatus() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Green.copy(alpha = 0.1f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Normal",
                tint = Color(0xFF2E7D32)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Normal Consumption Status",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun TrendContent(stats: com.example.batterymonitor.ui.dashboard.DetailedAppStats) {
    Column {
        Text("Avg. Daily Drain: ${String.format("%.1f%%", stats.sevenDayAverageDrain)}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (stats.dailyTrend.isEmpty()) {
            Text("No historical data yet", color = Color.Gray, fontSize = 12.sp)
        } else {
            TrendChart(trend = stats.dailyTrend)
        }
    }
}

@Composable
fun TrendChart(trend: List<Pair<String, Float>>) {
    val maxVal = trend.map { it.second }.maxOf { it }.coerceAtLeast(1f)
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        trend.forEach { (day, drain) ->
            val barHeight = (120f * (drain / maxVal)).dp
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                    Text(
                        text = if (drain > 0) String.format("%.1f%%", drain) else "",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(barHeight)
                            .background(
                                color = if (drain > maxVal * 0.8f) Color(0xFFD24D0A) else Color(0xFFFF9F00),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(text = day, fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun StatItemSmall(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
    }
}

fun formatTime(ms: Long): String {
    val mins = ms / 60000
    if (mins < 1) return "< 1m"
    val hours = mins / 60
    return if (hours > 0) "${hours}h ${mins % 60}m" else "${mins}m"
}

fun formatDynamicBytes(bytes: Long): String {
    val mb = bytes / (1024 * 1024)
    return if (mb < 1024) {
        "$mb MB"
    } else {
        String.format("%.2f GB", mb.toFloat() / 1024f)
    }
}

@Composable
fun DetailStatCard(title: String, value: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, fontSize = 12.sp, color = Color.Gray.copy(alpha = 0.8f))
        }
    }
}
