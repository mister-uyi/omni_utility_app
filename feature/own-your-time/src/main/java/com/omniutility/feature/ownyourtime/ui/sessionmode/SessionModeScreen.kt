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
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    DisposableEffect(Unit) {
        onDispose {
            val intent = Intent(context, com.omniutility.feature.ownyourtime.service.SessionService::class.java)
            context.stopService(intent)
        }
    }
    
    var showTimeUpDialog by remember { mutableStateOf(false) }
    var showExtendPicker by remember { mutableStateOf(false) }
    var showManualEndConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.remainingTimeMs, state.totalTimeMs) {
        if (state.totalTimeMs > 0 && state.remainingTimeMs <= 0L && !showExtendPicker) {
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
            // Timer
            val remainingSecs = maxOf(0L, state.remainingTimeMs / 1000)
            val h = remainingSecs / 3600
            val m = (remainingSecs % 3600) / 60
            val s = remainingSecs % 60
            val timeString = String.format("%02d:%02d:%02d", h, m, s)
            
            Text(
                text = timeString,
                color = Color.White,
                fontSize = 48.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = "remaining",
                color = Color(0xFF8A8A8A),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            val progress = if (state.totalTimeMs > 0) 1f - (state.remainingTimeMs.toFloat() / state.totalTimeMs.toFloat()) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF5A623),
                trackColor = Color(0xFF2A2A2A)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            val funRemainingSecs = maxOf(0L, state.funBudgetRemainingMs / 1000)
            val fm = funRemainingSecs / 60
            val fs = funRemainingSecs % 60
            val funProgress = if (state.funBudgetTotalMs > 0) state.funBudgetRemainingMs.toFloat() / state.funBudgetTotalMs.toFloat() else 0f
            Text(
                text = "Fun budget: ${fm}m ${fs}s left",
                color = Color(0xFFF5A623),
                fontSize = 14.sp
            )
            LinearProgressIndicator(
                progress = { funProgress },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = Color(0xFFF5A623),
                trackColor = Color(0xFF2A2A2A)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Text("Tasks", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(state.tasks) { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleTaskCompletion(task.id) }
                            .padding(vertical = 8.dp),
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.title, color = if (task.completed) Color(0xFF8A8A8A) else Color.White)
                            if (!task.url.isNullOrBlank()) {
                                Text(
                                    text = task.url,
                                    color = Color(0xFF8A8A8A),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { activeUrl = task.url }
                                )
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Productivity", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                item {
                    AppRow(apps = state.productivityApps)
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("System", color = Color(0xFF8A8A8A), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                item {
                    AppRow(apps = state.systemApps)
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Fun", color = Color(0xFFF5A623), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                item {
                    AppRow(apps = state.funApps, disabled = state.funBudgetRemainingMs <= 0)
                }
            }
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
                    Text(
                        text = "$completed of $total tasks completed",
                        color = Color(0xFFF5A623),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
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
fun AppRow(apps: List<AppUI>, disabled: Boolean = false) {
    val context = LocalContext.current
    val pm = context.packageManager
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(apps) { app ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clickable {
                        if (disabled) {
                            Toast.makeText(context, "Fun budget used. Back to work.", Toast.LENGTH_SHORT).show()
                        } else {
                            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        }
                    }
            ) {
                com.omniutility.feature.ownyourtime.ui.settings.AppIcon(
                    packageName = app.packageName,
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (disabled) Color(0xFF2A2A2A) else Color.Transparent, shape = MaterialTheme.shapes.medium)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = app.label, 
                    color = if (disabled) Color(0xFF8A8A8A) else Color.White, 
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
