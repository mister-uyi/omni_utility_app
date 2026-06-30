package com.omniutility.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniutility.core.ui.UtilityMetadata
import com.omniutility.feature.finance.platform.AICoreManager
import com.omniutility.feature.finance.platform.AICoreStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val aiCoreManager: AICoreManager
) : ViewModel() {
    
    val uiState: StateFlow<List<UtilityMetadata>> = aiCoreManager.status.map { status ->
        listOf(
            UtilityMetadata(
                id = "soft_power",
                title = "Soft Power Button",
                description = "Render a persistent floating lock button preserving biometrics",
                icon = Icons.Default.Build,
                route = "soft_power"
            ),
            UtilityMetadata(
                id = "finance",
                title = "Offline AI Finance Manager",
                description = "Privacy-first offline ledger powered by on-device Gemini Nano",
                icon = Icons.Default.Info,
                route = "finance",
                isLocked = status is AICoreStatus.Unsupported,
                lockMessage = if (status is AICoreStatus.Unsupported) {
                    "Requires Gemini Nano / AICore support on your device."
                } else null
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}
