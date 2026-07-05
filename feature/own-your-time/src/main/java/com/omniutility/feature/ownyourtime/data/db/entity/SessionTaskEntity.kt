package com.omniutility.feature.ownyourtime.data.db.entity

import androidx.room.Entity

@Entity(
    tableName = "session_tasks",
    primaryKeys = ["sessionId", "taskId"]
)
data class SessionTaskEntity(
    val sessionId: String,
    val taskId: String,
    val taskSnapshot: String,
    val completed: Boolean = false,
    val completedAt: Long? = null
)
