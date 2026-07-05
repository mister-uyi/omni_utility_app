package com.omniutility.feature.ownyourtime.data.db.dao

import androidx.room.*
import com.omniutility.feature.ownyourtime.data.db.entity.AppCategory
import com.omniutility.feature.ownyourtime.data.db.entity.AppConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_configs ORDER BY appLabel ASC")
    fun observeAll(): Flow<List<AppConfigEntity>>

    @Query("SELECT * FROM app_configs WHERE category = :category ORDER BY appLabel ASC")
    fun observeByCategory(category: AppCategory): Flow<List<AppConfigEntity>>

    @Query("SELECT * FROM app_configs WHERE category = :category ORDER BY appLabel ASC")
    suspend fun getByCategory(category: AppCategory): List<AppConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(appConfig: AppConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(appConfigs: List<AppConfigEntity>)

    @Query("DELETE FROM app_configs WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
