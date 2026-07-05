package com.omniutility.ui.main

import com.omniutility.feature.finance.platform.AICoreManager
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
    
    private class FakeAICoreManager : AICoreManager(context = null, shouldInit = false)

    @Test
    fun uiState_exposesSoftPowerUtility() = runTest {
        val viewModel = MainScreenViewModel(FakeAICoreManager())
        val items = viewModel.uiState.first { it.isNotEmpty() }
        assertEquals(3, items.size)
        
        assertEquals("soft_power", items[0].id)
        assertEquals("Soft Power Button", items[0].title)
        
        assertEquals("finance", items[1].id)
        assertEquals("Offline AI Finance Manager", items[1].title)
        
        assertEquals("own_your_time", items[2].id)
        assertEquals("Own Your Time", items[2].title)
    }
}
