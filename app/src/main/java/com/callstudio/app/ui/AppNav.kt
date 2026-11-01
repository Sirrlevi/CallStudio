package com.callstudio.app.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.callstudio.app.ui.screens.AudioSettingsScreen
import com.callstudio.app.ui.screens.CallerEditScreen
import com.callstudio.app.ui.screens.CallersScreen
import com.callstudio.app.ui.screens.DashboardScreen
import com.callstudio.app.ui.screens.HistoryScreen
import com.callstudio.app.ui.screens.OutgoingScreen
import com.callstudio.app.ui.screens.OutgoingSetupScreen
import com.callstudio.app.ui.screens.PreviewScreen
import com.callstudio.app.ui.screens.RecordingsScreen
import com.callstudio.app.ui.screens.ScheduleScreen
import com.callstudio.app.ui.screens.ScheduledScreen
import com.callstudio.app.ui.screens.SettingsScreen
import com.callstudio.app.ui.screens.SetupScreen
import com.callstudio.app.ui.screens.ThemeScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val CALLERS = "callers"
    const val SCHEDULED = "scheduled"
    const val SETTINGS = "settings"
    const val THEMES = "themes"
    const val AUDIO = "audio"
    const val RECORDINGS = "recordings"
    const val HISTORY = "history"
    const val SETUP = "setup"
    const val OUTGOING = "outgoing"
    const val OUTGOING_SETUP = "outgoing_setup"
    const val CALLER_EDIT = "caller/{id}"
    const val SCHEDULE = "schedule?callerId={callerId}&eventId={eventId}&mode={mode}"
    const val PREVIEW = "preview/{callerId}"

    fun callerEdit(id: Long) = "caller/$id"
    fun schedule(callerId: Long = -1L, eventId: Long = -1L, mode: Int = 1) =
        "schedule?callerId=$callerId&eventId=$eventId&mode=$mode"
    fun preview(callerId: Long) = "preview/$callerId"
}

private data class BarItem(val route: String, val label: String, val icon: ImageVector)

private val barItems = listOf(
    BarItem(Routes.DASHBOARD, "Home", Icons.Filled.Home),
    BarItem(Routes.CALLERS, "Callers", Icons.Filled.Contacts),
    BarItem(Routes.SCHEDULED, "Scheduled", Icons.Filled.Alarm),
    BarItem(Routes.OUTGOING, "Outgoing", Icons.Filled.CallMade),
    BarItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val topLevel = barItems.any { it.route == route }
    val fullBleed = route?.startsWith("preview") == true

    Scaffold(
        bottomBar = {
            if (topLevel) {
                NavigationBar {
                    barItems.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(Routes.DASHBOARD) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.DASHBOARD,
            modifier = if (fullBleed) Modifier.fillMaxSize() else Modifier.padding(inner).consumeWindowInsets(inner),
        ) {
            composable(Routes.DASHBOARD) { DashboardScreen(nav) }
            composable(Routes.CALLERS) { CallersScreen(nav) }
            composable(Routes.SCHEDULED) { ScheduledScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.OUTGOING) { OutgoingScreen(nav) }
            composable(Routes.OUTGOING_SETUP) { OutgoingSetupScreen(nav) }
            composable(Routes.THEMES) { ThemeScreen(nav) }
            composable(Routes.AUDIO) { AudioSettingsScreen(nav) }
            composable(Routes.RECORDINGS) { RecordingsScreen(nav) }
            composable(Routes.HISTORY) { HistoryScreen(nav) }
            composable(Routes.SETUP) { SetupScreen(nav) }
            composable(
                Routes.CALLER_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { CallerEditScreen(nav, it.arguments?.getLong("id") ?: 0L) }
            composable(
                Routes.SCHEDULE,
                arguments = listOf(
                    navArgument("callerId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("eventId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("mode") { type = NavType.IntType; defaultValue = 1 },
                ),
            ) {
                ScheduleScreen(
                    nav,
                    callerId = it.arguments?.getLong("callerId") ?: -1L,
                    eventId = it.arguments?.getLong("eventId") ?: -1L,
                    initialMode = it.arguments?.getInt("mode") ?: 1,
                )
            }
            composable(
                Routes.PREVIEW,
                arguments = listOf(navArgument("callerId") { type = NavType.LongType }),
            ) { PreviewScreen(nav, it.arguments?.getLong("callerId") ?: -1L) }
        }
    }
}
