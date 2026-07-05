package com.omniutility.feature.ownyourtime.ui.sessionsetup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSetupSheet(
    onDismiss: () -> Unit,
    onSessionStarted: (String) -> Unit,
    viewModel: SessionSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.reset()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            when (state.currentStep) {
                1 -> Step1Tasks(state, viewModel)
                2 -> Step2Duration(state, viewModel)
                3 -> Step3Confirm(state, viewModel, onSessionStarted, onDismiss)
            }
        }
    }
}

@Composable
fun Step1Tasks(state: SessionSetupState, viewModel: SessionSetupViewModel) {
    Column {
        Text("Step 1: Select Tasks", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${state.selectedTaskIds.size} selected", color = Color(0xFF8A8A8A))
            TextButton(onClick = { viewModel.selectAllTasks(state.selectedTaskIds.size != state.allTasks.size) }) {
                Text(if (state.selectedTaskIds.size == state.allTasks.size) "Deselect All" else "Select All", color = Color(0xFFF5A623))
            }
        }
        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
            items(state.allTasks) { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleTaskSelection(task.id) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.selectedTaskIds.contains(task.id),
                        onCheckedChange = { viewModel.toggleTaskSelection(task.id) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFF5A623),
                            uncheckedColor = Color(0xFF8A8A8A)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(task.title, color = Color.White)
                    if (task.type.name != "TEXT") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("(Link)", color = Color(0xFF8A8A8A), fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.nextStep() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5A623))
        ) {
            Text("Next: Duration", color = Color.Black)
        }
    }
}

@Composable
fun Step2Duration(state: SessionSetupState, viewModel: SessionSetupViewModel) {
    Column {
        Text("Step 2: Duration & Fun Budget", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Duration", color = Color(0xFF8A8A8A))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), 
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(1 * 60_000L to "1m", 2 * 60_000L to "2m", 5 * 60_000L to "5m", 10 * 60_000L to "10m", 30 * 60_000L to "30m", 60 * 60_000L to "1h", 120 * 60_000L to "2h", 240 * 60_000L to "4h")
            presets.forEach { (ms, label) ->
                FilterChip(
                    selected = state.durationMs == ms,
                    onClick = { viewModel.setDuration(ms) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF5A623),
                        selectedLabelColor = Color.Black,
                        labelColor = Color.White
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Fun App Budget: ${state.funBudgetPercent}%", color = Color(0xFF8A8A8A))
        Slider(
            value = state.funBudgetPercent.toFloat(),
            onValueChange = { viewModel.setFunBudgetPercent(it.toInt()) },
            valueRange = 0f..15f,
            steps = 14,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFF5A623),
                activeTrackColor = Color(0xFFF5A623),
                inactiveTrackColor = Color(0xFF2A2A2A)
            )
        )
        val funMinutes = (state.durationMs / 60_000) * state.funBudgetPercent / 100
        Text("${funMinutes} minutes for fun apps", color = Color(0xFFF5A623), fontSize = 14.sp)
        
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.previousStep() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Back", color = Color.White)
            }
            Button(
                onClick = { viewModel.nextStep() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5A623))
            ) {
                Text("Next: Confirm", color = Color.Black)
            }
        }
    }
}

@Composable
fun Step3Confirm(
    state: SessionSetupState, 
    viewModel: SessionSetupViewModel,
    onSessionStarted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column {
        Text("Step 3: Confirm", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        val durationMins = state.durationMs / 60_000
        val funMinutes = durationMins * state.funBudgetPercent / 100
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("${durationMins}m session", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${state.selectedTaskIds.size} tasks selected", color = Color(0xFF8A8A8A))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Apps Configuration:", color = Color.White)
                Text("${state.productivityAppsCount} Productivity Apps", color = Color(0xFF8A8A8A))
                Text("${state.systemAppsCount} System Apps (Always Available)", color = Color(0xFF8A8A8A))
                Text("${state.funAppsCount} Fun Apps (${funMinutes}m cap)", color = Color(0xFFF5A623))
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.previousStep() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Back", color = Color.White)
            }
            Button(
                onClick = {
                    val appOps = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
                    } else {
                        @Suppress("DEPRECATION")
                        appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
                    }
                    val hasUsageStats = mode == android.app.AppOpsManager.MODE_ALLOWED

                    if (!hasUsageStats) {
                        android.widget.Toast.makeText(context, "Please grant Usage Access to block apps", android.widget.Toast.LENGTH_LONG).show()
                        val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } else if (!android.provider.Settings.canDrawOverlays(context)) {
                        android.widget.Toast.makeText(context, "Please grant Display Over Other Apps permission to allow background blocks", android.widget.Toast.LENGTH_LONG).show()
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        ).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } else {
                        viewModel.commitSession { sessionId ->
                            onSessionStarted(sessionId)
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5A623))
            ) {
                Text("Commit & Start", color = Color.Black)
            }
        }
    }
}
