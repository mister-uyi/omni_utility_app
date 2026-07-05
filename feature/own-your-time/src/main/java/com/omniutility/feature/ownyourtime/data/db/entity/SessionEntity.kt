package com.omniutility.feature.ownyourtime.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val plannedDurationMs: Long,
    val actualDurationMs: Long = 0L,
    val funBudgetPercent: Int,
    val funBudgetMs: Long,
    val funTimeUsedMs: Long = 0L,
    val extensions: String = "[]",
    val endedAt: Long? = null
)
