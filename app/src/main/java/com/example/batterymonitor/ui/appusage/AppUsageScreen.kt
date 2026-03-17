package com.example.batterymonitor.ui.appusage

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.batterymonitor.ui.dashboard.AppUsageSummary
import com.example.batterymonitor.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.delay

@Composable
fun AppUsageScreen(
    onAppClick: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val appUsage by viewModel.appUsage.collectAsState()
    val context = LocalContext.current
    val packageManager = context.packageManager

    LaunchedEffect(Unit) {
        while (true) {
            com.example.batterymonitor.worker.UsageStatsWorker.runOnce(context)
            delay(30000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (appUsage.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No usage data available.", color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { 
                        com.example.batterymonitor.utils.PermissionHelper.requestUsageStatsPermission(context)
                    }) {
                        Text("Grant Usage Stats Permission")
                    }
                }
            }
        } else {
            Text(
                text = "Estimated Battery Impact",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(appUsage) { usage ->
                    val appInfo = remember(usage.packageName) {
                        try {
                            val info = packageManager.getApplicationInfo(usage.packageName, 0)
                            val label = packageManager.getApplicationLabel(info).toString()
                            val icon = packageManager.getApplicationIcon(info)
                            label to icon
                        } catch (e: Exception) {
                            usage.packageName.substringAfterLast(".") to null
                        }
                    }

                    AppUsageCard(
                        name = appInfo.first,
                        packageName = usage.packageName,
                        drainPct = usage.estimatedDrainPct,
                        icon = appInfo.second
                    ) {
                        onAppClick(usage.packageName)
                    }
                }
            }
        }
    }
}

@Composable
fun AppUsageCard(
    name: String,
    packageName: String,
    drainPct: Float,
    icon: android.graphics.drawable.Drawable?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { context ->
                        android.widget.ImageView(context).apply {
                            setImageDrawable(icon)
                        }
                    },
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            name.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = packageName,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = drainPct / 100f,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (drainPct > 40f) Color.Red else MaterialTheme.colorScheme.primary,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = String.format("%.1f%%", drainPct),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = if (drainPct > 40f) Color.Red else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
