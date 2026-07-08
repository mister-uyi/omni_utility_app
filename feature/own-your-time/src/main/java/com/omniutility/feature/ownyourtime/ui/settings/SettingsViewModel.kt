package com.omniutility.feature.ownyourtime.ui.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniutility.feature.ownyourtime.data.db.entity.AppCategory
import com.omniutility.feature.ownyourtime.data.db.entity.AppConfigEntity
import com.omniutility.feature.ownyourtime.data.db.entity.UserConfigEntity
import com.omniutility.feature.ownyourtime.data.db.entity.UserInterestEntity
import com.omniutility.feature.ownyourtime.data.repository.OwnYourTimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InstalledApp(
    val packageName: String,
    val appLabel: String
)

data class SettingsState(
    val userConfig: UserConfigEntity = UserConfigEntity(),
    val productivityApps: List<AppConfigEntity> = emptyList(),
    val funApps: List<AppConfigEntity> = emptyList(),
    val systemApps: List<AppConfigEntity> = emptyList(),
    val pickerApps: List<InstalledApp> = emptyList(),
    val appPickerCategory: AppCategory? = null,
    val interests: List<UserInterestEntity> = emptyList(),
    val passiveBudgetEnabled: Boolean = false,
    val passiveBudgetPercent: Int = 20,
    val passiveBudgetPeriodMinutes: Int = 60
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: OwnYourTimeRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _allInstalledApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _appPickerCategory = MutableStateFlow<AppCategory?>(null)
    private val _pickerAppsSnapshot = MutableStateFlow<List<InstalledApp>?>(null)
    private val _passiveBudgetEnabled = MutableStateFlow(false)
    private val _passiveBudgetPercent = MutableStateFlow(20)
    private val _passiveBudgetPeriodMinutes = MutableStateFlow(60)

    val state: StateFlow<SettingsState> = combine(
        repository.observeUserConfig(),
        repository.observeAppConfigs(),
        repository.observeInterests(),
        _allInstalledApps,
        _appPickerCategory,
    ) { userConfig, appConfigs, interests, installedApps, appPickerCategory ->
        val config = userConfig ?: UserConfigEntity()
        val prodApps = appConfigs.filter { it.category == AppCategory.PRODUCTIVITY }
        val funApps = appConfigs.filter { it.category == AppCategory.FUN }
        val systemApps = appConfigs.filter { it.category == AppCategory.SYSTEM }

        val assignedPackages = appConfigs.map { it.packageName }.toSet()
        val unassignedApps = installedApps.filter { it.packageName !in assignedPackages }

        SettingsState(
            userConfig = config,
            productivityApps = prodApps,
            funApps = funApps,
            systemApps = systemApps,
            pickerApps = _pickerAppsSnapshot.value ?: unassignedApps,
            appPickerCategory = appPickerCategory,
            interests = interests,
            passiveBudgetEnabled = _passiveBudgetEnabled.value,
            passiveBudgetPercent = _passiveBudgetPercent.value,
            passiveBudgetPeriodMinutes = _passiveBudgetPeriodMinutes.value
        )
    }.combine(
        combine(_passiveBudgetEnabled, _passiveBudgetPercent, _passiveBudgetPeriodMinutes) { enabled, percent, period ->
            Triple(enabled, percent, period)
        }
    ) { settingsState, (enabled, percent, period) ->
        settingsState.copy(
            passiveBudgetEnabled = enabled,
            passiveBudgetPercent = percent,
            passiveBudgetPeriodMinutes = period
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    init {
        loadInstalledApps()
        loadPassiveBudget()
    }

    private fun loadPassiveBudget() {
        viewModelScope.launch {
            val budget = repository.getPassiveBudget()
            _passiveBudgetEnabled.value = budget.enabled
            _passiveBudgetPercent.value = budget.budgetPercent
            _passiveBudgetPeriodMinutes.value = budget.periodMinutes
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                packages.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || pm.getLaunchIntentForPackage(it.packageName) != null }
                    .filter { it.packageName != context.packageName }
                    .map { 
                        InstalledApp(
                            packageName = it.packageName,
                            appLabel = pm.getApplicationLabel(it).toString()
                        )
                    }
                    .sortedBy { it.appLabel.lowercase() }
            }
            _allInstalledApps.value = apps
        }
    }

    fun updateUserName(name: String) {
        viewModelScope.launch {
            val config = repository.getUserConfig().copy(userName = name)
            repository.saveUserConfig(config)
        }
    }

    fun updateDefaultDuration(ms: Long) {
        viewModelScope.launch {
            val config = repository.getUserConfig().copy(defaultDurationMs = ms)
            repository.saveUserConfig(config)
        }
    }

    fun updateDefaultFunBudgetPercent(percent: Int) {
        viewModelScope.launch {
            val config = repository.getUserConfig().copy(defaultFunBudgetPercent = percent)
            repository.saveUserConfig(config)
        }
    }

    fun showAppPicker(category: AppCategory) {
        val assignedPackages = state.value.productivityApps.map { it.packageName } +
                               state.value.funApps.map { it.packageName } +
                               state.value.systemApps.map { it.packageName }
        _pickerAppsSnapshot.value = _allInstalledApps.value.filter { it.packageName !in assignedPackages }
        _appPickerCategory.update { category }
    }

    fun hideAppPicker() {
        _pickerAppsSnapshot.value = null
        _appPickerCategory.update { null }
    }

    fun addAppToCategory(packageName: String, appLabel: String, category: AppCategory) {
        viewModelScope.launch {
            val appConfig = AppConfigEntity(packageName, appLabel, category)
            repository.saveAppConfig(appConfig)
        }
    }

    fun changeAppCategory(packageName: String, appLabel: String, category: AppCategory) {
        viewModelScope.launch {
            val appConfig = AppConfigEntity(packageName, appLabel, category)
            repository.saveAppConfig(appConfig)
        }
    }

    fun removeAppConfig(packageName: String) {
        viewModelScope.launch {
            repository.removeAppConfig(packageName)
        }
    }

    // --- Passive Budget ---

    fun updatePassiveBudgetEnabled(enabled: Boolean) {
        _passiveBudgetEnabled.value = enabled
        persistPassiveBudget()
    }

    fun updatePassiveBudgetPercent(percent: Int) {
        _passiveBudgetPercent.value = percent.coerceIn(0, 50)
        persistPassiveBudget()
    }

    fun updatePassiveBudgetPeriod(minutes: Int) {
        _passiveBudgetPeriodMinutes.value = minutes
        persistPassiveBudget()
    }

    private fun persistPassiveBudget() {
        viewModelScope.launch {
            val current = repository.getPassiveBudget()
            repository.savePassiveBudget(
                current.copy(
                    enabled = _passiveBudgetEnabled.value,
                    budgetPercent = _passiveBudgetPercent.value,
                    periodMinutes = _passiveBudgetPeriodMinutes.value
                )
            )
        }
    }

    // --- Topics of Interest ---

    fun addInterest(topic: String) {
        val trimmed = topic.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val interest = UserInterestEntity(topic = trimmed)
            repository.saveInterest(interest)
        }
    }

    fun removeInterest(id: String) {
        viewModelScope.launch {
            repository.deleteInterest(id)
        }
    }
}
