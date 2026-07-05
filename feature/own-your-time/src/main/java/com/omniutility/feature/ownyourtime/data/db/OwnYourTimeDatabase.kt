package com.omniutility.feature.ownyourtime.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.omniutility.feature.ownyourtime.data.db.dao.*
import com.omniutility.feature.ownyourtime.data.db.entity.*

class Converters {
    @TypeConverter
    fun fromTaskType(value: TaskType): String = value.name
    @TypeConverter
    fun toTaskType(value: String): TaskType = TaskType.valueOf(value)
    @TypeConverter
    fun fromAppCategory(value: AppCategory): String = value.name
    @TypeConverter
    fun toAppCategory(value: String): AppCategory = AppCategory.valueOf(value)
}

@Database(
    entities = [
        TaskEntity::class,
        SessionEntity::class,
        SessionTaskEntity::class,
        AppConfigEntity::class,
        UserConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class OwnYourTimeDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun sessionDao(): SessionDao
    abstract fun appConfigDao(): AppConfigDao
    abstract fun userConfigDao(): UserConfigDao
}
