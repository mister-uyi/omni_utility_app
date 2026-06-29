package com.omniutility.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent { 
            MainScreen(onItemClick = {}) 
        }
    }

    @Test
    fun softPowerButtonCard_exists() {
        composeTestRule.onNodeWithText("Soft Power Button").assertExists()
        composeTestRule.onNodeWithText("Render a persistent floating lock button preserving biometrics").assertExists()
    }
}
