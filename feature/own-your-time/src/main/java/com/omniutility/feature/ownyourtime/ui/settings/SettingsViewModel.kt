package com.omniutility.feature.ownyourtime.ui.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniutility.feature.ownyourtime.data.db.entity.AppCategory
import com.omniutility.feature.ownyourtime.data.db.entity.AppConfigEntity
import com.omniutility.feature.ownyourtime.data.db.entity.UserConfigEntity
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
    val unassignedInstalledApps: List<InstalledApp> = emptyList(),
    val appPickerCategory: AppCategory? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: OwnYourTimeRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _allInstalledApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _appPickerCategory = MutableStateFlow<AppCategory?>(null)

    val state: StateFlow<SettingsState> = combine(
        repository.observeUserConfig(),
        repository.observeAppConfigs(),
        _allInstalledApps,
        _appPickerCategory
    ) { userConfig, appConfigs, installedApps, appPickerCategory ->
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
            unassignedInstalledApps = unassignedApps,
            appPickerCategory = appPickerCategory
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    init {
        loadInstalledApps()
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
        _appPickerCategory.update { category }
    }

    fun hideAppPicker() {
        _appPickerCategory.update { null }
    }

    fun addAppToCategory(packageName: String, appLabel: String, category: AppCategory) {
        viewModelScope.launch {
            val appConfig = AppConfigEntity(packageName, appLabel, category)
            repository.saveAppConfig(appConfig)
            hideAppPicker()
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
}
