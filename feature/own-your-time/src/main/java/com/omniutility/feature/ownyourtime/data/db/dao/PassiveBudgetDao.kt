package com.omniutility.feature.ownyourtime.data.db.dao

import androidx.room.*
import com.omniutility.feature.ownyourtime.data.db.entity.PassiveBudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PassiveBudgetDao {
    @Query("SELECT * FROM passive_budget WHERE id = 1")
    fun observe(): Flow<PassiveBudgetEntity?>

    @Query("SELECT * FROM passive_budget WHERE id = 1")
    suspend fun get(): PassiveBudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: PassiveBudgetEntity)
}
