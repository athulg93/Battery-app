package com.example.batterymonitor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.batterymonitor.ui.appusage.AppUsageScreen
import com.example.batterymonitor.ui.dashboard.DashboardScreen
import com.example.batterymonitor.ui.history.HistoryDetailsScreen

sealed class Screen(val route: String, val title: String) {
    object Dashboard : Screen("dashboard", "Dashboard")
    object AppUsage : Screen("app_usage", "App Based Usage")
    object History : Screen("history", "Charging History")
    object AppDetail : Screen("app_detail/{packageName}", "App Details") {
        fun createRoute(packageName: String) = "app_detail/$packageName"
    }
}

@Composable
fun BatteryNavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen()
        }
        composable(Screen.AppUsage.route) {
            AppUsageScreen(onAppClick = { pkg ->
                navController.navigate(Screen.AppDetail.createRoute(pkg))
            })
        }
        composable(Screen.History.route) {
            HistoryDetailsScreen()
        }
        composable(
            route = Screen.AppDetail.route,
            arguments = listOf(androidx.navigation.navArgument("packageName") { 
                type = androidx.navigation.NavType.StringType 
            })
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            com.example.batterymonitor.ui.appdetail.AppDetailScreen(
                packageName = packageName,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
