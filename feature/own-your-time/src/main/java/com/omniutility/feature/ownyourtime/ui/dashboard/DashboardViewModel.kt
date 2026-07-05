package com.omniutility.feature.ownyourtime.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniutility.feature.ownyourtime.data.db.entity.AppCategory
import com.omniutility.feature.ownyourtime.data.db.entity.SessionEntity
import com.omniutility.feature.ownyourtime.data.db.entity.SessionTaskEntity
import com.omniutility.feature.ownyourtime.data.repository.OwnYourTimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardUiState(
    val userName: String = "",
    val currentMonthSessions: Int = 0,
    val currentMonthTasks: Int = 0,
    val currentMonthDurationMs: Long = 0L,
    val monthDeltaSessions: Int = 0,
    val defaultDurationMs: Long = 3600000L,
    val defaultFunBudgetPercent: Int = 10,
    val prodAppCount: Int = 0,
    val funAppCount: Int = 0,
    val sysAppCount: Int = 0,
    val selectedMonthOffset: Int = 0,
    val recentSessions: List<SessionWithTasks> = emptyList()
)

data class SessionWithTasks(
    val session: SessionEntity,
    val tasks: List<SessionTaskEntity>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: OwnYourTimeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    fun selectMonthOffset(offset: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedMonthOffset = offset) }
        }
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            repository.observeUserConfig().collect { config ->
                if (config != null) {
                    _uiState.update { 
                        it.copy(
                            userName = config.userName,
                            defaultDurationMs = config.defaultDurationMs,
                            defaultFunBudgetPercent = config.defaultFunBudgetPercent
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.observeAppConfigs().collect { apps ->
                _uiState.update { 
                    it.copy(
                        prodAppCount = apps.count { app -> app.category == AppCategory.PRODUCTIVITY },
                        funAppCount = apps.count { app -> app.category == AppCategory.FUN },
                        sysAppCount = apps.count { app -> app.category == AppCategory.SYSTEM }
                    )
                }
            }
        }

        viewModelScope.launch {
            _uiState.map { it.selectedMonthOffset }.distinctUntilChanged().collectLatest { offset ->
                val (curStart, curEnd) = getMonthRange(offset)
                val (prevStart, prevEnd) = getMonthRange(offset - 1)

                combine(
                    repository.observeSessionCountInRange(curStart, curEnd),
                    repository.observeCompletedTaskCountInRange(curStart, curEnd),
                    repository.observeTotalDurationMsInRange(curStart, curEnd),
                    repository.observeSessionCountInRange(prevStart, prevEnd)
                ) { curSessions, curTasks, curDuration, prevSessions ->
                    val delta = curSessions - prevSessions
                    _uiState.update {
                        it.copy(
                            currentMonthSessions = curSessions,
                            currentMonthTasks = curTasks,
                            currentMonthDurationMs = curDuration,
                            monthDeltaSessions = delta
                        )
                    }
                }.collect()
            }
        }

        viewModelScope.launch {
            repository.observeRecentSessions(20).collect { sessions ->
                val now = System.currentTimeMillis()
                sessions.forEach { session ->
                    if (session.endedAt == null && session.startedAt + session.plannedDurationMs < now) {
                        repository.saveSession(session.copy(endedAt = session.startedAt + session.plannedDurationMs))
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.observeRecentSessions(20).flatMapLatest { sessions ->
                if (sessions.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val taskFlows = sessions.map { session ->
                        repository.observeSessionTasks(session.id).map { tasks ->
                            SessionWithTasks(session, tasks)
                        }
                    }
                    combine(taskFlows) { it.toList() }
                }
            }.collect { recent ->
                _uiState.update { it.copy(recentSessions = recent) }
            }
        }
    }

    private fun getMonthRange(offset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, offset)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.MILLISECOND, -1)
        val end = cal.timeInMillis
        return start to end
    }
}
