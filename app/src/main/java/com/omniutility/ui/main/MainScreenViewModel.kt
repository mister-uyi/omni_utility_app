package com.omniutility.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.lifecycle.ViewModel
import com.omniutility.core.ui.UtilityMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainScreenViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<List<UtilityMetadata>>(
        listOf(
            UtilityMetadata(
                id = "soft_power",
                title = "Soft Power Button",
                description = "Render a persistent floating lock button preserving biometrics",
                icon = Icons.Default.Build,
                route = "soft_power"
            )
        )
    )
    val uiState: StateFlow<List<UtilityMetadata>> = _uiState
}
