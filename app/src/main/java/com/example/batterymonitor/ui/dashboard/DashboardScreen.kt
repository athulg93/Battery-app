package com.example.batterymonitor.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.batterymonitor.data.local.ChargeSession
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sessions by viewModel.sessions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery Monitor", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${uiState.level}%",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.level < 20) Color.Red else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (uiState.isCharging) "Charging" else "Discharging",
                        fontSize = 20.sp,
                        color = if (uiState.isCharging) Color(0xFF4CAF50) else Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem("Temp", "${uiState.temperature}°C")
                        StatItem("Voltage", String.format(Locale.US, "%.2f V", uiState.voltage))
                        StatItem("Health", "${uiState.healthEstimate.roundToInt()}%")
                    }
                }
            }

            // Custom Battery Chart (Canvas) - No libraries used
            BatteryLevelChart(sessions)
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Recent Charge Sessions",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(vertical = 8.dp)
            )

            // History List
            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("No charging sessions recorded yet.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(sessions) { session ->
                        SessionItem(session)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Per-App Battery Drain (Est.)",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(vertical = 8.dp)
            )
            
            val appUsage by viewModel.appUsage.collectAsState()
            
            if (appUsage.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("No usage data available (Grant permission or wait)", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(appUsage) { usage ->
                        AppUsageItem(usage)
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryLevelChart(sessions: List<ChargeSession>) {
    if (sessions.isEmpty()) return
    
    // Simplistic canvas bar chart showing charge amounts
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(16.dp)
    ) {
        val width = size.width
        val height = size.height
        val maxItems = sessions.size.coerceAtMost(10)
        val barWidth = width / (maxItems * 1.5f)
        val primaryColor = Color(0xFF6650a4)
        
        val recentSessions = sessions.take(maxItems).reversed()
        recentSessions.forEachIndexed { index, session ->
            val chargeAmount = if (session.isComplete) (session.endLevel - session.startLevel).coerceAtLeast(0) else 0
            val barHeight = (chargeAmount / 100f) * height
            
            val x = index * (width / maxItems)
            val y = height - barHeight
            
            drawRect(
                color = primaryColor,
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
            )
        }
    }
}

@Composable
fun AppUsageItem(usage: AppUsageSummary) {
    // Constraint: Any app/service exceeding 40% usage must be highlighted in Red.
    val usageColor = if (usage.estimatedDrainPct > 40f) Color.Red else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Strip off the long java package prefix for readability if it's there
        val simpleName = usage.packageName.substringAfterLast(".") 
        
        Text(
            text = simpleName.take(20), // truncate if too long
            fontWeight = FontWeight.Medium,
            color = usageColor
        )
        Text(
            text = String.format("%.1f %%", usage.estimatedDrainPct),
            fontWeight = FontWeight.Bold,
            color = usageColor
        )
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.Gray, fontSize = 14.sp)
        Text(text = value, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    }
}

@Composable
fun SessionItem(session: ChargeSession) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val format = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            val dateStr = format.format(Date(session.startTime))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = dateStr, fontWeight = FontWeight.Bold)
                if (session.isComplete) {
                    Text(text = "+${session.endLevel - session.startLevel}%", color = Color(0xFF4CAF50))
                } else {
                    Text(text = "In Progress...", color = Color(0xFFFFA000))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "From ${session.startLevel}% to ${if (session.isComplete) session.endLevel else "?"}%",
                fontSize = 14.sp
            )
        }
    }
}
