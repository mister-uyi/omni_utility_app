package com.omniutility.feature.ownyourtime.data.db.dao

import androidx.room.*
import com.omniutility.feature.ownyourtime.data.db.entity.UserConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserConfigDao {
    @Query("SELECT * FROM user_config WHERE id = 1")
    fun observe(): Flow<UserConfigEntity?>

    @Query("SELECT * FROM user_config WHERE id = 1")
    suspend fun get(): UserConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: UserConfigEntity)
}
