package com.omniutility.feature.ownyourtime.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskType { TEXT, WEB_LINK, YOUTUBE_LINK }

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: TaskType,
    val url: String? = null,
    val createdAt: Long,
    val isArchived: Boolean = false
)
