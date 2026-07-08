package com.omniutility.feature.ownyourtime.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a user's topic of interest for passive content recommendations.
 * Examples: "System Design", "Space", "History"
 */
@Entity(tableName = "user_interests")
data class UserInterestEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val topic: String,
    val addedAt: Long = System.currentTimeMillis()
)
