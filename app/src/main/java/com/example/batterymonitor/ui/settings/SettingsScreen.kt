package com.example.batterymonitor.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.batterymonitor.data.pref.AppTheme
import com.example.batterymonitor.ui.components.V2GlassCard

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val appTheme by viewModel.appTheme.collectAsState()
    val isChecking by viewModel.isCheckingUpdate.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val restoreStatus by viewModel.restoreStatus.collectAsState()

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.restoreBackup(it, force = false) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "APPEARANCE",
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        V2GlassCard {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Theme", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = appTheme == AppTheme.DARK,
                        onCheckedChange = { isChecked ->
                            viewModel.setTheme(if (isChecked) AppTheme.DARK else AppTheme.LIGHT)
                        },
                        modifier = Modifier.scale(0.8f)
                    )
                }
                Divider(modifier = Modifier.padding(horizontal = 8.dp).alpha(0.05f))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.setTheme(if (appTheme == AppTheme.SYSTEM) AppTheme.LIGHT else AppTheme.SYSTEM)
                    }.padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = appTheme == AppTheme.SYSTEM,
                        onCheckedChange = { isChecked ->
                            viewModel.setTheme(if (isChecked) AppTheme.SYSTEM else AppTheme.LIGHT)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Follow System Settings", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Switch appearance based on OS", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "SYSTEM UPDATES",
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        V2GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.checkForUpdates() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Check for Updates", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Connect to GitHub to verify version", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "DATA & BACKUP",
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        V2GlassCard {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { launcher.launch("application/json") }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Restore Backup", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Load history from a saved file", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (restoreStatus is RestoreStatus.Loading) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Restoring data...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        } else if (restoreStatus == RestoreStatus.Success) {
            LaunchedEffect(restoreStatus) {
                Toast.makeText(context, "Data restored successfully!", Toast.LENGTH_SHORT).show()
                viewModel.resetRestoreStatus()
            }
        } else if (restoreStatus is RestoreStatus.Error) {
            LaunchedEffect(restoreStatus) {
                Toast.makeText(context, (restoreStatus as RestoreStatus.Error).message, Toast.LENGTH_LONG).show()
                viewModel.resetRestoreStatus()
            }
        }

        // Modals
        if (updateInfo != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearUpdateInfo() },
                title = { Text("Update Available") },
                text = { 
                    Column {
                        Text("A new version (${updateInfo?.version}) is available. Would you like to download and install it?")
                        if (updateInfo?.body?.isNotEmpty() == true) {
                            Spacer(Modifier.height(8.dp))
                            Text("Whats new:", fontWeight = FontWeight.Bold)
                            Text(updateInfo!!.body)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.downloadUpdate() }) {
                        Text("Download & Install")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearUpdateInfo() }) {
                        Text("Later")
                    }
                }
            )
        }

        if (restoreStatus is RestoreStatus.Warning) {
            val warning = restoreStatus as RestoreStatus.Warning
            AlertDialog(
                onDismissRequest = { viewModel.resetRestoreStatus() },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Older Backup Detected", color = MaterialTheme.colorScheme.error) 
                    }
                },
                text = { Text(warning.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.restoreBackup(warning.uri, force = true) }) {
                        Text("Restore Anyway", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.resetRestoreStatus() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
