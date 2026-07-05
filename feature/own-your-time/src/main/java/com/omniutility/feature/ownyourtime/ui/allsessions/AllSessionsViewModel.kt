package com.omniutility.feature.ownyourtime.ui.allsessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniutility.feature.ownyourtime.data.db.entity.SessionEntity
import com.omniutility.feature.ownyourtime.data.db.entity.SessionTaskEntity
import com.omniutility.feature.ownyourtime.data.repository.OwnYourTimeRepository
import com.omniutility.feature.ownyourtime.ui.dashboard.SessionWithTasks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

enum class SessionSort {
    DATE_NEWEST,
    DATE_OLDEST,
    DURATION_LONGEST,
    DURATION_SHORTEST
}

data class AllSessionsUiState(
    val filteredSessions: List<SessionWithTasks> = emptyList(),
    val selectedSort: SessionSort = SessionSort.DATE_NEWEST,
    val selectedStartDate: Long? = null,
    val selectedEndDate: Long? = null,
    val dateRangeText: String = "All Time"
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AllSessionsViewModel @Inject constructor(
    private val repository: OwnYourTimeRepository
) : ViewModel() {

    private val _sort = MutableStateFlow(SessionSort.DATE_NEWEST)
    private val _dateRange = MutableStateFlow<Pair<Long?, Long?>>(null to null)

    val uiState: StateFlow<AllSessionsUiState> = combine(
        repository.observeAllSessions().flatMapLatest { sessions ->
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
        },
        _sort,
        _dateRange
    ) { allSessions, sort, dateRange ->
        val (start, end) = dateRange

        // Apply calendar date filter
        val filtered = allSessions.filter { sessionWithTasks ->
            val sessionTime = sessionWithTasks.session.startedAt
            val passStart = start == null || sessionTime >= getStartOfDay(start)
            val passEnd = end == null || sessionTime <= getEndOfDay(end)
            passStart && passEnd
        }

        // Apply sort
        val sorted = when (sort) {
            SessionSort.DATE_NEWEST -> filtered.sortedByDescending { it.session.startedAt }
            SessionSort.DATE_OLDEST -> filtered.sortedBy { it.session.startedAt }
            SessionSort.DURATION_LONGEST -> filtered.sortedByDescending {
                val s = it.session
                if (s.actualDurationMs > 0) s.actualDurationMs else s.plannedDurationMs
            }
            SessionSort.DURATION_SHORTEST -> filtered.sortedBy {
                val s = it.session
                if (s.actualDurationMs > 0) s.actualDurationMs else s.plannedDurationMs
            }
        }

        val dateRangeText = buildDateRangeText(start, end)

        AllSessionsUiState(
            filteredSessions = sorted,
            selectedSort = sort,
            selectedStartDate = start,
            selectedEndDate = end,
            dateRangeText = dateRangeText
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AllSessionsUiState()
    )

    fun setSort(sort: SessionSort) {
        _sort.value = sort
    }

    fun setDateRange(start: Long?, end: Long?) {
        _dateRange.value = start to end
    }

    fun clearDateRange() {
        _dateRange.value = null to null
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun getEndOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    private fun buildDateRangeText(start: Long?, end: Long?): String {
        if (start == null && end == null) return "All Time"
        val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return when {
            start != null && end != null -> {
                if (getStartOfDay(start) == getStartOfDay(end)) {
                    df.format(start)
                } else {
                    "${df.format(start)} - ${df.format(end)}"
                }
            }
            start != null -> "From ${df.format(start)}"
            end != null -> "Until ${df.format(end)}"
            else -> "All Time"
        }
    }
}
