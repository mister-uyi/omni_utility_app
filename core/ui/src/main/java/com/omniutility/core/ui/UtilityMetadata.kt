package com.omniutility.core.ui

import androidx.compose.ui.graphics.vector.ImageVector

data class UtilityMetadata(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
    val isLocked: Boolean = false,
    val lockMessage: String? = null
)
