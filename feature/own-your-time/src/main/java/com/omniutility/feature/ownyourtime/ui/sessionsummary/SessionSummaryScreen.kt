package com.omniutility.feature.ownyourtime.ui.sessionsummary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SessionSummaryScreen(
    sessionId: String,
    onDone: () -> Unit,
    viewModel: SessionSummaryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Session Summary",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Stats card
            val completedTasks = state.tasks.count { it.completed }
            val totalTasks = state.tasks.size
            val actualMins = maxOf(0L, state.actualDurationMs / 60000)
            val funMins = maxOf(0L, state.funBudgetUsedMs / 60000)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFF5A623), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Time: ${actualMins}m", color = Color.White, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tasks: $completedTasks / $totalTasks completed", color = Color(0xFFF5A623), fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Fun Budget Used: ${funMins}m", color = Color.White, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Tasks",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "You can tap any task to update its completion status.",
                color = Color(0xFF8A8A8A),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(state.tasks) { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleTaskCompletion(task.id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = task.completed,
                            onCheckedChange = { viewModel.toggleTaskCompletion(task.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFFF5A623),
                                uncheckedColor = Color(0xFF8A8A8A)
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = task.title,
                            color = if (task.completed) Color(0xFF8A8A8A) else Color.White,
                            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }
                }
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5A623)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Done", color = Color(0xFF0D0D0D), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}
