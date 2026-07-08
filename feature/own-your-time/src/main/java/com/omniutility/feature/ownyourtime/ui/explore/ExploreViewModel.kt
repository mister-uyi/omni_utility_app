package com.omniutility.feature.ownyourtime.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniutility.feature.ownyourtime.data.db.entity.TaskEntity
import com.omniutility.feature.ownyourtime.data.db.entity.TaskType
import com.omniutility.feature.ownyourtime.data.repository.OwnYourTimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: OwnYourTimeRepository
) : ViewModel() {

    val tasks: StateFlow<List<TaskEntity>> = repository.observeTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val completedTaskIds: StateFlow<Set<String>> = repository.observeCompletedTaskIds().map { it.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    fun saveTask(id: String?, title: String, type: TaskType, url: String?) {
        viewModelScope.launch {
            val isNew = id == null
            val existing = if (!isNew) repository.getTask(id!!) else null
            
            val task = TaskEntity(
                id = id ?: UUID.randomUUID().toString(),
                title = title,
                type = type,
                url = url?.takeIf { it.isNotBlank() },
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            repository.saveTask(task)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun restoreTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.saveTask(task)
        }
    }
}
