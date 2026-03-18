package com.example.batterymonitor.ui.appdetail

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
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
    var selectedMetric by remember { mutableStateOf<MetricInfo?>(null) }

    LaunchedEffect(packageName) {
        try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            appLabel = packageManager.getApplicationLabel(info).toString()
            appIcon = packageManager.getApplicationIcon(info)
        } catch (e: Exception) {}
        
        detailedStats = viewModel.getDetailedStatsForApp(packageName)
        isLoading = false
    }

    if (selectedMetric != null) {
        MetricDetailModal(
            metric = selectedMetric!!,
            onDismiss = { selectedMetric = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        // App Header
        androidx.compose.ui.res.painterResource(id = com.example.batterymonitor.R.drawable.ic_battery_placeholder)
        coil.compose.AsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(appIcon ?: com.example.batterymonitor.R.drawable.ic_battery_placeholder)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = androidx.compose.ui.Modifier.size(80.dp),
            error = androidx.compose.ui.res.painterResource(id = com.example.batterymonitor.R.drawable.ic_battery_placeholder),
            fallback = androidx.compose.ui.res.painterResource(id = com.example.batterymonitor.R.drawable.ic_battery_placeholder)
        )
        
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
            if (stats.anomalyType != null) {
                AnomalyWarning(stats.anomalyType)
            } else {
                NormalStatus()
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            // 1. Time & Impact Analysis
            Text("Activity & Drain Split", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            AnalysisCard {
                TimeSplitContent(stats) { metric -> selectedMetric = metric }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 2. Hardware Resources
            Text("Hardware Resource Impact", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            AnalysisCard {
                HardwareStatsContent(stats) { metric -> selectedMetric = metric }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 3. Historical Trends
            Text("7-Day Consumption Trend", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            AnalysisCard {
                TrendContent(stats) { metric -> selectedMetric = metric }
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
fun TimeSplitContent(
    stats: com.example.batterymonitor.ui.dashboard.DetailedAppStats,
    onMetricClick: (MetricInfo) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            StatItemSmall(
                label = "Foreground Time", 
                value = formatTime(stats.foregroundTimeMs),
                onClick = {
                    onMetricClick(MetricInfo(
                        "Foreground Time",
                        formatTime(stats.foregroundTimeMs),
                        "Time the app was actively visible and used on your screen. This is usually when the app consumes the most battery."
                    ))
                }
            )
            StatItemSmall(
                label = "Foreground Drain", 
                value = String.format("%.1f%%", stats.foregroundDrainPct),
                onClick = {
                    onMetricClick(MetricInfo(
                        "Foreground Drain",
                        String.format("%.1f%%", stats.foregroundDrainPct),
                        "Estimated battery percentage consumed while the app was active on screen."
                    ))
                }
            )
        }
        Divider(modifier = Modifier.width(1.dp).height(60.dp).padding(vertical = 4.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            StatItemSmall(
                label = "Background Time", 
                value = formatTime(stats.backgroundTimeMs),
                onClick = {
                    onMetricClick(MetricInfo(
                        "Background Time",
                        formatTime(stats.backgroundTimeMs),
                        "Time the app was running silently while you were using other apps or while the screen was off. High background time can lead to unexpected drain."
                    ))
                }
            )
            StatItemSmall(
                label = "Background Drain", 
                value = String.format("%.1f%%", stats.backgroundDrainPct),
                onClick = {
                    onMetricClick(MetricInfo(
                        "Background Drain",
                        String.format("%.1f%%", stats.backgroundDrainPct),
                        "Estimated battery percentage consumed while the app was running in the background. Reducing this helps improve standby battery life."
                    ))
                }
            )
        }
    }
}

@Composable
fun HardwareStatsContent(
    stats: com.example.batterymonitor.ui.dashboard.DetailedAppStats,
    onMetricClick: (MetricInfo) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatItemSmall(
                label = "CPU Intensity", 
                value = String.format("%.1f%%", stats.cpuIntensityScore),
                onClick = {
                    onMetricClick(MetricInfo(
                        "CPU Intensity",
                        String.format("%.1f%%", stats.cpuIntensityScore),
                        "Measures how much the app's processing tasks are working the phone's CPU. Higher intensity means more heat and faster battery drain."
                    ))
                }
            )
            StatItemSmall(
                label = "Network Usage", 
                value = formatDynamicBytes(stats.networkUsageBytes),
                onClick = {
                    onMetricClick(MetricInfo(
                        "Network Usage",
                        formatDynamicBytes(stats.networkUsageBytes),
                        "Total data sent and received by the app. Constant data sync, especially over cellular, can significantly impact battery."
                    ))
                }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val wakeLockText = if (stats.wakeLockEstimate == 0) "0 (App allows deep sleep)" else "${stats.wakeLockEstimate}"
            StatItemSmall(
                label = "Est. Wake Locks", 
                value = wakeLockText,
                onClick = {
                    onMetricClick(MetricInfo(
                        "Est. Wake Locks",
                        wakeLockText,
                        "Number of times the app prevented your phone from entering 'Deep Sleep'. Frequent wake locks (like music or background tracking) are a common cause of idle drain."
                    ))
                }
            )
            StatItemSmall(
                label = "SOT Impact", 
                value = String.format("%.1f%%", stats.sotImpactPct),
                onClick = {
                    onMetricClick(MetricInfo(
                        "SOT Impact",
                        String.format("%.1f%%", stats.sotImpactPct),
                        "Percentage of your total Screen-On Time (since last charge) that was used by this specific app."
                    ))
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onMetricClick(MetricInfo(
                        "CPU Intensity",
                        String.format("%.1f%%", stats.cpuIntensityScore),
                        "Measures how much the app's processing tasks are working the phone's CPU. Higher intensity means more heat and faster battery drain."
                    ))
                }
        ) {
            LinearProgressIndicator(
                progress = (stats.cpuIntensityScore / 100f).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(8.dp),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CPU utilization estimation (Active sessions)", 
                    fontSize = 10.sp, 
                    color = Color.Gray
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "More info",
                    modifier = Modifier.size(10.dp),
                    tint = Color.Gray.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun AnomalyWarning(message: String) {
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
                    message,
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
                "Normal consumption",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun TrendContent(
    stats: com.example.batterymonitor.ui.dashboard.DetailedAppStats,
    onMetricClick: (MetricInfo) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onMetricClick(MetricInfo(
                        "Avg. Daily Drain",
                        String.format("%.1f%%", stats.sevenDayAverageDrain),
                        "Typical battery percentage this app consumes over a 24-hour period, based on the last 7 days of usage."
                    ))
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Avg. Daily Drain: ${String.format("%.1f%%", stats.sevenDayAverageDrain)}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "More info",
                modifier = Modifier.size(14.dp),
                tint = Color.Gray.copy(alpha = 0.6f)
            )
        }
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

data class MetricInfo(
    val title: String,
    val currentValue: String,
    val explanation: String
)

@Composable
fun MetricDetailModal(
    metric: MetricInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFFFF9F00))
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = metric.title,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9F00),
                    fontSize = 20.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    text = "Current Impact: ${metric.currentValue}",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = metric.explanation,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        },
        containerColor = Color(0xFF2C2822),
        shape = MaterialTheme.shapes.large
    )
}

@Composable
fun StatItemSmall(
    label: String, 
    value: String, 
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "More info",
                    modifier = Modifier.size(12.dp),
                    tint = Color.Gray.copy(alpha = 0.6f)
                )
            }
        }
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
