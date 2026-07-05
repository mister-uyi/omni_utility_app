package com.omniutility.feature.ownyourtime.data.db.dao

import androidx.room.*
import com.omniutility.feature.ownyourtime.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Query("UPDATE tasks SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: String)

    @Delete
    suspend fun delete(task: TaskEntity)
}
