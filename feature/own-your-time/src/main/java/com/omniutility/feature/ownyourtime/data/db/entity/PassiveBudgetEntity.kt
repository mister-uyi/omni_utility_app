package com.omniutility.feature.ownyourtime.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton row that stores the passive fun-time budget configuration
 * and accumulated usage for the current clock-aligned cycle.
 */
@Entity(tableName = "passive_budget")
data class PassiveBudgetEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = false,
    val periodMinutes: Int = 60,       // The cycle length (e.g., 60 = 1 hour)
    val budgetPercent: Int = 20,       // % of the period for fun apps
    val funTimeUsedMs: Long = 0L,      // Accumulated fun time in current cycle
    val cycleStartMs: Long = 0L        // Start of current clock-aligned cycle
)
