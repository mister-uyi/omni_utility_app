package com.omniutility.feature.finance.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountContainerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(container: AccountContainerEntity): Long

    @Query("SELECT * FROM account_containers")
    fun getAllFlow(): Flow<List<AccountContainerEntity>>

    @Query("SELECT * FROM account_containers")
    suspend fun getAll(): List<AccountContainerEntity>

    @Query("UPDATE account_containers SET current_balance = :balance WHERE container_id = :containerId")
    suspend fun updateBalance(containerId: String, balance: Double): Int

    @Delete
    suspend fun delete(container: AccountContainerEntity): Int
}

@Dao
interface TransactionRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionRecordEntity>): List<Long>

    @Query("SELECT * FROM transaction_records ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<TransactionRecordEntity>>

    @Query("SELECT * FROM transaction_records WHERE container_id = :containerId ORDER BY timestamp DESC")
    fun getTransactionsByContainerFlow(containerId: String): Flow<List<TransactionRecordEntity>>

    @Query("SELECT * FROM transaction_records")
    suspend fun getAll(): List<TransactionRecordEntity>

    @Delete
    suspend fun delete(transaction: TransactionRecordEntity): Int
}

@Dao
interface MemoryLookupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lookup: MemoryLookupEntity): Long

    @Query("SELECT * FROM memory_lookup_matrix WHERE raw_string_match = :rawString LIMIT 1")
    suspend fun findByRawString(rawString: String): MemoryLookupEntity?

    @Query("SELECT * FROM memory_lookup_matrix")
    fun getAllFlow(): Flow<List<MemoryLookupEntity>>

    @Query("UPDATE memory_lookup_matrix SET hit_count = hit_count + 1 WHERE lookup_id = :lookupId")
    suspend fun incrementHitCount(lookupId: Int): Int

    @Delete
    suspend fun delete(lookup: MemoryLookupEntity): Int
}

@Dao
interface FinancialCompassGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: FinancialCompassGoalEntity): Long

    @Query("SELECT * FROM financial_compass_goals ORDER BY end_date ASC")
    fun getAllFlow(): Flow<List<FinancialCompassGoalEntity>>

    @Delete
    suspend fun delete(goal: FinancialCompassGoalEntity): Int
}
