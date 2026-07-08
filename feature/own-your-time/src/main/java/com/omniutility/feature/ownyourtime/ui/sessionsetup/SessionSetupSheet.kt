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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.omniutility.core.ui.HUDPill
import com.omniutility.core.ui.HUDPillMessage
import com.omniutility.core.ui.HUDPillType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSetupSheet(
    onDismiss: () -> Unit,
    onSessionStarted: (String) -> Unit,
    viewModel: SessionSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hudMessage by remember { mutableStateOf<HUDPillMessage?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
            Text(
                text = "Start Session",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Duration", color = Color(0xFF8A8A8A))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()), 
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    1 * 60_000L to "1m",
                    2 * 60_000L to "2m",
                    5 * 60_000L to "5m",
                    10 * 60_000L to "10m",
                    30 * 60_000L to "30m",
                    60 * 60_000L to "1h",
                    120 * 60_000L to "2h",
                    240 * 60_000L to "4h"
                )
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
            val funBudgetMs = state.durationMs * state.funBudgetPercent / 100
            val totalSeconds = funBudgetMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val budgetStr = when {
                minutes == 0L -> "$seconds seconds"
                seconds == 0L -> if (minutes == 1L) "1 minute" else "$minutes minutes"
                else -> if (minutes == 1L) "1 minute $seconds seconds" else "$minutes minutes $seconds seconds"
            }
            Text("$budgetStr for fun apps", color = Color(0xFFF5A623), fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(32.dp))
            
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
                        hudMessage = HUDPillMessage("Please grant Usage Access to block apps", HUDPillType.ERROR)
                        scope.launch {
                            delay(1200)
                            val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    } else if (!android.provider.Settings.canDrawOverlays(context)) {
                        hudMessage = HUDPillMessage("Please grant Display Over Other Apps permission to allow background blocks", HUDPillType.ERROR)
                        scope.launch {
                            delay(1200)
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            ).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    } else {
                        viewModel.commitSession { sessionId ->
                            onSessionStarted(sessionId)
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5A623))
            ) {
                Text("Start Session", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
        hudMessage?.let { msg ->
            HUDPill(
                message = msg.message,
                type = msg.type,
                onDismiss = { hudMessage = null }
            )
        }
    }
}
}
