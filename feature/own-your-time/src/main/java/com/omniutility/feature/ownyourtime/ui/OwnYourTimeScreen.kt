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
import com.omniutility.feature.ownyourtime.ui.sessionsummary.SessionSummaryScreen

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
    var summarySessionId by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(OytTab.HOME) }

    if (activeSessionId != null) {
        SessionModeScreen(
            sessionId = activeSessionId!!,
            onSessionEnded = { 
                summarySessionId = activeSessionId
                activeSessionId = null 
            }
        )
    } else if (summarySessionId != null) {
        SessionSummaryScreen(
            sessionId = summarySessionId!!,
            onDone = { summarySessionId = null }
        )
    } else {
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
                        }
                    )
                    OytTab.TASKS -> TasksScreen()
                    OytTab.SETTINGS -> SettingsScreen()
                }
            }
        }
    }
}
