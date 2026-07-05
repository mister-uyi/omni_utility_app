package com.omniutility.feature.ownyourtime.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omniutility.feature.ownyourtime.ui.sessionsetup.SessionSetupSheet
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    onSessionStarted: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    var showSetupSheet by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
        ) {
            item {
                GreetingSection(userName = uiState.userName)
            }
            
            stickyHeader {
                Column(modifier = Modifier.background(Color(0xFF0D0D0D))) {
                    MonthSummaryCard(
                        sessions = uiState.currentMonthSessions,
                        tasks = uiState.currentMonthTasks,
                        durationMs = uiState.currentMonthDurationMs,
                        delta = uiState.monthDeltaSessions,
                        selectedMonthOffset = uiState.selectedMonthOffset,
                        onSelectMonthOffset = { viewModel.selectMonthOffset(it) }
                    )
                }
            }
            
            item {
                ConfigSummaryCard(
                    defaultDurationMs = uiState.defaultDurationMs,
                    funBudgetPercent = uiState.defaultFunBudgetPercent,
                    prodAppCount = uiState.prodAppCount,
                    funAppCount = uiState.funAppCount,
                    sysAppCount = uiState.sysAppCount
                )
            }

            if (uiState.recentSessions.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Sessions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
                    )
                }
                items(uiState.recentSessions) { sessionWithTasks ->
                    RecentSessionCard(sessionWithTasks)
                }
            } else {
                item {
                    Text(
                        text = "No recent sessions.",
                        color = Color(0xFF8A8A8A),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        val activeSession = uiState.recentSessions.firstOrNull { it.session.endedAt == null }?.session

        ExtendedFloatingActionButton(
            onClick = { 
                if (activeSession != null) {
                    onSessionStarted(activeSession.id)
                } else {
                    showSetupSheet = true 
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFFF5A623),
            text = { Text(if (activeSession != null) "Resume Session" else "Start Session", color = Color.Black) },
            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black) }
        )
    }

    if (showSetupSheet) {
        SessionSetupSheet(
            onDismiss = { showSetupSheet = false },
            onSessionStarted = onSessionStarted
        )
    }
}

@Composable
fun GreetingSection(userName: String) {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    
    val dateStr = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(calendar.time)

    Column(modifier = Modifier.padding(16.dp)) {
        val nameText = if (userName.isNotBlank()) ", $userName" else ""
        Text(text = "$greeting$nameText", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(text = dateStr, fontSize = 14.sp, color = Color(0xFF8A8A8A))
    }
}

fun getMonthName(offset: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MONTH, offset)
    return SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSummaryCard(
    sessions: Int,
    tasks: Int,
    durationMs: Long,
    delta: Int,
    selectedMonthOffset: Int,
    onSelectMonthOffset: (Int) -> Unit
) {
    val totalMins = durationMs / (1000 * 60)
    val hours = totalMins / 60
    val mins = totalMins % 60
    val durationStr = if (hours > 0) {
        if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
    } else {
        "${mins}m"
    }

    val deltaText = if (delta >= 0) "+$delta from last month" else "$delta from last month"

    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        var selectedYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
        var selectedMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }

        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("Select Month") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedYear-- }) { Text("<", color = Color.White) }
                        Text(text = selectedYear.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        IconButton(onClick = { selectedYear++ }) { Text(">", color = Color.White) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items((0..11).toList()) { monthIndex ->
                            val cal = Calendar.getInstance().apply { set(Calendar.MONTH, monthIndex) }
                            val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
                            Text(
                                text = monthName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMonth = monthIndex }
                                    .background(if (selectedMonth == monthIndex) Color(0xFFF5A623).copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(12.dp),
                                color = if (selectedMonth == monthIndex) Color(0xFFF5A623) else Color.White
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val currentCal = Calendar.getInstance()
                    val targetCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, selectedYear)
                        set(Calendar.MONTH, selectedMonth)
                    }
                    val offset = (targetCal.get(Calendar.YEAR) - currentCal.get(Calendar.YEAR)) * 12 + 
                                 (targetCal.get(Calendar.MONTH) - currentCal.get(Calendar.MONTH))
                    onSelectMonthOffset(offset)
                    showDatePicker = false
                }) {
                    Text("OK", color = Color(0xFFF5A623))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1A1A1A),
            titleContentColor = Color.White
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isCustomSelected = selectedMonthOffset !in listOf(0, -1, -2)
                val customLabel = if (isCustomSelected) {
                    val cal = Calendar.getInstance().apply { add(Calendar.MONTH, selectedMonthOffset) }
                    SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(cal.time)
                } else {
                    "Custom"
                }

                listOf(
                    -3 to customLabel,
                    -1 to getMonthName(-1),
                    -2 to getMonthName(-2),
                    0 to "This Month"
                ).forEach { (offset, label) ->
                    val isSelected = selectedMonthOffset == offset || (offset == -3 && isCustomSelected)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color(0xFFF5A623).copy(alpha = 0.2f) else Color.Transparent)
                            .border(1.dp, if (isSelected) Color(0xFFF5A623) else Color(0xFF2A2A2A), RoundedCornerShape(16.dp))
                            .clickable {
                                if (offset == -3) {
                                    showDatePicker = true
                                } else {
                                    onSelectMonthOffset(offset)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(label, color = if (isSelected) Color(0xFFF5A623) else Color.White, fontSize = 14.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatColumn(label = "Sessions", value = sessions.toString())
                StatColumn(label = "Tasks Done", value = tasks.toString())
                StatColumn(label = "Duration", value = durationStr)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = deltaText, fontSize = 12.sp, color = Color(0xFF8A8A8A))
        }
    }
}

@Composable
fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(text = label, fontSize = 12.sp, color = Color(0xFF8A8A8A))
    }
}

@Composable
fun ConfigSummaryCard(
    defaultDurationMs: Long,
    funBudgetPercent: Int,
    prodAppCount: Int,
    funAppCount: Int,
    sysAppCount: Int
) {
    val durationMins = defaultDurationMs / (1000 * 60)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Defaults & Apps", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            val funMins = (defaultDurationMs * funBudgetPercent) / 100 / 60000
            Text(text = "Session: $durationMins min • Fun Budget: $funMins min ($funBudgetPercent%)", fontSize = 14.sp, color = Color(0xFF8A8A8A))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Apps: $prodAppCount Prod • $funAppCount Fun • $sysAppCount System", fontSize = 14.sp, color = Color(0xFF8A8A8A))
        }
    }
}

@Composable
fun RecentSessionCard(sessionWithTasks: SessionWithTasks) {
    val session = sessionWithTasks.session
    val tasks = sessionWithTasks.tasks
    
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val dateStr = dateFormat.format(session.startedAt)
    val durationMins = session.actualDurationMs / (1000 * 60)
    val plannedMins = session.plannedDurationMs / (1000 * 60)
    val actualStr = if (session.actualDurationMs > 0) "$durationMins min" else "$plannedMins min (planned)"
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val startTime = timeFormat.format(session.startedAt)
    val endTime = if (session.endedAt != null) timeFormat.format(session.endedAt!!) else "Ongoing"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = dateStr, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(text = "$startTime - $endTime • $actualStr", fontSize = 14.sp, color = Color(0xFF8A8A8A))
            }
            
            // Progress Bar
            val completedCount = tasks.count { it.completed }
            val totalCount = tasks.size
            val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$completedCount/$totalCount Tasks Completed",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(100.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFFF5A623),
                    trackColor = Color(0xFF2A2A2A)
                )
            }
        }
    }
}
