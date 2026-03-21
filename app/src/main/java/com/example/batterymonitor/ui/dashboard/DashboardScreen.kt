package com.example.batterymonitor.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    
    var currentTipIndex by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60000) // 1 minute
            currentTipIndex = (currentTipIndex + 1) % BatteryTips.list.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        
        // Main Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false) 
                .padding(bottom = 12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    DynamicBatteryLogo(
                        level = uiState.level,
                        modifier = Modifier.size(80.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${uiState.level}%",
                            fontSize = 64.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (uiState.level < 20) Color.Red else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (uiState.isCharging) "Charging" else "Discharging",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isCharging) Color(0xFF4CAF50) else Color.Gray
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Divider(modifier = Modifier.alpha(0.3f), thickness = 0.5.dp, color = Color.Gray)
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

        Text(
            text = "Charging Trends",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()
        )
        
        Box(modifier = Modifier.fillMaxWidth().weight(1.5f)) {
            MixedBatteryChart(sessions)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Rotating Battery Advice Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = BatteryTips.list[currentTipIndex],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}


@Composable
fun MixedBatteryChart(sessions: List<ChargeSession>) {
    val displaySessions = sessions.filter { it.isComplete && it.endLevel != -1 }.take(7).reversed()
    if (displaySessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Text("No comparative data available yet.", color = Color.Gray)
        }
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(top = 24.dp, bottom = 12.dp, start = 40.dp, end = 16.dp)
    ) {
        val width = size.width
        val height = size.height
        val spacing = width / (displaySessions.size.coerceAtLeast(1))
        
        // Draw Y-axis labels and grid
        val levels = listOf(0, 50, 100)
        levels.forEach { level ->
            val y = height - (level / 100f * height)
            drawLine(
                color = Color.LightGray.copy(alpha = 0.3f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                "$level%",
                -35.dp.toPx(),
                y + 5.dp.toPx(),
                android.graphics.Paint().apply {
                    color = labelColor
                    textSize = 10.sp.toPx()
                    alpha = 150
                }
            )
        }

        displaySessions.forEachIndexed { index, session ->
            val x = index * spacing + (spacing / 2)
            val yStart = height - (session.startLevel / 100f * height)
            val yEnd = height - (session.endLevel / 100f * height)

            // Draw Bar (Gain)
            if (session.endLevel > session.startLevel) {
                drawRect(
                    color = primaryColor.copy(alpha = 0.15f),
                    topLeft = Offset(x - 10.dp.toPx(), yEnd),
                    size = Size(20.dp.toPx(), (yStart - yEnd).coerceAtLeast(1f))
                )
            }

            // Draw Line for the session
            drawLine(
                color = primaryColor,
                start = Offset(x, yStart),
                end = Offset(x, yEnd),
                strokeWidth = 3.dp.toPx()
            )
            
            // Dots at start and end
            drawCircle(color = secondaryColor, radius = 4.dp.toPx(), center = Offset(x, yStart))
            drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = Offset(x, yEnd))
            
            // Labels
            drawContext.canvas.nativeCanvas.drawText(
                "${session.startLevel}%",
                x - 12.dp.toPx(),
                yStart + 15.dp.toPx(),
                android.graphics.Paint().apply {
                    color = labelColor
                    textSize = 8.sp.toPx()
                }
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${session.endLevel}%",
                x - 12.dp.toPx(),
                yEnd - 10.dp.toPx(),
                android.graphics.Paint().apply {
                    color = labelColor
                    textSize = 9.sp.toPx()
                    isFakeBoldText = true
                }
            )

            // Connection Logic: Link end of this session to start of next session (Discharge)
            if (index < displaySessions.size - 1) {
                val nextSession = displaySessions[index + 1]
                val nextX = (index + 1) * spacing + (spacing / 2)
                val nextYStart = height - (nextSession.startLevel / 100f * height)
                
                drawLine(
                    color = Color.Gray.copy(alpha = 0.3f),
                    start = Offset(x, yEnd),
                    end = Offset(nextX, nextYStart),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                )
            }

            // Draw X-axis Time Label
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.startTime))
            drawContext.canvas.nativeCanvas.drawText(
                timeStr,
                x - 15.dp.toPx(),
                height + 15.dp.toPx(),
                android.graphics.Paint().apply {
                    color = labelColor
                    textSize = 8.sp.toPx()
                }
            )
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.Gray, fontSize = 14.sp)
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
