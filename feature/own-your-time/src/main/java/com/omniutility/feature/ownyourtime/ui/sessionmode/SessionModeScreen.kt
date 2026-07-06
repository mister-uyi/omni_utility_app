package com.omniutility.feature.ownyourtime.ui.sessionmode

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.hilt.navigation.compose.hiltViewModel
import com.omniutility.feature.ownyourtime.data.db.entity.TaskType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionModeScreen(
    sessionId: String,
    onSessionEnded: () -> Unit,
    viewModel: SessionModeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    androidx.activity.compose.BackHandler { }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }
    
    // Start Service when duration is known
    LaunchedEffect(state.totalTimeMs) {
        if (state.totalTimeMs > 0) {
            val intent = Intent(context, com.omniutility.feature.ownyourtime.service.SessionService::class.java).apply {
                putExtra(com.omniutility.feature.ownyourtime.service.SessionService.EXTRA_DURATION_MS, state.totalTimeMs)
                putExtra(com.omniutility.feature.ownyourtime.service.SessionService.EXTRA_SESSION_ID, sessionId)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
    // Service lifecycle is managed explicitly on session end/finish rather than screen disposal

    
    var showTimeUpDialog by remember { mutableStateOf(false) }
    var showExtendPicker by remember { mutableStateOf(false) }
    var showManualEndConfirm by remember { mutableStateOf(false) }
    var showTasksBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.remainingTimeMs, state.totalTimeMs, state.sessionId, sessionId) {
        if (state.sessionId == sessionId && state.totalTimeMs > 0 && state.remainingTimeMs <= 0L && !showExtendPicker) {
            showTimeUpDialog = true
        }
    }

    
    var activeUrl by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
        ) {
            // Circular countdown timer
            val remainingSecs = maxOf(0L, state.remainingTimeMs / 1000)
            val h = remainingSecs / 3600
            val m = (remainingSecs % 3600) / 60
            val s = remainingSecs % 60
            val timeString = String.format("%02d:%02d:%02d", h, m, s)
            val progress = if (state.totalTimeMs > 0) state.remainingTimeMs.toFloat() / state.totalTimeMs.toFloat() else 0f
            val accentColor = Color(0xFFF5A623)
            val trackColor = Color(0xFF2A2A2A)

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.CenterHorizontally)
                    .drawBehind {
                        val strokeWidth = 18.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                        val arcSize = Size(diameter, diameter)
                        // Track
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                        // Progress
                        drawArc(
                            color = accentColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeString,
                        color = Color.White,
                        fontSize = 38.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "remaining",
                        color = Color(0xFF8A8A8A),
                        fontSize = 13.sp
                    )
                }
            }

            // Fun budget progress bar is now shown as a circular indicator directly around the fun app icons.

            
            Spacer(modifier = Modifier.weight(1f))

            // Tappable summary of open tasks
            val openTasks = state.tasks.filter { !it.completed }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clickable { showTasksBottomSheet = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (openTasks.isEmpty()) "No tasks remaining" else "${openTasks.size} Tasks Remaining",
                            fontWeight = FontWeight.Bold,
                            color = if (openTasks.isEmpty()) Color(0xFF8A8A8A) else Color(0xFFF5A623),
                            fontSize = 16.sp
                        )
                        if (openTasks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            openTasks.take(2).forEach { task ->
                                Text(
                                    text = "• ${task.title}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (openTasks.size > 2) {
                                Text(
                                    text = "... and ${openTasks.size - 2} more",
                                    color = Color(0xFF8A8A8A),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "View All Tasks",
                        tint = Color(0xFF8A8A8A),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Apps at the bottom — reachable by thumb, sorted alphabetically
            val combinedApps = (state.productivityApps + state.systemApps + state.funApps)
                .sortedBy { it.label.lowercase() }
            
            if (combinedApps.isNotEmpty()) {
                AppGrid(
                    apps = combinedApps,
                    funApps = state.funApps,
                    funBudgetRemainingMs = state.funBudgetRemainingMs,
                    funBudgetTotalMs = state.funBudgetTotalMs
                )
            }
            Spacer(modifier = Modifier.height(56.dp)) // space for End Session button
        }
        
        TextButton(
            onClick = {
                showManualEndConfirm = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("End Session", color = Color(0xFF8A8A8A))
        }

        if (showTasksBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTasksBottomSheet = false },
                containerColor = Color(0xFF1A1A1A)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = "Session Tasks",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val openTasks = state.tasks.filter { !it.completed }
                    val completedTasks = state.tasks.filter { it.completed }

                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (openTasks.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Open",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            items(openTasks) { task ->
                                SessionTaskItem(
                                    task = task,
                                    onToggleCompletion = { viewModel.toggleTaskCompletion(task.id) },
                                    onUrlClick = { activeUrl = task.url }
                                )
                            }
                        }
                        if (completedTasks.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Completed",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(completedTasks) { task ->
                                SessionTaskItem(
                                    task = task,
                                    onToggleCompletion = { viewModel.toggleTaskCompletion(task.id) },
                                    onUrlClick = { activeUrl = task.url }
                                )
                            }
                        }
                        if (openTasks.isEmpty() && completedTasks.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No tasks in this session.", color = Color(0xFF8A8A8A))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (activeUrl != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { activeUrl = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            loadUrl(activeUrl!!)
                        }
                    },
                    update = {
                        it.loadUrl(activeUrl!!)
                    }
                )
                
                IconButton(
                    onClick = { activeUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape)
                ) {
                    Text("X", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showManualEndConfirm) {
        AlertDialog(
            onDismissRequest = { showManualEndConfirm = false },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("End Session?", color = Color.White) },
            text = { Text("Are you sure you want to end this session early?", color = Color.White) },
            confirmButton = {
                Button(
                    onClick = {
                        showManualEndConfirm = false
                        viewModel.endSession()
                        val intent = Intent(context, com.omniutility.feature.ownyourtime.service.SessionService::class.java)
                        context.stopService(intent)
                        onSessionEnded()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5A623))
                ) {
                    Text("End Session", color = Color(0xFF0D0D0D))
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualEndConfirm = false }) {
                    Text("Cancel", color = Color(0xFFF5A623))
                }
            }
        )
    }

    if (showTimeUpDialog) {
        AlertDialog(
            onDismissRequest = { }, // Cannot be dismissed without action
            containerColor = Color(0xFF1A1A1A),
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF5A623)) },
            title = { Text("Time's Up.", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                val completed = state.tasks.count { it.completed }
                val total = state.tasks.size
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (total > 0) {
                        Text(
                            text = "$completed of $total tasks completed",
                            color = Color(0xFFF5A623),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Text("Continuing will not reset your fun app budget", color = Color(0xFF8A8A8A), fontSize = 12.sp)
                }
            },
            confirmButton = {},
            dismissButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showTimeUpDialog = false
                            viewModel.endSession()
                            val intent = Intent(context, com.omniutility.feature.ownyourtime.service.SessionService::class.java)
                            context.stopService(intent)
                            onSessionEnded()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5A623))
                    ) {
                        Text("End Session", color = Color(0xFF0D0D0D), fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            showTimeUpDialog = false
                            showExtendPicker = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF5A623))
                    ) {
                        Text("Continue Session")
                    }
                }
            }
        )
    }

    if (showExtendPicker) {
        var selectedMinutes by remember { mutableStateOf(30L) }
        AlertDialog(
            onDismissRequest = { showExtendPicker = false },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("Extend Session", color = Color.White) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "+ $selectedMinutes min",
                        color = Color(0xFFF5A623),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedMinutes == 5L,
                            onClick = { selectedMinutes = 5L },
                            label = { Text("+5m", color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF3A3A3A))
                        )
                        FilterChip(
                            selected = selectedMinutes == 10L,
                            onClick = { selectedMinutes = 10L },
                            label = { Text("+10m", color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF3A3A3A))
                        )
                        FilterChip(
                            selected = selectedMinutes == 15L,
                            onClick = { selectedMinutes = 15L },
                            label = { Text("+15m", color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF3A3A3A))
                        )
                        FilterChip(
                            selected = selectedMinutes == 30L,
                            onClick = { selectedMinutes = 30L },
                            label = { Text("+30m", color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF3A3A3A))
                        )
                        FilterChip(
                            selected = selectedMinutes == 60L,
                            onClick = { selectedMinutes = 60L },
                            label = { Text("+1h", color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF3A3A3A))
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFF5A623), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fun budget will NOT reset", color = Color(0xFFF5A623), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExtendPicker = false
                        viewModel.extendSession(selectedMinutes * 60 * 1000)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5A623))
                ) {
                    Text("Start Extension", color = Color(0xFF0D0D0D))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtendPicker = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun AppGrid(
    apps: List<AppUI>,
    funApps: List<AppUI>,
    funBudgetRemainingMs: Long,
    funBudgetTotalMs: Long
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val columns = 5
    val rows = (apps.size + columns - 1) / columns
    val itemHeight = 80.dp
    val gridHeight = itemHeight * rows

    Box(modifier = Modifier.fillMaxWidth().height(gridHeight)) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps) { app ->
                val isFunApp = funApps.any { it.packageName == app.packageName }
                val isFunDisabled = isFunApp && funBudgetRemainingMs <= 0
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            if (isFunDisabled) {
                                Toast.makeText(context, "Fun budget used. Back to work.", Toast.LENGTH_SHORT).show()
                            } else {
                                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                }
                            }
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(64.dp)
                    ) {
                        if (isFunApp && funBudgetTotalMs > 0) {
                            val funProgress = maxOf(0f, funBudgetRemainingMs.toFloat() / funBudgetTotalMs.toFloat())
                            CircularProgressIndicator(
                                progress = { funProgress },
                                modifier = Modifier.size(60.dp),
                                color = Color(0xFFF5A623),
                                strokeWidth = 3.dp,
                                trackColor = Color(0xFF2A2A2A)
                            )
                        }
                        
                        com.omniutility.feature.ownyourtime.ui.settings.AppIcon(
                            packageName = app.packageName,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(if (isFunDisabled) Color(0xFF2A2A2A) else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SessionTaskItem(
    task: SessionTaskUI,
    onToggleCompletion: () -> Unit,
    onUrlClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleCompletion() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.completed,
            onCheckedChange = { onToggleCompletion() },
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFFF5A623),
                uncheckedColor = Color(0xFF8A8A8A)
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title, 
                color = if (task.completed) Color(0xFF8A8A8A) else Color.White,
                textDecoration = if (task.completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
            )
            if (!task.url.isNullOrBlank()) {
                Text(
                    text = task.url,
                    color = Color(0xFFF5A623),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier
                        .clickable { onUrlClick() }
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}
