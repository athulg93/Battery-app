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
import com.example.batterymonitor.ui.appdetail.AppDetailViewModel
import com.example.batterymonitor.ui.appdetail.AppDetailState
import kotlinx.coroutines.launch
import java.util.Locale
import com.example.batterymonitor.ui.components.V2BentoCard
import com.example.batterymonitor.ui.components.V2GlassCard
import com.example.batterymonitor.ui.components.V2TrendChart
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun AppDetailScreen(
    packageName: String,
    viewModel: AppDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var selectedMetric by remember { mutableStateOf<MetricInfo?>(null) }

    LaunchedEffect(packageName) {
        viewModel.loadAppDetails(packageName)
    }

    if (selectedMetric != null) {
        MetricDetailModal(
            metric = selectedMetric!!,
            onDismiss = { selectedMetric = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is AppDetailState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is AppDetailState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = Color.Red)
                }
            }
            is AppDetailState.Success -> {
                AppDetailContent(
                    packageName = packageName,
                    label = state.label,
                    icon = state.icon,
                    stats = state.stats,
                    onMetricClick = { selectedMetric = it }
                )
            }
        }
    }
}

@Composable
fun AppDetailContent(
    packageName: String,
    label: String,
    icon: Drawable?,
    stats: com.example.batterymonitor.data.repository.DetailedAppStats,
    onMetricClick: (MetricInfo) -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // App Header
        V2GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(icon ?: com.example.batterymonitor.R.drawable.ic_battery_placeholder)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            error = androidx.compose.ui.res.painterResource(id = com.example.batterymonitor.R.drawable.ic_battery_placeholder)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = label,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = packageName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
        
        // Anomaly Warning
        if (stats.anomalyType != null) {
            Spacer(modifier = Modifier.height(16.dp))
            AnomalyWarningCard(type = stats.anomalyType)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Metrics Grid (Bento style)
        Text(
            text = "HARDWARE IMPACT",
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.example.batterymonitor.ui.components.V2BentoCard(
                    label = "SOT Impact",
                    value = String.format(Locale.getDefault(), "%.1f%%", stats.sotImpactPct),
                    modifier = Modifier.weight(1f).clickable {
                        onMetricClick(MetricInfo("SOT Impact", String.format("%.1f%%", stats.sotImpactPct), "Contribution to total screen-on time."))
                    }
                )
                com.example.batterymonitor.ui.components.V2BentoCard(
                    label = "Network",
                    value = formatDynamicBytes(stats.networkUsageBytes),
                    modifier = Modifier.weight(1f).clickable {
                        onMetricClick(MetricInfo("Network Usage", formatDynamicBytes(stats.networkUsageBytes), "Total data throughput impact."))
                    },
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.example.batterymonitor.ui.components.V2BentoCard(
                    label = "Active",
                    value = formatTime(stats.foregroundTimeMs),
                    modifier = Modifier.weight(1f).clickable {
                        onMetricClick(MetricInfo("Active Time", formatTime(stats.foregroundTimeMs), "Total foreground interaction time."))
                    }
                )
                com.example.batterymonitor.ui.components.V2BentoCard(
                    label = "Idle Drain",
                    value = String.format(Locale.getDefault(), "%.1f%%", stats.backgroundDrainPct),
                    modifier = Modifier.weight(1f).clickable {
                        onMetricClick(MetricInfo("Idle Drain", String.format("%.1f%%", stats.backgroundDrainPct), "Battery consumed while in background."))
                    },
                    color = if (stats.backgroundDrainPct > 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Detailed Time Analysis
        Text(
            text = "USAGE RETENTION",
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
        )
        
        com.example.batterymonitor.ui.components.V2GlassCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TimeStatItem("Foreground", formatTime(stats.foregroundTimeMs), String.format("%.1f%%", stats.foregroundDrainPct))
                Divider(modifier = Modifier.width(1.dp).height(40.dp).align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                TimeStatItem("Background", formatTime(stats.backgroundTimeMs), String.format("%.1f%%", stats.backgroundDrainPct))
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Detailed Resource Usage
        com.example.batterymonitor.ui.components.V2GlassCard {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CPU UTILIZATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f%%", stats.cpuIntensityScore),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((stats.cpuIntensityScore / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    )
                }
                Spacer(Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Wake locks detected: ${stats.wakeLockEstimate}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Trends
        Text(
            text = "CONSUMPTION HISTORY",
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
        )
        
        if (stats.dailyTrend.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Collecting data cycle...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        } else {
            com.example.batterymonitor.ui.components.V2TrendChart(trend = stats.dailyTrend)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text("System Management", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        
        Spacer(modifier = Modifier.height(40.dp))
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
                Text("Close", color = MaterialTheme.colorScheme.primary)
            }
        },
        title = {
            Text(
                text = metric.title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "Current Impact: ${metric.currentValue}",
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = metric.explanation,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun AnomalyWarningCard(type: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "ABNORMAL ACTIVITY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = type,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun TimeStatItem(label: String, time: String, drain: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(time, fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        Text("Est. Drain: $drain", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
        String.format(Locale.getDefault(), "%.2f GB", mb.toFloat() / 1024f)
    }
}
