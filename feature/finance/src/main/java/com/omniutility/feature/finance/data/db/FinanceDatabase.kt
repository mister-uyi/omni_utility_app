package com.omniutility.feature.finance.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountContainerEntity::class,
        TransactionRecordEntity::class,
        MemoryLookupEntity::class,
        FinancialCompassGoalEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun accountContainerDao(): AccountContainerDao
    abstract fun transactionRecordDao(): TransactionRecordDao
    abstract fun memoryLookupDao(): MemoryLookupDao
    abstract fun financialCompassGoalDao(): FinancialCompassGoalDao

    companion object {
        init {
            System.loadLibrary("sqlcipher")
        }
    }
}
