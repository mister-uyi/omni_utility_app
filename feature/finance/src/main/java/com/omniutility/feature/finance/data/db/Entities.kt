package com.omniutility.feature.finance.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "account_containers")
data class AccountContainerEntity(
    @PrimaryKey
    @ColumnInfo(name = "container_id")
    val containerId: String,
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "bank_code")
    val bankCode: String,
    
    @ColumnInfo(name = "current_balance")
    val currentBalance: Double
)

@Entity(
    tableName = "transaction_records",
    foreignKeys = [
        ForeignKey(
            entity = AccountContainerEntity::class,
            parentColumns = ["container_id"],
            childColumns = ["container_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["container_id"])]
)
data class TransactionRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "trx_id")
    val trxId: String,
    
    @ColumnInfo(name = "container_id")
    val containerId: String,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "raw_narration")
    val rawNarration: String,
    
    @ColumnInfo(name = "cleaned_vendor")
    val cleanedVendor: String,
    
    @ColumnInfo(name = "amount")
    val amount: Double,
    
    @ColumnInfo(name = "type") // CR or DR
    val type: String,
    
    @ColumnInfo(name = "category")
    val category: String
)

@Entity(
    tableName = "memory_lookup_matrix",
    indices = [Index(value = ["raw_string_match"], unique = true)]
)
data class MemoryLookupEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "lookup_id")
    val lookupId: Int = 0,
    
    @ColumnInfo(name = "raw_string_match")
    val rawStringMatch: String,
    
    @ColumnInfo(name = "explicit_user_category")
    val explicitUserCategory: String,
    
    @ColumnInfo(name = "hit_count")
    val hitCount: Int = 1
)

@Entity(tableName = "financial_compass_goals")
data class FinancialCompassGoalEntity(
    @PrimaryKey
    @ColumnInfo(name = "goal_id")
    val goalId: String,
    
    @ColumnInfo(name = "goal_text")
    val goalText: String,
    
    @ColumnInfo(name = "category_restriction")
    val categoryRestriction: String?,
    
    @ColumnInfo(name = "target_cap")
    val targetCap: Double,
    
    @ColumnInfo(name = "end_date")
    val endDate: Long
)
