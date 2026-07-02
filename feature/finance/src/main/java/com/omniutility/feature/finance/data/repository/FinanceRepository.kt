package com.omniutility.feature.finance.data.repository

import com.omniutility.feature.finance.data.ai.OfflineAIEngine
import com.omniutility.feature.finance.data.ai.ParsedTransaction
import com.omniutility.feature.finance.data.db.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepository @Inject constructor(
    private val accountContainerDao: AccountContainerDao,
    private val transactionRecordDao: TransactionRecordDao,
    private val memoryLookupDao: MemoryLookupDao,
    private val financialCompassGoalDao: FinancialCompassGoalDao,
    private val aiEngine: OfflineAIEngine
) {
    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (accountContainerDao.getAll().isEmpty()) {
                    createAccount("Default Wallet", "NGN", 0.0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    // --- Account Container Operations ---
    fun getAccountsFlow(): Flow<List<AccountContainerEntity>> = accountContainerDao.getAllFlow()
    
    suspend fun getAccounts(): List<AccountContainerEntity> = accountContainerDao.getAll()

    suspend fun createAccount(name: String, bankCode: String, initialBalance: Double) {
        val account = AccountContainerEntity(
            containerId = UUID.randomUUID().toString(),
            displayName = name,
            bankCode = bankCode,
            currentBalance = initialBalance
        )
        accountContainerDao.insert(account)
    }

    suspend fun deleteAccount(account: AccountContainerEntity) {
        accountContainerDao.delete(account)
    }

    // --- Transaction Operations ---
    fun getTransactionsFlow(): Flow<List<TransactionRecordEntity>> = transactionRecordDao.getAllFlow()

    fun getTransactionsByAccountFlow(accountId: String): Flow<List<TransactionRecordEntity>> =
        transactionRecordDao.getTransactionsByContainerFlow(accountId)

    suspend fun getTransactions(): List<TransactionRecordEntity> = transactionRecordDao.getAll()

    /**
     * Parses a raw narration, resolves the category via AI or memory cache, and registers the transaction.
     */
    suspend fun addRawTransaction(accountId: String, rawNarration: String): TransactionRecordEntity? {
        val accounts = accountContainerDao.getAll()
        val account = accounts.find { it.containerId == accountId } ?: return null

        // 1. Try parsing transaction using Gemini Nano
        val aiResult = aiEngine.parseTransaction(rawNarration)
        
        var amount = 0.0
        var vendor = "Unknown Merchant"
        var type = "DR"
        var category = "Others"

        if (aiResult != null) {
            amount = aiResult.amount
            vendor = aiResult.vendor
            type = aiResult.type
            category = aiResult.category
        } else {
            // Fallback: parse basic amount and merchant if AI fails
            // Simple regex match for amounts (e.g. 100.00, 45)
            val amountRegex = """(?i)(?:USD|NGN|EUR|GBP)?\s*(\d+(?:\.\d{1,2})?)""".toRegex()
            val match = amountRegex.find(rawNarration)
            if (match != null) {
                amount = match.groupValues[1].toDoubleOrNull() ?: 0.0
            }
        }

        // 2. Resolve Category from Local Memory Cache (if exists)
        val cachedLookup = memoryLookupDao.findByRawString(vendor.lowercase().trim())
        if (cachedLookup != null) {
            category = cachedLookup.explicitUserCategory
            memoryLookupDao.incrementHitCount(cachedLookup.lookupId)
        }

        val trxId = UUID.randomUUID().toString()
        val transaction = TransactionRecordEntity(
            trxId = trxId,
            containerId = accountId,
            timestamp = System.currentTimeMillis(),
            rawNarration = rawNarration,
            cleanedVendor = vendor,
            amount = amount,
            type = type,
            category = category
        )

        // Save transaction
        transactionRecordDao.insertAll(listOf(transaction))

        // Update account balance
        val newBalance = if (type == "CR") {
            account.currentBalance + amount
        } else {
            account.currentBalance - amount
        }
        accountContainerDao.updateBalance(accountId, newBalance)

        return transaction
    }

    /**
     * User can manually edit / override the category of a transaction.
     * We save this mapping to the memory lookup table so future transactions match it.
     */
    suspend fun updateTransactionCategory(transaction: TransactionRecordEntity, newCategory: String) {
        val updatedTrx = transaction.copy(category = newCategory)
        transactionRecordDao.insertAll(listOf(updatedTrx))

        // Save mapping to memory cache
        val key = transaction.cleanedVendor.lowercase().trim()
        val existing = memoryLookupDao.findByRawString(key)
        if (existing != null) {
            memoryLookupDao.insert(existing.copy(explicitUserCategory = newCategory))
        } else {
            memoryLookupDao.insert(
                MemoryLookupEntity(
                    rawStringMatch = key,
                    explicitUserCategory = newCategory
                )
            )
        }
    }

    suspend fun deleteTransaction(transaction: TransactionRecordEntity) {
        // Reverse balance change
        val account = accountContainerDao.getAll().find { it.containerId == transaction.containerId }
        if (account != null) {
            val newBalance = if (transaction.type == "CR") {
                account.currentBalance - transaction.amount
            } else {
                account.currentBalance + transaction.amount
            }
            accountContainerDao.updateBalance(transaction.containerId, newBalance)
        }
        transactionRecordDao.delete(transaction)
    }

    fun getMemoryLookupsFlow(): Flow<List<MemoryLookupEntity>> = memoryLookupDao.getAllFlow()

    suspend fun deleteMemoryLookup(lookup: MemoryLookupEntity) {
        memoryLookupDao.delete(lookup)
    }

    suspend fun addRawTransactionChunk(accountId: String, chunkText: String): List<ParsedTransaction> {
        val accounts = accountContainerDao.getAll()
        val account = accounts.find { it.containerId == accountId } ?: return emptyList()

        val parsedTrxs = aiEngine.parseTransactionChunk(chunkText)
        if (parsedTrxs.isEmpty()) return emptyList()

        var accumulatedBalance = account.currentBalance
        val entities = parsedTrxs.map { trx ->
            // Check memory cache lookup
            var category = trx.category
            val cachedLookup = memoryLookupDao.findByRawString(trx.vendor.lowercase().trim())
            if (cachedLookup != null) {
                category = cachedLookup.explicitUserCategory
                memoryLookupDao.incrementHitCount(cachedLookup.lookupId)
            }

            accumulatedBalance = if (trx.type == "CR") {
                accumulatedBalance + trx.amount
            } else {
                accumulatedBalance - trx.amount
            }

            TransactionRecordEntity(
                trxId = UUID.randomUUID().toString(),
                containerId = accountId,
                timestamp = System.currentTimeMillis(),
                rawNarration = "Statement Import Line",
                cleanedVendor = trx.vendor,
                amount = trx.amount,
                type = trx.type,
                category = category
            )
        }

        // Save transactions
        transactionRecordDao.insertAll(entities)

        // Save updated account balance
        accountContainerDao.updateBalance(accountId, accumulatedBalance)

        return parsedTrxs
    }

    // --- Financial Compass Goals Operations ---
    fun getGoalsFlow(): Flow<List<FinancialCompassGoalEntity>> = financialCompassGoalDao.getAllFlow()

    suspend fun addGoal(text: String, category: String?, cap: Double, endDate: Long) {
        val goal = FinancialCompassGoalEntity(
            goalId = UUID.randomUUID().toString(),
            goalText = text,
            categoryRestriction = category,
            targetCap = cap,
            endDate = endDate
        )
        financialCompassGoalDao.insert(goal)
    }

    suspend fun deleteGoal(goal: FinancialCompassGoalEntity) {
        financialCompassGoalDao.delete(goal)
    }

    // --- Private Insights & Advisor ---
    suspend fun fetchInsights(): String {
        val transactions = transactionRecordDao.getAll()
        val goals = financialCompassGoalDao.getAllFlow().firstOrNull() ?: emptyList()

        if (transactions.isEmpty()) {
            return "Provide some transactions or narrations to generate your offline insights."
        }

        // Aggregate transactions for the prompt context
        val summaryBuilder = StringBuilder()
        transactions.take(20).forEach {
            summaryBuilder.append("- ${it.cleanedVendor}: ${it.type} ${it.amount} [${it.category}]\n")
        }

        val goalsSummaryBuilder = StringBuilder()
        goals.forEach {
            goalsSummaryBuilder.append("- Goal: ${it.goalText} (Cap: ${it.targetCap})\n")
        }

        return aiEngine.generateInsights(summaryBuilder.toString(), goalsSummaryBuilder.toString())
    }

    suspend fun fetchGoalAdvice(goalText: String): String {
        val transactions = transactionRecordDao.getAll()
        val summaryBuilder = StringBuilder()
        transactions.take(10).forEach {
            summaryBuilder.append("- ${it.cleanedVendor}: ${it.type} ${it.amount} [${it.category}]\n")
        }
        return aiEngine.generateGoalAdvice(goalText, summaryBuilder.toString())
    }
}
