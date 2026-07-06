package com.omniutility.feature.ownyourtime.ui.sessionsetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniutility.feature.ownyourtime.data.db.entity.AppCategory
import com.omniutility.feature.ownyourtime.data.db.entity.SessionEntity
import com.omniutility.feature.ownyourtime.data.db.entity.SessionTaskEntity
import com.omniutility.feature.ownyourtime.data.db.entity.TaskEntity
import com.omniutility.feature.ownyourtime.data.repository.OwnYourTimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionSetupState(
    val allTasks: List<TaskEntity> = emptyList(),
    val durationMs: Long = 3_600_000L,
    val funBudgetPercent: Int = 10,
    val productivityAppsCount: Int = 0,
    val systemAppsCount: Int = 0,
    val funAppsCount: Int = 0
)

@HiltViewModel
class SessionSetupViewModel @Inject constructor(
    private val repository: OwnYourTimeRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SessionSetupState())
    val state: StateFlow<SessionSetupState> = _state.asStateFlow()

    private var completedTaskIds = emptySet<String>()

    init {
        viewModelScope.launch {
            val userConfig = repository.getUserConfig()
            _state.update { 
                it.copy(
                    durationMs = userConfig.defaultDurationMs,
                    funBudgetPercent = userConfig.defaultFunBudgetPercent
                )
            }
        }
        
        repository.observeTasks()
            .onEach { tasks -> _state.update { it.copy(allTasks = tasks.filter { !it.isArchived }) } }
            .launchIn(viewModelScope)

        repository.observeCompletedTaskIds()
            .onEach { ids -> completedTaskIds = ids.toSet() }
            .launchIn(viewModelScope)

        repository.observeAppConfigs().onEach { apps ->
            _state.update { 
                it.copy(
                    productivityAppsCount = apps.count { app -> app.category == AppCategory.PRODUCTIVITY },
                    systemAppsCount = apps.count { app -> app.category == AppCategory.SYSTEM },
                    funAppsCount = apps.count { app -> app.category == AppCategory.FUN }
                )
            }
        }.launchIn(viewModelScope)
    }

    fun setDuration(durationMs: Long) {
        _state.update { it.copy(durationMs = durationMs) }
    }

    fun setFunBudgetPercent(percent: Int) {
        _state.update { it.copy(funBudgetPercent = percent) }
    }
    
    fun commitSession(onSessionStarted: (String) -> Unit) {
        viewModelScope.launch {
            val currentState = _state.value
            val funBudgetMs = (currentState.durationMs * currentState.funBudgetPercent / 100)
            val session = SessionEntity(
                id = java.util.UUID.randomUUID().toString(),
                startedAt = System.currentTimeMillis(),
                plannedDurationMs = currentState.durationMs,
                actualDurationMs = 0L,
                funBudgetPercent = currentState.funBudgetPercent,
                funBudgetMs = funBudgetMs
            )
            repository.saveSession(session)
            
            // Auto-populate with all open tasks (not completed)
            val openTasks = currentState.allTasks.filter { it.id !in completedTaskIds }
            
            openTasks.forEach { task ->
                val snapshotJson = org.json.JSONObject().apply {
                    put("title", task.title)
                    put("type", task.type.name)
                    put("url", task.url ?: "")
                }.toString()
                val sessionTask = SessionTaskEntity(
                    sessionId = session.id,
                    taskId = task.id,
                    taskSnapshot = snapshotJson
                )
                repository.saveSessionTask(sessionTask)
            }
            onSessionStarted(session.id)
        }
    }
}
