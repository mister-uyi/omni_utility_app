package com.omniutility.feature.ownyourtime.ui.sessionmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniutility.feature.ownyourtime.data.db.entity.AppCategory
import com.omniutility.feature.ownyourtime.data.db.entity.SessionEntity
import com.omniutility.feature.ownyourtime.data.db.entity.SessionTaskEntity
import com.omniutility.feature.ownyourtime.data.db.entity.TaskType
import com.omniutility.feature.ownyourtime.data.repository.OwnYourTimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import org.json.JSONObject
import javax.inject.Inject
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable

data class SessionTaskUI(
    val id: String,
    val title: String,
    val type: TaskType,
    val completed: Boolean,
    val url: String? = null
)

data class AppUI(
    val packageName: String,
    val label: String
)

data class SessionModeState(
    val sessionId: String = "",
    val tasks: List<SessionTaskUI> = emptyList(),
    val productivityApps: List<AppUI> = emptyList(),
    val systemApps: List<AppUI> = emptyList(),
    val funApps: List<AppUI> = emptyList(),
    val startedAt: Long = 0L,
    val remainingTimeMs: Long = 0L,
    val totalTimeMs: Long = 0L,
    val funBudgetRemainingMs: Long = 0L,
    val funBudgetTotalMs: Long = 0L
)

@HiltViewModel
class SessionModeViewModel @Inject constructor(
    private val repository: OwnYourTimeRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SessionModeState())
    val state: StateFlow<SessionModeState> = _state.asStateFlow()
    
    private var currentSessionId: String? = null

    private var timerJob: kotlinx.coroutines.Job? = null
    private var sessionObserveJob: kotlinx.coroutines.Job? = null

    fun loadSession(sessionId: String) {
        currentSessionId = sessionId
        _state.value = SessionModeState(sessionId = sessionId)
        
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                _state.update {
                    val remaining = if (it.startedAt > 0) {
                        it.totalTimeMs - (System.currentTimeMillis() - it.startedAt)
                    } else 0L
                    it.copy(remainingTimeMs = maxOf(0L, remaining))
                }
                delay(1000)
            }
        }
        
        sessionObserveJob?.cancel()
        sessionObserveJob = viewModelScope.launch {
            repository.observeSession(sessionId).onEach { session ->
                if (session == null) return@onEach
                _state.update {
                    val remaining = maxOf(0L, session.plannedDurationMs - (System.currentTimeMillis() - session.startedAt))
                    it.copy(
                        sessionId = session.id,
                        startedAt = session.startedAt,
                        totalTimeMs = session.plannedDurationMs,
                        remainingTimeMs = remaining,
                        funBudgetTotalMs = session.funBudgetMs,
                        funBudgetRemainingMs = session.funBudgetMs - session.funTimeUsedMs
                    )
                }
            }.launchIn(this)
            
            // Load tasks
            repository.observeSessionTasks(sessionId).onEach { tasks ->
                val uiTasks = mutableListOf<SessionTaskUI>()
                for (sessionTask in tasks) {
                    val json = JSONObject(sessionTask.taskSnapshot)
                    val typeStr = json.optString("type", TaskType.TEXT.name)
                    val type = try { TaskType.valueOf(typeStr) } catch (e: Exception) { TaskType.TEXT }
                    
                    var url = json.optString("url", "").takeIf { it != "null" && it.isNotBlank() }
                    if (url == null) {
                        val dbTask = repository.getTask(sessionTask.taskId)
                        url = dbTask?.url
                    }
                    
                    uiTasks.add(
                        SessionTaskUI(
                            id = sessionTask.taskId,
                            title = json.optString("title", "Unknown Task"),
                            type = type,
                            completed = sessionTask.completed,
                            url = url
                        )
                    )
                }
                _state.update { it.copy(tasks = uiTasks) }
            }.launchIn(this)
            
            // Load apps
            repository.observeAppConfigs().onEach { appConfigs ->
                val prodApps = appConfigs.filter { it.category == AppCategory.PRODUCTIVITY }.map { AppUI(it.packageName, it.appLabel) }
                val sysApps = appConfigs.filter { it.category == AppCategory.SYSTEM }.map { AppUI(it.packageName, it.appLabel) }
                val funApps = appConfigs.filter { it.category == AppCategory.FUN }.map { AppUI(it.packageName, it.appLabel) }
                
                _state.update { it.copy(
                    productivityApps = prodApps,
                    systemApps = sysApps,
                    funApps = funApps
                ) }
            }.launchIn(this)
        }
    }

    fun toggleTaskCompletion(taskId: String) {
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch
            val sessionTasks = repository.getSessionTasks(sessionId)
            val sessionTask = sessionTasks.find { it.taskId == taskId } ?: return@launch
            val newCompleted = !sessionTask.completed
            repository.saveSessionTask(
                sessionTask.copy(
                    completed = newCompleted,
                    completedAt = if (newCompleted) System.currentTimeMillis() else null
                )
            )
        }
    }
    
    fun markTaskAsDone(taskId: String) {
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch
            val sessionTasks = repository.getSessionTasks(sessionId)
            val sessionTask = sessionTasks.find { it.taskId == taskId } ?: return@launch
            repository.saveSessionTask(
                sessionTask.copy(
                    completed = true,
                    completedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun extendSession(durationMs: Long) {
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch
            val session = repository.getSession(sessionId) ?: return@launch
            
            val currentExtensionsJson = org.json.JSONArray(session.extensions)
            currentExtensionsJson.put(durationMs)
            
            repository.saveSession(
                session.copy(
                    plannedDurationMs = session.plannedDurationMs + durationMs,
                    extensions = currentExtensionsJson.toString()
                )
            )
        }
    }

    fun endSession() {
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch
            val session = repository.getSession(sessionId) ?: return@launch
            val actualDurationMs = System.currentTimeMillis() - session.startedAt
            
            if (actualDurationMs < 60_000L) {
                repository.deleteSession(sessionId)
            } else {
                repository.saveSession(
                    session.copy(
                        endedAt = System.currentTimeMillis(),
                        actualDurationMs = actualDurationMs
                    )
                )
            }
        }
    }
}
