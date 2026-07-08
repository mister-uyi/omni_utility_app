package com.omniutility.feature.ownyourtime.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omniutility.feature.ownyourtime.data.db.entity.UserInterestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserInterestDao {

    @Query("SELECT * FROM user_interests ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<UserInterestEntity>>

    @Query("SELECT * FROM user_interests ORDER BY addedAt DESC")
    suspend fun getAll(): List<UserInterestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(interest: UserInterestEntity)

    @Query("DELETE FROM user_interests WHERE id = :id")
    suspend fun delete(id: String)
}
