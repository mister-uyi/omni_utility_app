package com.omniutility.feature.finance.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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

fun getCurrencySymbol(bankCode: String): String {
    return when (bankCode.uppercase()) {
        "NGN", "₦" -> "₦"
        "USD", "$" -> "$"
        "EUR", "€" -> "€"
        "GBP", "£" -> "£"
        else -> "$"
    }
}

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
                    Text("Private AI Finance", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    AppBarAiStatus(status = uiState.aiCoreStatus, onRetryClick = { viewModel.retryDiagnostics() })
                    Spacer(modifier = Modifier.width(4.dp))
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
                        onClearTransactions = { viewModel.clearTransactions() }
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
                        onDeleteMemoryLookup = { viewModel.deleteMemoryLookup(it) },
                        onSaveApiKey = { viewModel.updateApiKey(it) },
                        onSaveBasePrompt = { viewModel.updateBasePrompt(it) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTabContent(
    uiState: FinanceUiState,
    onAddAccountClick: () -> Unit,
    onAccountSelect: (String) -> Unit,
    onUpdateCategory: (TransactionRecordEntity, String) -> Unit,
    onDeleteTransaction: (TransactionRecordEntity) -> Unit,
    onClearTransactions: () -> Unit
) {
    val activeAccount = uiState.accounts.find { it.containerId == uiState.activeAccountId }
    val currencySymbol = activeAccount?.let { getCurrencySymbol(it.bankCode) } ?: "$"
    var showGrouped by remember { mutableStateOf(false) }
    val expandedMerchants = remember { mutableStateMapOf<String, Boolean>() }
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = screenWidth * 0.82f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // Sticky Header containing Diagnostics Banner and Active summary ledger delta card
        stickyHeader {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                
                val income = uiState.transactions.filter { it.type == "CR" }.sumOf { it.amount }
                val expenses = uiState.transactions.filter { it.type == "DR" }.sumOf { it.amount }
                val netDelta = income - expenses
                val currentBalance = activeAccount?.currentBalance ?: 0.0

                FinancialDeltaCard(
                    currentBalance = currentBalance,
                    income = income,
                    expenses = expenses,
                    netDelta = netDelta,
                    currencySymbol = currencySymbol
                )
            }
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
                                .width(cardWidth)
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
                                    String.format(Locale.getDefault(), "%s%,.2f", getCurrencySymbol(account.bankCode), account.currentBalance),
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
                    if (uiState.isProcessingStatement) {
                        Spacer(modifier = Modifier.width(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Processing statement...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (uiState.transactions.isNotEmpty() && !uiState.isProcessingStatement) {
                    TextButton(onClick = onClearTransactions) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        }

        // Segmented Control for Detailed vs Grouped
        if (uiState.transactions.isNotEmpty()) {
            item {
                val totalCount = uiState.transactions.size
                val groupedCount = uiState.transactions.groupBy { it.cleanedVendor }.size

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val buttonModifier = Modifier.weight(1f)
                    
                    Button(
                        onClick = { showGrouped = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!showGrouped) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (!showGrouped) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        modifier = buttonModifier
                    ) {
                        Text("Detailed List ($totalCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { showGrouped = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showGrouped) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (showGrouped) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        modifier = buttonModifier
                    ) {
                        Text("Grouped View ($groupedCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
        } else if (showGrouped) {
            val groupedTrxs = uiState.transactions.groupBy { it.cleanedVendor }
            items(groupedTrxs.keys.toList()) { vendor ->
                val trxs = groupedTrxs[vendor].orEmpty()
                val isExpanded = expandedMerchants[vendor] ?: false
                GroupedMerchantCard(
                    vendor = vendor,
                    transactions = trxs,
                    currencySymbol = currencySymbol,
                    isExpanded = isExpanded,
                    onToggleExpand = { expandedMerchants[vendor] = !isExpanded },
                    onUpdateCategory = onUpdateCategory,
                    onDeleteTransaction = onDeleteTransaction
                )
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
                    modifier = Modifier.animateItem(),
                    currencySymbol = currencySymbol
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
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            scrollState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pinned context category label at top if active
        if (selectedCategoryContext != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Focused on Category: $selectedCategoryContext",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onClearCategoryContext, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear Context",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Chat Container occupying full remaining height
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                // Header details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            Icons.Default.CheckCircle, 
                            contentDescription = null, 
                            tint = if (uiState.apiKey.isNotEmpty()) Color(0xFF1565C0) else Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (uiState.apiKey.isNotEmpty()) "Gemini AI Agent" else "On-Device Assistant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = "Context: ${uiState.transactions.size} transactions",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )

                // Scrollable Chat area
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(chatMessages) { chat ->
                            val isUser = chat.isUser
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                                verticalAlignment = Alignment.Top
                            ) {
                                if (!isUser) {
                                    // Gemini-style logo block on the left
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (uiState.apiKey.isNotEmpty()) Color(0xFF1565C0).copy(alpha = 0.15f) else Color(0xFF2E7D32).copy(alpha = 0.15f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle, 
                                            contentDescription = null, 
                                            tint = if (uiState.apiKey.isNotEmpty()) Color(0xFF1565C0) else Color(0xFF2E7D32),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Card(
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isUser) 16.dp else 4.dp,
                                        bottomEnd = if (isUser) 4.dp else 16.dp
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isUser) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                        }
                                    ),
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = chat.text,
                                            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }

                                if (isUser) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // User profile initial / circle
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "U", 
                                            fontWeight = FontWeight.Bold, 
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // If empty chat history, show helper suggestion prompt cards
                    if (chatMessages.size <= 1) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "How can I help you today?",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            
                            val suggestions = listOf(
                                "Analyze my recent transactions",
                                "Categorize my spending",
                                "Am I saving enough?"
                            )
                            
                            suggestions.forEach { prompt ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSendChatMessage(prompt) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(prompt, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send, 
                                            contentDescription = null, 
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Chat Input bar
                var textInput by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { 
                            Text(
                                if (uiState.apiKey.isNotEmpty()) "Ask Gemini..." else "Ask something offline...", 
                                fontSize = 13.sp
                            ) 
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        ),
                        singleLine = true
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
                        Icon(
                            Icons.AutoMirrored.Filled.Send, 
                            contentDescription = "Send", 
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
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
    onDeleteMemoryLookup: (MemoryLookupEntity) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onSaveBasePrompt: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // API Key Configuration Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Gemini API Configuration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Add a free API Key from Google AI Studio to run advanced transaction parsing and chats if on-device model is pending.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    var keyInput by remember { mutableStateOf(uiState.apiKey) }
                    
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        placeholder = { Text("Paste AQ. or AIzaSy key here...", fontSize = 13.sp) },
                        label = { Text("Gemini API Key", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.apiKey.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    keyInput = ""
                                    onSaveApiKey("")
                                }
                            ) {
                                Text("Remove Key", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Button(
                            onClick = { onSaveApiKey(keyInput.trim()) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Key")
                        }
                    }
                }
            }
        }

        // Custom AI Persona / Base System Prompt Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "AI Persona / Base System Prompt",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Define custom rules, guidelines, or a specific persona (e.g. strict savings coach, detailed accountant) for how Gemini answers questions on the Analytics screen.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    var promptInput by remember { mutableStateOf(uiState.basePrompt) }
                    
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        placeholder = { Text("E.g. You are a strict savings coach. Talk in a highly motivating tone...", fontSize = 13.sp) },
                        label = { Text("Base System Instruction", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.basePrompt.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    promptInput = ""
                                    onSaveBasePrompt("")
                                }
                            ) {
                                Text("Reset Default", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Button(
                            onClick = { onSaveBasePrompt(promptInput.trim()) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Prompt")
                        }
                    }
                }
            }
        }

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
        is AICoreStatus.CloudActive -> Color(0xFF1565C0).copy(alpha = 0.1f)
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
                    is AICoreStatus.CloudActive -> Icons.Default.CheckCircle
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
                        is AICoreStatus.CloudActive -> Color(0xFF1565C0)
                        is AICoreStatus.Fallback -> Color(0xFFE65100)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                val title = when (status) {
                    is AICoreStatus.CloudActive -> "Gemini Cloud AI Status"
                    else -> "On-Device AI Engine Status"
                }
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val description = when (status) {
                    is AICoreStatus.Ready -> "Gemini Nano hardware key binding successfully established."
                    is AICoreStatus.CloudActive -> "Gemini 3.1 Flash Lite API active (Key preview: ${status.keyPreview})."
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
    netDelta: Double,
    currencySymbol: String = "$"
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Ledger Balance", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                text = String.format(Locale.getDefault(), "%s%,.2f", currencySymbol, currentBalance),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Income", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        String.format(Locale.getDefault(), "%s%,.2f", currencySymbol, income),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1B5E20)
                    )
                }
                Column {
                    Text("Expenses", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        String.format(Locale.getDefault(), "%s%,.2f", currencySymbol, expenses),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFFB71C1C)
                    )
                }
                Column {
                    Text("Net Delta", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        String.format(Locale.getDefault(), "%s%s%,.2f", if (netDelta >= 0) "+" else "-", currencySymbol, kotlin.math.abs(netDelta)),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (netDelta >= 0) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                    )
                }
            }
        }
    }
}

@Composable
fun GroupedMerchantCard(
    vendor: String,
    transactions: List<TransactionRecordEntity>,
    currencySymbol: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onUpdateCategory: (TransactionRecordEntity, String) -> Unit,
    onDeleteTransaction: (TransactionRecordEntity) -> Unit
) {
    val totalNet = transactions.sumOf { if (it.type == "CR") it.amount else -it.amount }
    val count = transactions.size
    val mainCategory = transactions.firstOrNull()?.category ?: "Others"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (totalNet >= 0) Color(0xFF2E7D32).copy(alpha = 0.1f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (totalNet >= 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (totalNet >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(vendor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("$count transactions • $mainCategory", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                val prefix = if (totalNet >= 0) "+" else "-"
                val absAmount = kotlin.math.abs(totalNet)
                val color = if (totalNet >= 0) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format(Locale.getDefault(), "%s%s%,.2f", prefix, currencySymbol, absAmount),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = color
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    transactions.forEach { trx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                val dateText = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(trx.timestamp))
                                Text(dateText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val trxPrefix = if (trx.type == "CR") "+" else "-"
                                val trxColor = if (trx.type == "CR") Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                Text(
                                    text = String.format(Locale.getDefault(), "%s%s%,.2f", trxPrefix, currencySymbol, trx.amount),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = trxColor
                                )
                                IconButton(
                                    onClick = { onDeleteTransaction(trx) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
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
    modifier: Modifier = Modifier,
    currencySymbol: String = "$"
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
                val color = if (transaction.type == "CR") Color(0xFF1B5E20) else Color(0xFFB71C1C)
                Text(
                    text = String.format(Locale.getDefault(), "%s%s%,.2f", prefix, currencySymbol, transaction.amount),
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

@Composable
fun AppBarAiStatus(
    status: AICoreStatus,
    onRetryClick: () -> Unit
) {
    val badgeColor = when (status) {
        is AICoreStatus.Ready -> Color(0xFF2E7D32)
        is AICoreStatus.CloudActive -> Color(0xFF1565C0)
        is AICoreStatus.Checking, is AICoreStatus.Downloading -> Color.Gray
        is AICoreStatus.Fallback -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.error
    }
    
    val badgeText = when (status) {
        is AICoreStatus.Ready -> "Nano Active"
        is AICoreStatus.CloudActive -> "Gemini Active"
        is AICoreStatus.Checking -> "AI checking..."
        is AICoreStatus.Downloading -> "AI syncing..."
        is AICoreStatus.Fallback -> "Rules active"
        else -> "AI Error"
    }

    val badgeIcon = when (status) {
        is AICoreStatus.Ready, is AICoreStatus.CloudActive -> Icons.Default.CheckCircle
        is AICoreStatus.Checking, is AICoreStatus.Downloading -> Icons.Default.Refresh
        else -> Icons.Default.Warning
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(badgeColor.copy(alpha = 0.15f))
            .border(1.dp, badgeColor, CircleShape)
            .clickable(enabled = status is AICoreStatus.Error || status is AICoreStatus.Unsupported) {
                onRetryClick()
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(badgeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(12.dp))
            Text(badgeText, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

