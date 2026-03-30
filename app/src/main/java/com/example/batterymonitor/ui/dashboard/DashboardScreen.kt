package com.example.batterymonitor.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.batterymonitor.ui.components.V2BentoCard
import com.example.batterymonitor.ui.components.V2CircularBatteryIndicator
import com.example.batterymonitor.ui.components.V2GlassCard
import com.example.batterymonitor.ui.components.V2TrendChart
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.batterymonitor.data.local.ChargeSession
import com.example.batterymonitor.ui.components.DynamicBatteryLogo
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    
    var currentTipIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60000)
            currentTipIndex = (currentTipIndex + 1) % BatteryTips.list.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // Hero Section: Circular Indicator
        Box(
            modifier = Modifier.fillMaxWidth().height(260.dp),
            contentAlignment = Alignment.Center
        ) {
            com.example.batterymonitor.ui.components.V2CircularBatteryIndicator(
                level = uiState.level,
                temperature = uiState.temperature,
                isHealthy = uiState.healthEstimate > 80f,
                modifier = Modifier.size(240.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Bento Grid: Metrics
        Text(
            text = "SYSTEM VITALITY",
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.example.batterymonitor.ui.components.V2BentoCard(
                    label = "Thermal",
                    value = "${uiState.temperature}°C",
                    modifier = Modifier.weight(1f),
                    color = if (uiState.temperature > 40) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                com.example.batterymonitor.ui.components.V2BentoCard(
                    label = "Health",
                    value = "${uiState.healthEstimate.roundToInt()}%",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.example.batterymonitor.ui.components.V2BentoCard(
                    label = "Voltage",
                    value = String.format(Locale.US, "%.2fV", uiState.voltage),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.secondary
                )
                com.example.batterymonitor.ui.components.V2BentoCard(
                    label = "Power",
                    value = if (uiState.isCharging && uiState.chargingWattage > 0) 
                        String.format(Locale.US, "%.1fW", uiState.chargingWattage) 
                    else if (uiState.isCharging) "Charging" else "Standby",
                    modifier = Modifier.weight(1f),
                    color = if (uiState.isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Consumption Trends
        Text(
            text = "POWER DYNAMICS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
        )
        
        // Map sessions to trend pairs
        val trendData = sessions.takeLast(7).map { 
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.startTime))
            time to (it.endLevel - it.startLevel).toFloat().coerceAtLeast(0f)
        }
        
        com.example.batterymonitor.ui.components.V2TrendChart(
            trend = trendData.ifEmpty { listOf("Now" to 0f) }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Live Intelligence Card
        com.example.batterymonitor.ui.components.V2GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "VITALITY INSIGHT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = BatteryTips.list[currentTipIndex],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}



