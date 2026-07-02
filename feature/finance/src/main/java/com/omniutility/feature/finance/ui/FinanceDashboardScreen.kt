package com.omniutility.feature.finance.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omniutility.feature.finance.data.db.AccountContainerEntity
import com.omniutility.feature.finance.data.db.FinancialCompassGoalEntity
import com.omniutility.feature.finance.data.db.MemoryLookupEntity
import com.omniutility.feature.finance.data.db.TransactionRecordEntity
import com.omniutility.feature.finance.data.service.StatementIngestionService
import com.omniutility.feature.finance.platform.AICoreStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FinanceTab {
    Home, Analytics, Vault
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceDashboardScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FinanceDashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val memoryRegistry by viewModel.memoryRegistry.collectAsState()
    val context = LocalContext.current

    var activeTab by remember { mutableStateOf(FinanceTab.Home) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var selectedCategoryContext by remember { mutableStateOf<String?>(null) }

    // Launcher for PDF/CSV files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && uiState.activeAccountId != null) {
            val intent = Intent(context, StatementIngestionService::class.java).apply {
                putExtra("file_uri", uri.toString())
                putExtra("account_id", uiState.activeAccountId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            android.widget.Toast.makeText(context, "Processing statement in background...", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Private AI Finance", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        // Airplane mode isolation shield status pill
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF2E7D32), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(10.dp))
                                Text("Offline Shield", color = Color(0xFF2E7D32), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshInsights() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Insights")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                NavigationBarItem(
                    selected = activeTab == FinanceTab.Home,
                    onClick = { activeTab = FinanceTab.Home },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = activeTab == FinanceTab.Analytics,
                    onClick = { activeTab = FinanceTab.Analytics },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Analytics") },
                    label = { Text("Analytics") }
                )
                NavigationBarItem(
                    selected = activeTab == FinanceTab.Vault,
                    onClick = { activeTab = FinanceTab.Vault },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Vault Setup") },
                    label = { Text("Vault") }
                )
            }
        },
        floatingActionButton = {
            if (activeTab == FinanceTab.Home && uiState.activeAccountId != null) {
                FloatingActionButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ingest Statement")
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (activeTab) {
                FinanceTab.Home -> {
                    HomeTabContent(
                        uiState = uiState,
                        onAddAccountClick = { showAddAccountDialog = true },
                        onAccountSelect = { viewModel.selectAccount(it) },
                        onUpdateCategory = { trx, cat -> viewModel.updateTransactionCategory(trx, cat) },
                        onDeleteTransaction = { viewModel.deleteTransaction(it) },
                        onClearTransactions = { viewModel.clearTransactions() },
                        onRetryDiagnostics = { viewModel.retryDiagnostics() }
                    )
                }
                FinanceTab.Analytics -> {
                    AnalyticsTabContent(
                        uiState = uiState,
                        chatMessages = chatMessages,
                        selectedCategoryContext = selectedCategoryContext,
                        onCategorySelect = { selectedCategoryContext = if (selectedCategoryContext == it) null else it },
                        onClearCategoryContext = { selectedCategoryContext = null },
                        onSendChatMessage = { msg -> viewModel.sendChatMessage(msg, selectedCategoryContext) }
                    )
                }
                FinanceTab.Vault -> {
                    VaultSetupTabContent(
                        uiState = uiState,
                        memoryRegistry = memoryRegistry,
                        onAddGoalClick = { showAddGoalDialog = true },
                        onDeleteGoal = { viewModel.deleteGoal(it) },
                        onFetchGoalAdvice = { viewModel.fetchGoalAdvice(it) },
                        onDeleteMemoryLookup = { viewModel.deleteMemoryLookup(it) }
                    )
                }
            }

            // Dialogs
            if (showAddAccountDialog) {
                AddAccountDialog(
                    onDismiss = { showAddAccountDialog = false },
                    onConfirm = { name, code, balance ->
                        viewModel.createAccount(name, code, balance)
                        showAddAccountDialog = false
                    }
                )
            }

            if (showAddGoalDialog) {
                AddGoalDialog(
                    onDismiss = { showAddGoalDialog = false },
                    onConfirm = { text, cat, cap, days ->
                        viewModel.addGoal(text, cat, cap, days)
                        showAddGoalDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun HomeTabContent(
    uiState: FinanceUiState,
    onAddAccountClick: () -> Unit,
    onAccountSelect: (String) -> Unit,
    onUpdateCategory: (TransactionRecordEntity, String) -> Unit,
    onDeleteTransaction: (TransactionRecordEntity) -> Unit,
    onClearTransactions: () -> Unit,
    onRetryDiagnostics: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // Diagnostics Banner
        item {
            DiagnosticsCard(uiState.aiCoreStatus, onRetryDiagnostics)
        }

        // Active summary ledger delta card
        item {
            val income = uiState.transactions.filter { it.type == "CR" }.sumOf { it.amount }
            val expenses = uiState.transactions.filter { it.type == "DR" }.sumOf { it.amount }
            val netDelta = income - expenses
            val activeAccount = uiState.accounts.find { it.containerId == uiState.activeAccountId }
            val currentBalance = activeAccount?.currentBalance ?: 0.0

            FinancialDeltaCard(
                currentBalance = currentBalance,
                income = income,
                expenses = expenses,
                netDelta = netDelta
            )
        }

        // Wallet Containers
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Account Wallets", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = onAddAccountClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }

            if (uiState.accounts.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "No active wallets. Tap '+' to initialize a secure room-container.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.accounts) { account ->
                        val isSelected = account.containerId == uiState.activeAccountId
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        val secondaryColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                        val balanceColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary

                        Card(
                            modifier = Modifier
                                .width(150.dp)
                                .clickable { onAccountSelect(account.containerId) },
                            shape = RoundedCornerShape(16.dp),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(account.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = contentColor)
                                Text(account.bankCode, fontSize = 11.sp, color = secondaryColor)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    String.format(Locale.getDefault(), "$%.2f", account.currentBalance),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = balanceColor
                                )
                            }
                        }
                    }
                }
            }
        }



        // Transaction list header with count and clear button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ledger Transactions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = uiState.transactions.size.toString(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                if (uiState.transactions.isNotEmpty()) {
                    TextButton(onClick = onClearTransactions) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        }

        if (uiState.transactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "No transactions in this wallet container.",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                items = uiState.transactions,
                key = { it.trxId }
            ) { trx ->
                TransactionItemCard(
                    transaction = trx,
                    onUpdateCategory = { onUpdateCategory(trx, it) },
                    onDelete = { onDeleteTransaction(trx) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
fun AnalyticsTabContent(
    uiState: FinanceUiState,
    chatMessages: List<ChatMessage>,
    selectedCategoryContext: String?,
    onCategorySelect: (String) -> Unit,
    onClearCategoryContext: () -> Unit,
    onSendChatMessage: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Insights Text banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("AI Contextual Insights", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (uiState.isInsightsLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text(uiState.insights, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }

        // Charts scrollable
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Interactive Expense Distribution Pie Chart
            val categorySum = uiState.transactions.filter { it.type == "DR" }
                .groupBy { it.category }
                .mapValues { entry -> entry.value.sumOf { it.amount } }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(220.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Expense Distribution", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    PieChart(
                        data = categorySum,
                        modifier = Modifier.fillMaxSize(),
                        onSliceClick = onCategorySelect
                    )
                }
            }

            // Cash flow velocity line chart
            val cashFlowPoints = remember(uiState.transactions) {
                var bal = 0.0
                uiState.transactions.sortedBy { it.timestamp }.map {
                    bal += if (it.type == "CR") it.amount else -it.amount
                    bal
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(220.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Velocity Cash Flow", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    LineChart(
                        points = cashFlowPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                }
            }
        }

        // Contextual AI Chat overlay at bottom of screen
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header with context badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Context-Aware Assistant", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (selectedCategoryContext != null) {
                        SuggestionChip(
                            onClick = onClearCategoryContext,
                            label = { Text("Category: $selectedCategoryContext") },
                            icon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                        )
                    }
                }

                // Chat history bubble
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(chatMessages) { chat ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            contentAlignment = if (chat.isUser) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (chat.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                                    )
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = chat.text,
                                    color = if (chat.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Input bar
                var textInput by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Ask something offline...", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    IconButton(
                        onClick = {
                            if (textInput.trim().isNotEmpty()) {
                                onSendChatMessage(textInput)
                                textInput = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun VaultSetupTabContent(
    uiState: FinanceUiState,
    memoryRegistry: List<MemoryLookupEntity>,
    onAddGoalClick: () -> Unit,
    onDeleteGoal: (FinancialCompassGoalEntity) -> Unit,
    onFetchGoalAdvice: (FinancialCompassGoalEntity) -> Unit,
    onDeleteMemoryLookup: (MemoryLookupEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Goals Setup Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Financial Compass Goals", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = onAddGoalClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        if (uiState.goals.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "No goals set yet. Set savings targets with offline private advice.",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(uiState.goals) { goal ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(goal.goalText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (goal.categoryRestriction != null) {
                                    Text("Category: ${goal.categoryRestriction}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            IconButton(onClick = { onDeleteGoal(goal) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Target: " + String.format(Locale.getDefault(), "$%.2f", goal.targetCap),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val dateText = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(goal.endDate))
                        Text("Deadline: $dateText", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(modifier = Modifier.height(12.dp))
                        val advice = uiState.goalAdvice[goal.goalId]
                        if (advice != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(8.dp)
                            ) {
                                Text("AI Advice: $advice", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        } else {
                            Button(
                                onClick = { onFetchGoalAdvice(goal) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Get AI Savings Strategy", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // Memory Registry Section
        item {
            Text("Learned System Memory Registry", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "A private lookup registry mapping merchant strings to custom categories.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (memoryRegistry.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "System memory is currently empty. Override transaction categories to save mappings here.",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(memoryRegistry) { memory ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(memory.rawStringMatch, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Category: ${memory.explicitUserCategory}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Text("Hits: ${memory.hitCount}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDeleteMemoryLookup(memory) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Purge Mapping", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// Custom Draw Charts
@Composable
fun PieChart(
    data: Map<String, Double>,
    modifier: Modifier = Modifier,
    onSliceClick: (String) -> Unit = {}
) {
    val total = data.values.sum()
    if (total == 0.0) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No debit data.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val colors = listOf(
        Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), Color(0xFF3F51B5),
        Color(0xFF2196F3), Color(0xFF00BCD4), Color(0xFF009688), Color(0xFF4CAF50)
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(90.dp)) {
                var startAngle = 0f
                data.values.forEachIndexed { index, value ->
                    val sweepAngle = (value / total * 360f).toFloat()
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true
                    )
                    startAngle += sweepAngle
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(data.entries.toList()) { entry ->
                val index = data.keys.indexOf(entry.key)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSliceClick(entry.key) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).background(colors[index % colors.size], RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${entry.key} (${String.format(Locale.getDefault(), "$%.2f", entry.value)})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun LineChart(
    points: List<Double>,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Need more ledger points.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val maxVal = points.maxOrNull() ?: 1.0
        val minVal = points.minOrNull() ?: 0.0
        val range = if (maxVal == minVal) 1.0 else maxVal - minVal

        val width = size.width
        val height = size.height
        val stepX = width / (points.size - 1)

        val path = Path().apply {
            val startY = height - (((points[0] - minVal) / range) * height).toFloat()
            moveTo(0f, startY)
            for (i in 1 until points.size) {
                val x = i * stepX
                val y = height - (((points[i] - minVal) / range) * height).toFloat()
                lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 4f)
        )
    }
}

// Helpers Cards
@Composable
fun DiagnosticsCard(
    status: AICoreStatus,
    onRetryClick: () -> Unit
) {
    val containerColor = when (status) {
        is AICoreStatus.Ready -> Color(0xFF2E7D32).copy(alpha = 0.1f)
        is AICoreStatus.Checking, is AICoreStatus.Downloading -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        is AICoreStatus.Fallback -> Color(0xFFE65100).copy(alpha = 0.1f)
        is AICoreStatus.Unsupported -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        is AICoreStatus.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (status) {
                    is AICoreStatus.Ready -> Icons.Default.CheckCircle
                    is AICoreStatus.Checking -> Icons.Default.Info
                    is AICoreStatus.Downloading -> Icons.Default.Refresh
                    is AICoreStatus.Fallback -> Icons.Default.Info
                    is AICoreStatus.Unsupported -> Icons.Default.Warning
                    is AICoreStatus.Error -> Icons.Default.Warning
                }
                Icon(
                    icon,
                    contentDescription = null,
                    tint = when (status) {
                        is AICoreStatus.Ready -> Color(0xFF2E7D32)
                        is AICoreStatus.Fallback -> Color(0xFFE65100)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text("On-Device AI Engine Status", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val description = when (status) {
                    is AICoreStatus.Ready -> "Gemini Nano hardware key binding successfully established."
                    is AICoreStatus.Checking -> "Scanning for local AICore engine..."
                    is AICoreStatus.Downloading -> "Syncing localized model packages: ${status.progressPercent}% downloaded."
                    is AICoreStatus.Fallback -> status.message
                    is AICoreStatus.Unsupported -> "Missing Gemini Nano. On-device LLM model requires hardware upgrade."
                    is AICoreStatus.Error -> "Hardware key binding failed: ${status.message}"
                }
                Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
            }

            if (status is AICoreStatus.Error || status is AICoreStatus.Unsupported) {
                IconButton(onClick = onRetryClick) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry check")
                }
            }
        }
    }
}

@Composable
fun FinancialDeltaCard(
    currentBalance: Double,
    income: Double,
    expenses: Double,
    netDelta: Double
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Ledger Balance", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                text = String.format(Locale.getDefault(), "$%.2f", currentBalance),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Income", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        String.format(Locale.getDefault(), "$%.2f", income),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1B5E20)
                    )
                }
                Column {
                    Text("Expenses", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        String.format(Locale.getDefault(), "$%.2f", expenses),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFFB71C1C)
                    )
                }
                Column {
                    Text("Net Delta", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        String.format(Locale.getDefault(), "$%.2f", netDelta),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (netDelta >= 0) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                    )
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionItemCard(
    transaction: TransactionRecordEntity,
    onUpdateCategory: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val categories = listOf("Food & Dining", "Shopping", "Groceries", "Utilities & Bills", "Transport & Travel", "Entertainment", "Income & Salary", "Others")

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (transaction.type == "CR") Color(0xFF2E7D32).copy(alpha = 0.1f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (transaction.type == "CR") Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (transaction.type == "CR") Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.cleanedVendor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val dateText = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(transaction.timestamp))
                Text(dateText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Category tag selector dropdown
                Box(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = transaction.category,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { expanded = true }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat, fontSize = 12.sp) },
                                onClick = {
                                    onUpdateCategory(cat)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val prefix = if (transaction.type == "CR") "+" else "-"
                val color = if (transaction.type == "CR") Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                Text(
                    text = String.format(Locale.getDefault(), "%s$%.2f", prefix, transaction.amount),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = color
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Account Wallet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") })
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Bank Code (e.g. Zenith, Kuda)") })
                OutlinedTextField(value = balanceText, onValueChange = { balanceText = it }, label = { Text("Starting Balance") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val bal = balanceText.toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty() && code.isNotEmpty()) {
                    onConfirm(name, code, bal)
                }
            }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?, Double, Int) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var capText by remember { mutableStateOf("") }
    var daysText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Financial Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Goal (e.g. Save $500 for rent)") })
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category Restriction (Optional)") })
                OutlinedTextField(value = capText, onValueChange = { capText = it }, label = { Text("Target Capital Cap") })
                OutlinedTextField(value = daysText, onValueChange = { daysText = it }, label = { Text("Duration in Days") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val cap = capText.toDoubleOrNull() ?: 0.0
                val days = daysText.toIntOrNull() ?: 30
                if (text.isNotEmpty()) {
                    onConfirm(text, category.takeIf { it.isNotEmpty() }, cap, days)
                }
            }) {
                Text("Set Goal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
