package com.example.batterymonitor.ui.history

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
import com.example.batterymonitor.ui.dashboard.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryDetailsScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val sessions by viewModel.sessions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No charging history found.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
            ) {
                items(sessions) { session ->
                    HistoryItem(session)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(session: ChargeSession) {
    val format = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
    val dateStr = format.format(Date(session.startTime))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = dateStr, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Gray)
                
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when {
                        session.isComplete && session.endLevel != -1 -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                        !session.isComplete -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else -> Color.Gray.copy(alpha = 0.1f)
                    }
                ) {
                    Text(
                        text = when {
                            session.isComplete && session.endLevel != -1 -> "Complete"
                            !session.isComplete -> "In Progress"
                            else -> "Interrupted"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            session.isComplete && session.endLevel != -1 -> Color(0xFF4CAF50)
                            !session.isComplete -> MaterialTheme.colorScheme.primary
                            else -> Color.Gray
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Level Change", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        "${session.startLevel}% → ${if (session.isComplete && session.endLevel != -1) "${session.endLevel}%" else "..."}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                
                if (session.isComplete && session.endLevel != -1) {
                    val gain = session.endLevel - session.startLevel
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Gain", fontSize = 12.sp, color = Color.Gray)
                        Text("+$gain%", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF4CAF50))
                    }
                }
            }
            
            if (session.endTime > 0) {
                val durationMs = session.endTime - session.startTime
                val minutes = durationMs / (1000 * 60)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Duration: ${minutes}m",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
