package com.omniutility.feature.ownyourtime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.omniutility.feature.ownyourtime.ui.dashboard.DashboardScreen
import com.omniutility.feature.ownyourtime.ui.settings.SettingsScreen
import com.omniutility.feature.ownyourtime.ui.tasks.TasksScreen
import com.omniutility.feature.ownyourtime.ui.sessionmode.SessionModeScreen
import com.omniutility.feature.ownyourtime.ui.allsessions.AllSessionsScreen
import com.omniutility.feature.ownyourtime.ui.sessionsetup.SessionSetupSheet
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext

private enum class OytTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.DateRange),
    TASKS("Tasks", Icons.AutoMirrored.Filled.List),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun OwnYourTimeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var showAllSessions by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(OytTab.HOME) }
    var showSetupSheet by remember { mutableStateOf(false) }

    val activity = LocalContext.current as? ComponentActivity
    
    androidx.compose.runtime.DisposableEffect(activity) {
        val listener = androidx.core.util.Consumer<android.content.Intent> { newIntent ->
            if (newIntent.getBooleanExtra("EXTRA_SHOW_SESSION_SETUP", false)) {
                showSetupSheet = true
                newIntent.removeExtra("EXTRA_SHOW_SESSION_SETUP")
            }
        }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    LaunchedEffect(activity?.intent) {
        if (activity?.intent?.getBooleanExtra("EXTRA_SHOW_SESSION_SETUP", false) == true) {
            showSetupSheet = true
            activity.intent.removeExtra("EXTRA_SHOW_SESSION_SETUP")
        }
    }

    if (activeSessionId != null) {
        SessionModeScreen(
            sessionId = activeSessionId!!,
            onSessionEnded = { 
                activeSessionId = null 
            }
        )
    } else if (showAllSessions) {
        BackHandler {
            showAllSessions = false
        }
        AllSessionsScreen(
            onBack = { showAllSessions = false }
        )
    } else {
        if (selectedTab != OytTab.HOME) {
            BackHandler {
                selectedTab = OytTab.HOME
            }
        }
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF1A1A1A)) {
                    OytTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFF5A623),
                                selectedTextColor = Color(0xFFF5A623),
                                indicatorColor = Color(0xFF2A2A2A),
                                unselectedIconColor = Color(0xFF8A8A8A),
                                unselectedTextColor = Color(0xFF8A8A8A)
                            )
                        )
                    }
                }
            },
            containerColor = Color(0xFF0D0D0D),
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    OytTab.HOME -> DashboardScreen(
                        onSessionStarted = { sessionId ->
                            activeSessionId = sessionId
                        },
                        onShowSetupSheet = {
                            showSetupSheet = true
                        },
                        onViewAllClick = {
                            showAllSessions = true
                        }
                    )
                    OytTab.TASKS -> TasksScreen()
                    OytTab.SETTINGS -> SettingsScreen()
                }
            }
        }
    }

    if (showSetupSheet) {
        SessionSetupSheet(
            onDismiss = { showSetupSheet = false },
            onSessionStarted = { sessionId ->
                activeSessionId = sessionId
                showSetupSheet = false
            }
        )
    }
}
