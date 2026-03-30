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
import com.example.batterymonitor.data.repository.AppUsageSummary
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.painterResource
import com.example.batterymonitor.ui.appusage.AppUsageViewModel
import java.util.Locale
import kotlin.math.roundToInt

import com.example.batterymonitor.ui.components.V2GlassCard
import com.example.batterymonitor.ui.components.V2AppDrainItem
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun AppUsageScreen(
    onAppClick: (String) -> Unit,
    viewModel: AppUsageViewModel = viewModel()
) {
    val appUsage by viewModel.appUsage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val context = LocalContext.current

    val totalConsumptionMah = appUsage.sumOf { it.estimatedDrainMah.toDouble() }.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (appUsage.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Vitality data pending calibration", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { com.example.batterymonitor.utils.PermissionHelper.requestUsageStatsPermission(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Initialize Analysis Engine", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Hero: Total Consumption
            V2GlassCard {
                Column {
                    Text(
                        text = "TOTAL CONSUMPTION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.US, "%,.0f", totalConsumptionMah),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "mAh",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 10.dp, start = 8.dp)
                        )
                    }
                    Text(
                        text = "Analysis since last full charge cycle",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IMPACT BREAKDOWN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                
                // Sorting Chiplist
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortChip("Drain", sortOrder == AppSortOrder.DRAIN) { viewModel.setSortOrder(AppSortOrder.DRAIN) }
                    SortChip("Time", sortOrder == AppSortOrder.TIME) { viewModel.setSortOrder(AppSortOrder.TIME) }
                    SortChip("Name", sortOrder == AppSortOrder.NAME) { viewModel.setSortOrder(AppSortOrder.NAME) }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(
                    items = appUsage,
                    key = { it.packageName }
                ) { usage ->
                    val timeMinutes = usage.foregroundTimeMs / (1000 * 60)
                    val timeText = if (timeMinutes > 60) "${timeMinutes / 60}h ${timeMinutes % 60}m active" else "${timeMinutes}m active"
                    
                    V2AppDrainItem(
                        label = usage.label,
                        percentage = usage.estimatedDrainPct.roundToInt(),
                        time = timeText,
                        drainMah = usage.estimatedDrainMah,
                        icon = {
                            AppIconImage(packageName = usage.packageName)
                        },
                        modifier = Modifier.clickable { onAppClick(usage.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun AppIconImage(packageName: String) {
    val context = LocalContext.current
    var appIcon by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
    
    LaunchedEffect(packageName) {
        try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            appIcon = context.packageManager.getApplicationIcon(info)
        } catch (e: Exception) {}
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(appIcon ?: com.example.batterymonitor.R.drawable.ic_battery_placeholder)
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = Modifier.size(32.dp),
        error = painterResource(id = com.example.batterymonitor.R.drawable.ic_battery_placeholder),
        fallback = painterResource(id = com.example.batterymonitor.R.drawable.ic_battery_placeholder)
    )
}
