package com.omniutility.core.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class HUDPillType {
    INFO, SUCCESS, WARNING, ERROR
}

data class HUDPillMessage(
    val message: String,
    val type: HUDPillType = HUDPillType.INFO
)

@Composable
fun HUDPill(
    message: String,
    type: HUDPillType = HUDPillType.INFO,
    durationMs: Long = 2500L,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        visible = true
        delay(durationMs)
        visible = false
        delay(300) // allow exit animation to complete
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .statusBarsPadding()
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            border = BorderStroke(
                width = 1.dp,
                color = when (type) {
                    HUDPillType.SUCCESS -> Color(0xFF4CAF50)
                    HUDPillType.WARNING -> Color(0xFFF5A623)
                    HUDPillType.ERROR -> Color(0xFFF44336)
                    HUDPillType.INFO -> Color(0xFFF5A623)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (type) {
                    HUDPillType.SUCCESS -> Icons.Default.Check
                    HUDPillType.WARNING -> Icons.Default.Warning
                    HUDPillType.ERROR -> Icons.Default.Warning
                    HUDPillType.INFO -> Icons.Default.Info
                }
                val iconColor = when (type) {
                    HUDPillType.SUCCESS -> Color(0xFF4CAF50)
                    HUDPillType.WARNING -> Color(0xFFF5A623)
                    HUDPillType.ERROR -> Color(0xFFF44336)
                    HUDPillType.INFO -> Color(0xFFF5A623)
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
