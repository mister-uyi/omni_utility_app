package com.omniutility.feature.ownyourtime.ui.sessionsummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniutility.feature.ownyourtime.data.db.entity.TaskType
import com.omniutility.feature.ownyourtime.data.repository.OwnYourTimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class SummaryTaskUI(
    val id: String,
    val title: String,
    val type: TaskType,
    val completed: Boolean
)

data class SessionSummaryState(
    val sessionId: String = "",
    val totalTimeMs: Long = 0L,
    val actualDurationMs: Long = 0L,
    val funBudgetUsedMs: Long = 0L,
    val tasks: List<SummaryTaskUI> = emptyList()
)

@HiltViewModel
class SessionSummaryViewModel @Inject constructor(
    private val repository: OwnYourTimeRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SessionSummaryState())
    val state: StateFlow<SessionSummaryState> = _state.asStateFlow()

    private var currentSessionId: String? = null

    fun loadSession(sessionId: String) {
        currentSessionId = sessionId
        viewModelScope.launch {
            repository.observeSession(sessionId).onEach { session ->
                if (session == null) return@onEach
                _state.update {
                    it.copy(
                        sessionId = session.id,
                        totalTimeMs = session.plannedDurationMs,
                        actualDurationMs = session.actualDurationMs,
                        funBudgetUsedMs = session.funTimeUsedMs
                    )
                }
            }.launchIn(viewModelScope)

            repository.observeSessionTasks(sessionId).onEach { tasks ->
                val uiTasks = tasks.map { sessionTask ->
                    val json = JSONObject(sessionTask.taskSnapshot)
                    val typeStr = json.optString("type", TaskType.TEXT.name)
                    val type = try { TaskType.valueOf(typeStr) } catch (e: Exception) { TaskType.TEXT }
                    SummaryTaskUI(
                        id = sessionTask.taskId,
                        title = json.optString("title", "Unknown Task"),
                        type = type,
                        completed = sessionTask.completed
                    )
                }
                _state.update { it.copy(tasks = uiTasks) }
            }.launchIn(viewModelScope)
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
}
