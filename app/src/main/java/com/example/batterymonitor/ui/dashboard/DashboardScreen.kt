package com.example.batterymonitor.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        // Main Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dynamic Battery Logo with 5% Stepped Clipping
                DynamicBatteryLogo(
                    level = uiState.level,
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "${uiState.level}%",
                        fontSize = 72.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (uiState.level < 20) Color.Red else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (uiState.isCharging) "Charging" else "Discharging",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isCharging) Color(0xFF4CAF50) else Color.Gray
                    )
                }
            }
            
            Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray)
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem("Temp", "${uiState.temperature}°C")
                StatItem("Voltage", String.format(Locale.US, "%.2f V", uiState.voltage))
                StatItem("Health", "${uiState.healthEstimate.roundToInt()}%")
            }
        }

        Text(
            text = "Charging Trends",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth()
        )
        
        // New Mixed Chart
        MixedBatteryChart(sessions)
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            "Tip: Keep battery between 20-80% for longevity.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )
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
