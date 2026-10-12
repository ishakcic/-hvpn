package com.ihvpn.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ihvpn.app.IhvpnApplication
import com.ihvpn.app.MainActivity
import com.ihvpn.app.ui.screens.HomeScreen
import com.ihvpn.app.ui.screens.LogsScreen
import com.ihvpn.app.ui.screens.ProfilesScreen
import com.ihvpn.app.ui.screens.SettingsScreen

private sealed class Dest(val route: String, val label: String) {
    data object Home : Dest("home", "Ana Sayfa")
    data object Profiles : Dest("profiles", "Profiller")
    data object Settings : Dest("settings", "Ayarlar")
    data object Logs : Dest("logs", "Loglar")
}

@Composable
fun IhvpnNavHost(
    application: IhvpnApplication,
    activity: MainActivity,
    incomingDeepLinkUri: String?,
    autoRequestVpnPermission: Boolean
) {
    val navController = rememberNavController()
    val items = listOf(Dest.Home, Dest.Profiles, Dest.Settings, Dest.Logs)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination
                items.forEach { dest ->
                    val icon = when (dest) {
                        Dest.Home -> Icons.Filled.Home
                        Dest.Profiles -> Icons.Filled.List
                        Dest.Settings -> Icons.Filled.Settings
                        Dest.Logs -> Icons.Filled.Terminal
                    }
                    NavigationBarItem(
                        selected = currentRoute?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Home.route) {
                HomeScreen(application = application, activity = activity)
            }
            composable(Dest.Profiles.route) {
                ProfilesScreen(application = application, incomingDeepLinkUri = incomingDeepLinkUri)
            }
            composable(Dest.Settings.route) {
                SettingsScreen(application = application, activity = activity)
            }
            composable(Dest.Logs.route) {
                LogsScreen()
            }
        }
    }
}
