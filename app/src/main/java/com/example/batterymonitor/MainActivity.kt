package com.example.batterymonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.batterymonitor.service.BatteryMonitorService
import com.example.batterymonitor.ui.navigation.BatteryNavGraph
import com.example.batterymonitor.ui.navigation.Screen
import com.example.batterymonitor.ui.theme.VoltMonitorTheme
import com.example.batterymonitor.utils.PermissionHelper
import com.example.batterymonitor.utils.UpdateManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdateInfo(val version: String, val downloadUrl: String, val body: String)

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startBatteryService()
        }
    }

    private lateinit var themeManager: com.example.batterymonitor.data.pref.ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        themeManager = com.example.batterymonitor.data.pref.ThemeManager(this)
        
        checkUpdateCompletion()
        checkAndRequestPermissions()

        setContent {
            val appTheme by themeManager.themeFlow.collectAsState(initial = com.example.batterymonitor.data.pref.AppTheme.SYSTEM)
            VoltMonitorTheme(appTheme = appTheme) {
                MainAppContent(appTheme = appTheme)
            }
        }
    }

    private fun checkUpdateCompletion() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastVersionCode = prefs.getInt("last_version_code", -1)
        val currentVersionCode = BuildConfig.VERSION_CODE
        
        if (lastVersionCode != -1 && currentVersionCode > lastVersionCode) {
            Toast.makeText(this, "Volt Monitor successfully updated to ${BuildConfig.VERSION_NAME}!", Toast.LENGTH_LONG).show()
        }
        
        prefs.edit().putInt("last_version_code", currentVersionCode).apply()
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainAppContent(appTheme: com.example.batterymonitor.data.pref.AppTheme) {
        val navController = rememberNavController()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        
        val items = listOf(Screen.Dashboard, Screen.AppUsage, Screen.History)

        var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
        var isCheckingForUpdate by remember { mutableStateOf(false) }

        if (updateInfo != null) {
            AlertDialog(
                onDismissRequest = { updateInfo = null },
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
                    TextButton(onClick = {
                        UpdateManager.downloadAndInstallUpdate(
                            this@MainActivity,
                            updateInfo!!.downloadUrl,
                            "BatteryMonitor_${updateInfo!!.version}.apk"
                        )
                        updateInfo = null
                        Toast.makeText(this@MainActivity, "Download started...", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Download & Install")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { updateInfo = null }) {
                        Text("Later")
                    }
                }
            )
        }

        if (isCheckingForUpdate) {
            AlertDialog(
                onDismissRequest = { },
                confirmButton = { },
                title = { Text("Checking for Updates") },
                text = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Connecting to GitHub...")
                    }
                }
            )
        }
        
        // Handle back press to close drawer
        if (drawerState.isOpen) {
            BackHandler {
                scope.launch { drawerState.close() }
            }
        }

        val baseTitle = when (currentRoute) {
            Screen.Dashboard.route -> Screen.Dashboard.title
            Screen.AppUsage.route -> Screen.AppUsage.title
            Screen.History.route -> Screen.History.title
            else -> ""
        }
        val displayTitle = if (currentRoute?.startsWith("app_detail") == true) {
            "Volt Monitor - App Analysis"
        } else if (baseTitle.isNotEmpty()) {
            "Volt Monitor - $baseTitle"
        } else {
            "Volt Monitor"
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerContentColor = MaterialTheme.colorScheme.onSurface,
                    drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)
                ) {
                    // Drawer Header with Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                )
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.example.batterymonitor.ui.components.DynamicBatteryLogo(
                                level = 100,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Volt Monitor",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    "V2.0 STITCH EDITION",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    items.forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) },
                            selected = currentRoute == item.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when(item) {
                                        Screen.Dashboard -> Icons.Default.Info
                                        Screen.AppUsage -> Icons.Default.Menu
                                        Screen.History -> Icons.Default.Info
                                        else -> Icons.Default.Info
                                    },
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Divider(modifier = Modifier.padding(horizontal = 24.dp).alpha(0.1f))
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        text = "PREFERENCES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                    )

                    // Unified Theme Control Card
                    Surface(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            ListItem(
                                headlineContent = { Text("Dark Theme", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                                trailingContent = {
                                    Switch(
                                        checked = appTheme == com.example.batterymonitor.data.pref.AppTheme.DARK,
                                        onCheckedChange = { isChecked ->
                                            scope.launch {
                                                themeManager.setTheme(if (isChecked) com.example.batterymonitor.data.pref.AppTheme.DARK else com.example.batterymonitor.data.pref.AppTheme.LIGHT)
                                            }
                                        },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            Divider(modifier = Modifier.padding(horizontal = 12.dp).alpha(0.05f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            themeManager.setTheme(if (appTheme == com.example.batterymonitor.data.pref.AppTheme.SYSTEM) com.example.batterymonitor.data.pref.AppTheme.LIGHT else com.example.batterymonitor.data.pref.AppTheme.SYSTEM)
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = appTheme == com.example.batterymonitor.data.pref.AppTheme.SYSTEM,
                                    onCheckedChange = { isChecked ->
                                        scope.launch {
                                            themeManager.setTheme(if (isChecked) com.example.batterymonitor.data.pref.AppTheme.SYSTEM else com.example.batterymonitor.data.pref.AppTheme.LIGHT)
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Follow System Settings", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Volt Monitor v2.0",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "Engineered with Stitch UI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    val isAppDetail = currentRoute?.startsWith("app_detail") == true
                    TopAppBar(
                        title = { Text(displayTitle, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            if (isAppDetail) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                            } else {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            }
                        },
                        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { padding ->
                Surface(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BatteryNavGraph(navController = navController)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionHelper.hasUsageStatsPermission(this)) {
            com.example.batterymonitor.worker.UsageStatsWorker.runOnce(this)
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startBatteryService()
            }
        } else {
            startBatteryService()
        }
    }

    private fun startBatteryService() {
        val serviceIntent = Intent(this, BatteryMonitorService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun checkForUpdates(
        context: Context,
        onShowProgress: (Boolean) -> Unit,
        onUpdateFound: (UpdateInfo) -> Unit
    ) {
        val scope = (context as MainActivity).lifecycleScope
        onShowProgress(true)
        
        UpdateManager.checkForUpdates(context, object : UpdateManager.UpdateCallback {
            override fun onUpdateAvailable(newVersion: String, downloadUrl: String, body: String) {
                scope.launch {
                    onShowProgress(false)
                    onUpdateFound(UpdateInfo(newVersion, downloadUrl, body))
                }
            }

            override fun onNoUpdate() {
                 scope.launch {
                    onShowProgress(false)
                    Toast.makeText(context, "You are on the latest version", Toast.LENGTH_SHORT).show()
                 }
            }

            override fun onError(message: String) {
                scope.launch {
                    onShowProgress(false)
                    Toast.makeText(context, "Update check failed: $message", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}
