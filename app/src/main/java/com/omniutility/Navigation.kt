package com.omniutility

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.omniutility.feature.softpower.SoftPowerSettingsScreen
import com.omniutility.ui.main.MainScreen
import com.omniutility.feature.finance.ui.FinanceDashboardScreen
import com.omniutility.feature.finance.ui.FinanceDashboardViewModel
import com.omniutility.feature.ownyourtime.ui.OwnYourTimeScreen
import androidx.activity.ComponentActivity
import androidx.core.util.Consumer
import android.content.Intent

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)
  val activity = LocalContext.current as? ComponentActivity

  androidx.compose.runtime.DisposableEffect(activity) {
    val listener = Consumer<Intent> { newIntent ->
      if (newIntent.getBooleanExtra("EXTRA_SHOW_SESSION_SETUP", false)) {
        if (backStack.lastOrNull() != OwnYourTime) {
          backStack.add(OwnYourTime)
        }
      }
    }
    activity?.addOnNewIntentListener(listener)
    onDispose { activity?.removeOnNewIntentListener(listener) }
  }

  androidx.compose.runtime.LaunchedEffect(activity?.intent) {
    if (activity?.intent?.getBooleanExtra("EXTRA_SHOW_SESSION_SETUP", false) == true) {
      if (backStack.lastOrNull() != OwnYourTime) {
        backStack.add(OwnYourTime)
      }
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(onItemClick = { navKey -> backStack.add(navKey) }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<SoftPower> {
          val context = LocalContext.current
          val repository = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.omniutility.feature.softpower.SoftPowerEntryPoint::class.java
          ).softPowerSettingsRepository()
          SoftPowerSettingsScreen(
            repository = repository,
            onBackClick = { backStack.removeLastOrNull() }
          )
        }
        entry<Finance> {
          val viewModel: FinanceDashboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
          FinanceDashboardScreen(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() }
          )
        }
        entry<OwnYourTime> {
          OwnYourTimeScreen(onBack = { backStack.removeLastOrNull() })
        }
      },
  )
}
