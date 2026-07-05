package com.omniutility.feature.ownyourtime.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AppCategory { PRODUCTIVITY, FUN, SYSTEM, SKIP }

@Entity(tableName = "app_configs")
data class AppConfigEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val category: AppCategory
)
