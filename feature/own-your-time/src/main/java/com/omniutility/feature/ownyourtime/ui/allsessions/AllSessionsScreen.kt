package com.omniutility.feature.ownyourtime.ui.allsessions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omniutility.feature.ownyourtime.ui.dashboard.DateGroupHeader
import com.omniutility.feature.ownyourtime.ui.dashboard.RecentSessionCard
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllSessionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AllSessionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showSortDropdown by remember { mutableStateOf(false) }

    val accentColor = Color(0xFFF5A623)
    val surfaceColor = Color(0xFF1A1A1A)
    val borderColor = Color(0xFF2A2A2A)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Sessions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D0D0D),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0D0D0D),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter and Sort controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Calendar Range Filter
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(surfaceColor, RoundedCornerShape(8.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Calendar",
                        tint = if (uiState.selectedStartDate != null) accentColor else Color(0xFF8A8A8A),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.dateRangeText,
                        color = if (uiState.selectedStartDate != null) Color.White else Color(0xFF8A8A8A),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.selectedStartDate != null) {
                        IconButton(
                            onClick = { viewModel.clearDateRange() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear date",
                                tint = Color(0xFF8A8A8A),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Sort Dropdown Trigger
                Box {
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .background(surfaceColor, RoundedCornerShape(8.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                            .clickable { showSortDropdown = true }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when (uiState.selectedSort) {
                                SessionSort.DATE_NEWEST -> "Date (Newest)"
                                SessionSort.DATE_OLDEST -> "Date (Oldest)"
                                SessionSort.DURATION_LONGEST -> "Duration (Longest)"
                                SessionSort.DURATION_SHORTEST -> "Duration (Shortest)"
                            },
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Sort",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showSortDropdown,
                        onDismissRequest = { showSortDropdown = false },
                        modifier = Modifier.background(surfaceColor)
                    ) {
                        SessionSort.entries.forEach { sortOption ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = when (sortOption) {
                                            SessionSort.DATE_NEWEST -> "Date (Newest)"
                                            SessionSort.DATE_OLDEST -> "Date (Oldest)"
                                            SessionSort.DURATION_LONGEST -> "Duration (Longest)"
                                            SessionSort.DURATION_SHORTEST -> "Duration (Shortest)"
                                        },
                                        color = if (uiState.selectedSort == sortOption) accentColor else Color.White
                                    )
                                },
                                onClick = {
                                    viewModel.setSort(sortOption)
                                    showSortDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Sessions List
            if (uiState.filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No sessions found for the active filter.",
                        color = Color(0xFF8A8A8A),
                        fontSize = 14.sp
                    )
                }
            } else {
                val sortByDate = uiState.selectedSort == SessionSort.DATE_NEWEST || uiState.selectedSort == SessionSort.DATE_OLDEST
                if (sortByDate) {
                    // Group by date when sorted chronologically
                    val grouped = remember(uiState.filteredSessions) {
                        uiState.filteredSessions.groupBy { sessionWithTasks ->
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = sessionWithTasks.session.startedAt
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            cal.timeInMillis
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        grouped.forEach { (dateMillis, sessionsInDay) ->
                            item(key = "header_$dateMillis") {
                                DateGroupHeader(
                                    dateMillis = dateMillis,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            items(
                                items = sessionsInDay,
                                key = { it.session.id }
                            ) { sessionWithTasks ->
                                RecentSessionCard(
                                    sessionWithTasks = sessionWithTasks,
                                    showDate = false
                                )
                            }
                        }
                    }
                } else {
                    // Flat list when sorted by duration
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            items = uiState.filteredSessions,
                            key = { it.session.id }
                        ) { sessionWithTasks ->
                            RecentSessionCard(
                                sessionWithTasks = sessionWithTasks,
                                showDate = true
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onDateRangeSelected = { start, end ->
                viewModel.setDateRange(start, end)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onDateRangeSelected: (Long?, Long?) -> Unit
) {
    val state = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onDateRangeSelected(state.selectedStartDateMillis, state.selectedEndDateMillis)
                    onDismiss()
                }
            ) {
                Text("Confirm", color = Color(0xFFF5A623))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        },
        colors = DatePickerDefaults.colors(
            containerColor = Color(0xFF1A1A1A)
        )
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = {
                Text(
                    text = "Select Date Range",
                    modifier = Modifier.padding(16.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            headline = {
                Text(
                    text = "Pick Range",
                    modifier = Modifier.padding(16.dp),
                    color = Color(0xFFF5A623)
                )
            },
            colors = DatePickerDefaults.colors(
                containerColor = Color(0xFF1A1A1A),
                titleContentColor = Color.White,
                headlineContentColor = Color(0xFFF5A623),
                selectedDayContainerColor = Color(0xFFF5A623),
                selectedDayContentColor = Color.Black,
                todayContentColor = Color(0xFFF5A623),
                todayDateBorderColor = Color(0xFFF5A623)
            )
        )
    }
}
