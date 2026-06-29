package com.omniutility.feature.softpower.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.omniutility.feature.softpower.data.SoftPowerPreferences
import com.omniutility.feature.softpower.data.SoftPowerSettingsRepository
import com.omniutility.feature.softpower.ui.FloatingWindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * AccessibilityService that hosts the floating soft-power overlay.
 *
 * Implements the full trio of owners that Compose requires on the ViewTree:
 * [LifecycleOwner], [SavedStateRegistryOwner], and [ViewModelStoreOwner].
 *
 * The [SavedStateRegistryController] attach+restore must happen in init (before any
 * lifecycle event) because the Jetpack Recreator observer enforces that the component
 * is in the INITIALIZED state when attach runs. By doing it in init{} we guarantee
 * the LifecycleRegistry hasn't moved past INITIALIZED yet.
 */
class SoftPowerAccessibilityService :
    AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManagerInstance: FloatingWindowManager? = null
    private lateinit var settingsRepository: SoftPowerSettingsRepository
    private lateinit var stateFlow: StateFlow<SoftPowerPreferences>

    // LifecycleOwner
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    // SavedStateRegistryOwner — attach in init while lifecycle is still INITIALIZED
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    init {
        // Must happen at INITIALIZED — before onCreate moves us to CREATED.
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
    }

    // ViewModelStoreOwner
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        settingsRepository = SoftPowerSettingsRepository(applicationContext)
        stateFlow = settingsRepository.preferencesFlow.stateIn(
            scope = serviceScope,
            started = SharingStarted.Eagerly,
            initialValue = SoftPowerPreferences()
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        // React to the user's enabled toggle — show or hide the overlay accordingly.
        serviceScope.launch {
            stateFlow
                .map { it.isServiceEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) showOverlay() else hideOverlay()
                }
        }
    }

    private fun showOverlay() {
        if (windowManagerInstance != null) return
        windowManagerInstance = FloatingWindowManager(this, stateFlow) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }.apply { show() }
    }

    private fun hideOverlay() {
        windowManagerInstance?.dismiss()
        windowManagerInstance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-Op: event aggregation not needed for a persistent overlay
    }

    override fun onInterrupt() {
        // No-Op: required by AccessibilityService contract
    }

    override fun onDestroy() {
        hideOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        store.clear()
        super.onDestroy()
    }
}
