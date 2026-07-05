package com.omniutility.feature.ownyourtime.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_config")
data class UserConfigEntity(
    @PrimaryKey val id: Int = 1,
    val userName: String = "",
    val defaultDurationMs: Long = 3_600_000L,
    val defaultFunBudgetPercent: Int = 10
)
