package com.omniutility.feature.finance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniutility.feature.finance.data.db.AccountContainerEntity
import com.omniutility.feature.finance.data.db.FinancialCompassGoalEntity
import com.omniutility.feature.finance.data.db.TransactionRecordEntity
import com.omniutility.feature.finance.data.repository.FinanceRepository
import com.omniutility.feature.finance.platform.AICoreManager
import com.omniutility.feature.finance.platform.AICoreStatus
import com.omniutility.feature.finance.data.db.MemoryLookupEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class FinanceUiState(
    val aiCoreStatus: AICoreStatus = AICoreStatus.Checking,
    val accounts: List<AccountContainerEntity> = emptyList(),
    val transactions: List<TransactionRecordEntity> = emptyList(),
    val goals: List<FinancialCompassGoalEntity> = emptyList(),
    val insights: String = "Analyze your data offline to generate insights.",
    val searchQuery: String = "",
    val activeAccountId: String? = null,
    val goalAdvice: Map<String, String> = emptyMap(),
    val isInsightsLoading: Boolean = false,
    val apiKey: String = "",
    val isProcessingStatement: Boolean = false,
    val basePrompt: String = ""
)

@HiltViewModel
class FinanceDashboardViewModel @Inject constructor(
    private val repository: FinanceRepository,
    private val aiCoreManager: AICoreManager,
    private val aiEngine: com.omniutility.feature.finance.data.ai.OfflineAIEngine
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _activeAccountId = MutableStateFlow<String?>(null)
    private val _insights = MutableStateFlow("Provide transactions to generate private offline insights.")
    private val _isInsightsLoading = MutableStateFlow(false)
    private val _goalAdvice = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _apiKey = MutableStateFlow(aiEngine.getApiKey())
    private val _basePrompt = MutableStateFlow(aiEngine.getBasePrompt())

    val uiState: StateFlow<FinanceUiState> = combine(
        aiCoreManager.status,
        repository.getAccountsFlow(),
        repository.getTransactionsFlow(),
        repository.getGoalsFlow(),
        _searchQuery,
        _activeAccountId,
        _insights,
        _goalAdvice,
        _isInsightsLoading,
        _apiKey,
        com.omniutility.feature.finance.data.service.StatementIngestionService.isProcessing,
        _basePrompt
    ) { flowValues: Array<Any?> ->
        val aiStatus = flowValues[0] as AICoreStatus
        val accounts = flowValues[1] as List<AccountContainerEntity>
        val transactions = flowValues[2] as List<TransactionRecordEntity>
        val goals = flowValues[3] as List<FinancialCompassGoalEntity>
        val query = flowValues[4] as String
        val activeId = flowValues[5] as String?
        val insightsText = flowValues[6] as String
        val adviceMap = flowValues[7] as Map<String, String>
        val insightsLoading = flowValues[8] as Boolean
        val apiKeyVal = flowValues[9] as String
        val isProcessingStatementVal = flowValues[10] as Boolean
        val basePromptVal = flowValues[11] as String
        
        // Auto-select first account if activeId is null
        val selectedId = activeId ?: accounts.firstOrNull()?.containerId
        
        // Filter transactions based on active account
        val accountTrxs = if (selectedId != null) {
            transactions.filter { it.containerId == selectedId }
        } else {
            transactions
        }

        // Apply search filtering
        val filteredTrxs = if (query.trim().isNotEmpty()) {
            accountTrxs.filter {
                it.cleanedVendor.contains(query, ignoreCase = true) ||
                it.rawNarration.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true)
            }
        } else {
            accountTrxs
        }

        FinanceUiState(
            aiCoreStatus = aiStatus,
            accounts = accounts,
            transactions = filteredTrxs,
            goals = goals,
            insights = insightsText,
            searchQuery = query,
            activeAccountId = selectedId,
            goalAdvice = adviceMap,
            isInsightsLoading = insightsLoading,
            apiKey = apiKeyVal,
            isProcessingStatement = isProcessingStatementVal,
            basePrompt = basePromptVal
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinanceUiState()
    )

    fun selectAccount(accountId: String) {
        _activeAccountId.value = accountId
    }

    fun updateApiKey(key: String) {
        aiEngine.saveApiKey(key)
        _apiKey.value = key
        
        val welcomeText = if (key.isNotEmpty())
            "Hi! I'm your Gemini AI assistant. Ask me anything about your expenses."
        else
            "Hi! I'm your offline Private AI. Ask me anything about your expenses."
        _chatMessages.value = listOf(ChatMessage(welcomeText, false))
    }

    fun updateBasePrompt(prompt: String) {
        aiEngine.saveBasePrompt(prompt)
        _basePrompt.value = prompt
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun createAccount(name: String, bankCode: String, initialBalance: Double) {
        viewModelScope.launch {
            repository.createAccount(name, bankCode, initialBalance)
        }
    }



    fun updateTransactionCategory(transaction: TransactionRecordEntity, category: String) {
        viewModelScope.launch {
            repository.updateTransactionCategory(transaction, category)
            refreshInsights()
        }
    }

    fun deleteTransaction(transaction: TransactionRecordEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            refreshInsights()
        }
    }

    fun clearTransactions() {
        viewModelScope.launch {
            uiState.value.activeAccountId?.let { accountId ->
                repository.clearTransactions(accountId)
                refreshInsights()
            }
        }
    }

    fun addGoal(text: String, category: String?, targetCap: Double, durationDays: Int) {
        viewModelScope.launch {
            val endDate = System.currentTimeMillis() + (durationDays * 24 * 60 * 60 * 1000L)
            repository.addGoal(text, category, targetCap, endDate)
        }
    }

    fun deleteGoal(goal: FinancialCompassGoalEntity) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
        }
    }

    fun refreshInsights() {
        viewModelScope.launch {
            _isInsightsLoading.value = true
            _insights.value = repository.fetchInsights()
            _isInsightsLoading.value = false
        }
    }

    fun fetchGoalAdvice(goal: FinancialCompassGoalEntity) {
        viewModelScope.launch {
            val advice = repository.fetchGoalAdvice(goal.goalText)
            _goalAdvice.value = _goalAdvice.value + (goal.goalId to advice)
        }
    }

    fun retryDiagnostics() {
        aiCoreManager.checkSupportAndPrepare()
    }

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                if (aiEngine.getApiKey().isNotEmpty())
                    "Hi! I'm your Gemini AI assistant. Ask me anything about your expenses."
                else
                    "Hi! I'm your offline Private AI. Ask me anything about your expenses.",
                false
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    val memoryRegistry: StateFlow<List<MemoryLookupEntity>> = repository.getMemoryLookupsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteMemoryLookup(lookup: MemoryLookupEntity) {
        viewModelScope.launch {
            repository.deleteMemoryLookup(lookup)
        }
    }

    fun sendChatMessage(messageText: String, categoryContext: String?) {
        val userMsg = ChatMessage(messageText, true)
        _chatMessages.value = _chatMessages.value + userMsg

        viewModelScope.launch {
            val transactions = uiState.value.transactions
            val totalIncome = transactions.filter { it.type == "CR" }.sumOf { it.amount }
            val totalExpenses = transactions.filter { it.type == "DR" }.sumOf { it.amount }
            val balance = totalIncome - totalExpenses
            val transactionCount = transactions.size

            val categoryBreakdown = transactions
                .groupBy { it.category }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
                .entries
                .joinToString("\n") { "- ${it.key}: ₦${String.format(java.util.Locale.US, "%,.2f", it.value)}" }

            val summaryText = transactions.take(1000).joinToString("\n") {
                "- ${it.cleanedVendor}: ${it.type} ₦${String.format(java.util.Locale.US, "%,.2f", it.amount)} [${it.category}]"
            }
            
            val customSystemPrompt = aiEngine.getBasePrompt()
            val systemContext = if (customSystemPrompt.trim().isNotEmpty()) {
                "System Instructions/Base Prompt: $customSystemPrompt\n"
            } else {
                "You are a private offline finance advisor. Answer the user's question concisely based on their transactions and the category context if provided.\n"
            }
            
            val prompt = """
                $systemContext
                Category Context: ${categoryContext ?: "None"}
                User Question: "$messageText"

                Verified Dashboard Metrics (Calculated mathematically, DO NOT count or sum raw transactions to guess these):
                - Total Transactions Count: $transactionCount
                - Total Ingested Income: ₦${String.format(java.util.Locale.US, "%,.2f", totalIncome)}
                - Total Ingested Expenses: ₦${String.format(java.util.Locale.US, "%,.2f", totalExpenses)}
                - Net Ledger Balance: ₦${String.format(java.util.Locale.US, "%,.2f", balance)}

                Category-wise Spending Totals:
                $categoryBreakdown

                Raw Transaction History List:
                $summaryText
            """.trimIndent()

            val response = try {
                aiEngine.generateChatReply(prompt, categoryContext)
            } catch (e: Exception) {
                "Error: ${e.message}"
            }

            _chatMessages.value = _chatMessages.value + ChatMessage(response, false)
        }
    }
}
