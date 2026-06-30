package com.omniutility.feature.finance.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniutility.feature.finance.data.db.AccountContainerEntity
import com.omniutility.feature.finance.data.db.FinancialCompassGoalEntity
import com.omniutility.feature.finance.data.db.TransactionRecordEntity
import com.omniutility.feature.finance.platform.AICoreStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceDashboardScreen(
    viewModel: FinanceDashboardViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var rawSmsText by remember { mutableStateOf("") }
    
    var showCategoryEditDialogForTrx by remember { mutableStateOf<TransactionRecordEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline AI Finance Manager", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Hardware/Diagnostics Banner
            item {
                DiagnosticsBanner(status = state.aiCoreStatus, onRetry = { viewModel.retryDiagnostics() })
            }

            // 2. Account Header and Selector
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Account Wallets",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showAddAccountDialog = true }) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Account")
                        }
                    }
                    
                    if (state.accounts.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "No active wallets. Tap '+' to initialize a secure room-container.",
                                modifier = Modifier.padding(16.dp),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(state.accounts) { acc ->
                                AccountBadge(
                                    account = acc,
                                    isSelected = acc.containerId == state.activeAccountId,
                                    onClick = { viewModel.selectAccount(acc.containerId) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Balance Card
            if (state.accounts.isNotEmpty()) {
                val activeAccount = state.accounts.find { it.containerId == state.activeAccountId }
                if (activeAccount != null) {
                    item {
                        BalanceCard(account = activeAccount)
                    }
                }
            }

            // 4. SMS Transaction parsing simulator
            if (state.accounts.isNotEmpty() && state.aiCoreStatus is AICoreStatus.Ready) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Parse Transaction Notification (AI Simulator)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = rawSmsText,
                                onValueChange = { rawSmsText = it },
                                placeholder = { Text("Paste SMS e.g., Debited USD 50.00 from Starbucks") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (rawSmsText.isNotEmpty()) {
                                        viewModel.addRawTransaction(rawSmsText)
                                        rawSmsText = ""
                                    }
                                },
                                modifier = Modifier.align(Alignment.End),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Parse with Gemini Nano")
                            }
                        }
                    }
                }
            }

            // 5. Private Advisor Insights Card
            if (state.accounts.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Private Offline Insights",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.refreshInsights() },
                                    enabled = !state.isInsightsLoading && state.aiCoreStatus is AICoreStatus.Ready
                                ) {
                                    if (state.isInsightsLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                    } else {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.insights,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 6. Goals Section
            if (state.accounts.isNotEmpty()) {
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Financial Compass Goals",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = { showAddGoalDialog = true }) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Goal")
                            }
                        }
                        
                        if (state.goals.isEmpty()) {
                            Text(
                                text = "No saving targets active. Create one to monitor your budgets.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            state.goals.forEach { goal ->
                                val advice = state.goalAdvice[goal.goalId]
                                GoalCard(
                                    goal = goal,
                                    advice = advice,
                                    onAskAdvice = { viewModel.fetchGoalAdvice(goal) },
                                    onDelete = { viewModel.deleteGoal(goal) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            // 7. Transaction List & AI Search Bar
            if (state.accounts.isNotEmpty()) {
                item {
                    Text(
                        text = "Transactions Ledger",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("Filter by merchant, category, or type...") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                if (state.transactions.isEmpty()) {
                    item {
                        Text(
                            text = "No matching transactions found.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    items(state.transactions) { trx ->
                        TransactionRow(
                            transaction = trx,
                            onClick = { showCategoryEditDialogForTrx = trx },
                            onDelete = { viewModel.deleteTransaction(trx) }
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showAddAccountDialog) {
        var name by remember { mutableStateOf("") }
        var bankCode by remember { mutableStateOf("") }
        var balance by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddAccountDialog = false },
            title = { Text("Initialize Secure Wallet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Wallet Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bankCode, onValueChange = { bankCode = it }, label = { Text("Bank Code (e.g. CHASE, CASH)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = balance, onValueChange = { balance = it }, label = { Text("Initial Balance") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bal = balance.toDoubleOrNull() ?: 0.0
                        if (name.isNotEmpty() && bankCode.isNotEmpty()) {
                            viewModel.createAccount(name, bankCode, bal)
                            showAddAccountDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAccountDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddGoalDialog) {
        var text by remember { mutableStateOf("") }
        var cap by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("") }
        var days by remember { mutableStateOf("30") }

        AlertDialog(
            onDismissRequest = { showAddGoalDialog = false },
            title = { Text("Set Financial Compass Goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Goal Description (e.g. Save on dining)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cap, onValueChange = { cap = it }, label = { Text("Spending Cap (e.g. 200.00)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category Restriction (Optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("Duration (Days)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cp = cap.toDoubleOrNull() ?: 0.0
                        val dy = days.toIntOrNull() ?: 30
                        val cat = if (category.isEmpty()) null else category
                        if (text.isNotEmpty() && cp > 0) {
                            viewModel.addGoal(text, cat, cp, dy)
                            showAddGoalDialog = false
                        }
                    }
                ) {
                    Text("Add Goal")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGoalDialog = false }) { Text("Cancel") }
            }
        )
    }

    showCategoryEditDialogForTrx?.let { trx ->
        var category by remember { mutableStateOf(trx.category) }
        AlertDialog(
            onDismissRequest = { showCategoryEditDialogForTrx = null },
            title = { Text("Override Category Mapping") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set manual category override for all future transactions with vendor: '${trx.cleanedVendor}'", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (category.isNotEmpty()) {
                            viewModel.updateTransactionCategory(trx, category)
                            showCategoryEditDialogForTrx = null
                        }
                    }
                ) {
                    Text("Apply Override")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryEditDialogForTrx = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun DiagnosticsBanner(
    status: AICoreStatus,
    onRetry: () -> Unit
) {
    val containerColor = when (status) {
        is AICoreStatus.Checking -> MaterialTheme.colorScheme.surfaceVariant
        is AICoreStatus.Ready -> Color(0xFFE8F5E9)
        is AICoreStatus.Downloading -> MaterialTheme.colorScheme.primaryContainer
        is AICoreStatus.Error, AICoreStatus.Unsupported -> Color(0xFFFFEBEE)
    }
    
    val textColor = when (status) {
        is AICoreStatus.Checking -> MaterialTheme.colorScheme.onSurfaceVariant
        is AICoreStatus.Ready -> Color(0xFF2E7D32)
        is AICoreStatus.Downloading -> MaterialTheme.colorScheme.onPrimaryContainer
        is AICoreStatus.Error, AICoreStatus.Unsupported -> Color(0xFFC62828)
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "On-Device AI Engine Status",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                val message = when (status) {
                    is AICoreStatus.Checking -> "Scanning secure keystore & binding local core..."
                    is AICoreStatus.Ready -> "Gemini Nano (AICore) is active & parsing transactions offline."
                    is AICoreStatus.Downloading -> "Downloading local Gemini Nano model: ${status.progressPercent}%"
                    is AICoreStatus.Unsupported -> "On-device AI is unsupported on this device. Feature locked."
                    is AICoreStatus.Error -> "Hardware key binding failed: ${status.message}"
                }
                Text(text = message, fontSize = 12.sp, color = textColor.copy(alpha = 0.8f))
            }
            
            if (status is AICoreStatus.Error || status is AICoreStatus.Unsupported) {
                IconButton(onClick = onRetry) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry", tint = textColor)
                }
            }
        }
    }
}

@Composable
fun AccountBadge(
    account: AccountContainerEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val text = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = account.displayName, color = text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun BalanceCard(account: AccountContainerEntity) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "${account.displayName} (${account.bankCode})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            val balanceStr = String.format(Locale.getDefault(), "$%.2f", account.currentBalance)
            Text(
                text = balanceStr,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Secured with SQLCipher AES-256",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun GoalCard(
    goal: FinancialCompassGoalEntity,
    advice: String?,
    onAskAdvice: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = goal.goalText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    val limitStr = String.format(Locale.getDefault(), "Limit: $%.2f", goal.targetCap)
                    Text(text = limitStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Row {
                    IconButton(onClick = {
                        expanded = !expanded
                        if (expanded && advice == null) {
                            onAskAdvice()
                        }
                    }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Show AI advice"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Goal", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Local AI Action Strategy:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = advice ?: "Generating strategy recommendation from recent spends...",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionRow(
    transaction: TransactionRecordEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(transaction.timestamp))
    
    val color = if (transaction.type == "CR") Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = transaction.cleanedVendor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = transaction.category, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            val amountPrefix = if (transaction.type == "CR") "+" else "-"
            val amountStr = String.format(Locale.getDefault(), "%s$%.2f", amountPrefix, transaction.amount)
            Text(
                text = amountStr,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete transaction",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
