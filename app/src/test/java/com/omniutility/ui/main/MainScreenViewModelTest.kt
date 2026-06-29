package com.omniutility.ui.main

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
    @Test
    fun uiState_exposesSoftPowerUtility() = runTest {
        val viewModel = MainScreenViewModel()
        val items = viewModel.uiState.first()
        assertEquals(1, items.size)
        assertEquals("soft_power", items[0].id)
        assertEquals("Soft Power Button", items[0].title)
    }
}
