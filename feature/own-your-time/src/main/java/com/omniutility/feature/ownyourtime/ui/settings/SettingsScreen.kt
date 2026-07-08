package com.omniutility.feature.ownyourtime.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import com.omniutility.core.ui.HUDPill
import com.omniutility.core.ui.HUDPillMessage
import com.omniutility.core.ui.HUDPillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omniutility.feature.ownyourtime.data.db.entity.AppCategory
import com.omniutility.feature.ownyourtime.data.db.entity.AppConfigEntity
import com.omniutility.feature.ownyourtime.data.db.entity.UserConfigEntity
import com.omniutility.feature.ownyourtime.data.db.entity.UserInterestEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var hudMessage by remember { mutableStateOf<HUDPillMessage?>(null) }
    
    val accentColor = Color(0xFFF5A623)
    val surfaceColor = Color(0xFF1A1A1A)
    val borderColor = Color(0xFF2A2A2A)

    val onRemoveApp: (AppConfigEntity) -> Unit = { app ->
        viewModel.removeAppConfig(app.packageName)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "${app.appLabel} removed",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.addAppToCategory(app.packageName, app.appLabel, app.category)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0D0D0D),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // User Profile Section
            item {
                SectionTitle("User Profile")
                var localUserName by remember(state.userConfig.userName) { mutableStateOf(state.userConfig.userName) }
                OutlinedTextField(
                    value = localUserName,
                    onValueChange = { 
                        localUserName = it
                        viewModel.updateUserName(it)
                    },
                    label = { Text("Your Name", color = Color(0xFF8A8A8A)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = borderColor,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = accentColor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Session Defaults Section
            item {
                SectionTitle("Session Defaults")
                SessionDefaultsCard(
                    userConfig = state.userConfig,
                    onDurationChange = { viewModel.updateDefaultDuration(it) },
                    onFunBudgetChange = { viewModel.updateDefaultFunBudgetPercent(it) },
                    accentColor = accentColor,
                    surfaceColor = surfaceColor,
                    borderColor = borderColor
                )
            }

            // App Categories Section
            item {
                SectionTitle("App Categories")
                
                AppCategorySection(
                    title = "PRODUCTIVITY",
                    apps = state.productivityApps,
                    color = accentColor,
                    surfaceColor = surfaceColor,
                    borderColor = borderColor,
                    onAddClick = { viewModel.showAppPicker(AppCategory.PRODUCTIVITY) },
                    onRemove = onRemoveApp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                AppCategorySection(
                    title = "FUN",
                    apps = state.funApps,
                    color = accentColor,
                    surfaceColor = surfaceColor,
                    borderColor = borderColor,
                    onAddClick = { viewModel.showAppPicker(AppCategory.FUN) },
                    onRemove = onRemoveApp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                AppCategorySection(
                    title = "SYSTEM",
                    apps = state.systemApps,
                    color = Color(0xFF8A8A8A),
                    surfaceColor = surfaceColor,
                    borderColor = borderColor,
                    onAddClick = { viewModel.showAppPicker(AppCategory.SYSTEM) },
                    onRemove = onRemoveApp,
                    isSystem = true
                )
                
                Text(
                    text = "Apps not assigned to any category are hidden during sessions.",
                    color = Color(0xFF8A8A8A),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            // Passive Tracking Section
            item {
                SectionTitle("Passive Tracking")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(surfaceColor)
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Enable toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Passive Tracking", color = Color.White, fontSize = 14.sp)
                        Switch(
                            checked = state.passiveBudgetEnabled,
                            onCheckedChange = { viewModel.updatePassiveBudgetEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = accentColor,
                                checkedTrackColor = accentColor.copy(alpha = 0.3f),
                                uncheckedThumbColor = Color(0xFF8A8A8A),
                                uncheckedTrackColor = borderColor
                            )
                        )
                    }

                    if (state.passiveBudgetEnabled) {
                        // Period selector chips
                        Column {
                            Text("Tracking Period", color = Color.White, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val periods = listOf(30 to "30m", 60 to "1h", 120 to "2h", 240 to "4h")
                                periods.forEach { (minutes, label) ->
                                    val isSelected = state.passiveBudgetPeriodMinutes == minutes
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                                            .border(1.dp, if (isSelected) accentColor else borderColor, RoundedCornerShape(16.dp))
                                            .clickable { viewModel.updatePassiveBudgetPeriod(minutes) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(label, color = if (isSelected) accentColor else Color.White, fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        // Budget percentage slider
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Fun Budget", color = Color.White, fontSize = 14.sp)
                                Text("${state.passiveBudgetPercent}%", color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = state.passiveBudgetPercent.toFloat(),
                                onValueChange = { viewModel.updatePassiveBudgetPercent(it.toInt()) },
                                valueRange = 0f..50f,
                                steps = 49,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor,
                                    inactiveTrackColor = borderColor
                                )
                            )
                            val funMinutes = state.passiveBudgetPeriodMinutes * state.passiveBudgetPercent / 100
                            val periodLabel = when (state.passiveBudgetPeriodMinutes) {
                                30 -> "30 minutes"
                                60 -> "hour"
                                120 -> "2 hours"
                                240 -> "4 hours"
                                else -> "${state.passiveBudgetPeriodMinutes} minutes"
                            }
                            Text(
                                "$funMinutes minutes of fun per $periodLabel",
                                color = Color(0xFF8A8A8A),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Topics of Interest Section
            item {
                SectionTitle("Topics of Interest")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(surfaceColor)
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Current interests list
                    if (state.interests.isNotEmpty()) {
                        state.interests.forEach { interest ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0D0D0D))
                                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(interest.topic, color = Color.White, fontSize = 14.sp)
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove ${interest.topic}",
                                    tint = Color(0xFF8A8A8A),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { viewModel.removeInterest(interest.id) }
                                )
                            }
                        }
                    } else {
                        Text(
                            "No topics added yet. Add some below!",
                            color = Color(0xFF8A8A8A),
                            fontSize = 13.sp
                        )
                    }

                    // Pre-suggested topic chips
                    val addedTopics = state.interests.map { it.topic.lowercase() }.toSet()
                    val suggestions = listOf(
                        "System Design", "Machine Learning", "History", "Space",
                        "Philosophy", "Fitness", "Cooking", "Finance", "Music", "Art"
                    ).filter { it.lowercase() !in addedTopics }

                    if (suggestions.isNotEmpty()) {
                        Text("Suggestions", color = Color(0xFF8A8A8A), fontSize = 12.sp)
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            suggestions.forEach { suggestion ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                        .clickable { viewModel.addInterest(suggestion) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(suggestion, color = accentColor, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // Add custom topic field
                    var newTopic by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTopic,
                            onValueChange = { newTopic = it },
                            placeholder = { Text("Add a topic…", color = Color(0xFF8A8A8A)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = borderColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = accentColor
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (newTopic.isNotBlank()) {
                                    viewModel.addInterest(newTopic)
                                    newTopic = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = Color(0xFF0D0D0D)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Picture-in-Picture Bypass Tip
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Picture-in-Picture Bypass Warning",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Video apps like YouTube or Netflix can bypass focus sessions by entering Picture-in-Picture mode automatically. To enforce complete blocking, manually turn off the Picture-in-Picture permission for these apps in Android Settings.",
                            fontSize = 12.sp,
                            color = Color(0xFF8A8A8A)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Button(
                            onClick = {
                                try {
                                    val intent = android.content.Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS")
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (ex: Exception) {
                                        hudMessage = HUDPillMessage("Could not open Settings", HUDPillType.ERROR)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open PiP Settings", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (state.appPickerCategory != null) {
            val assignedPackages = state.productivityApps.map { it.packageName }.toSet() + 
                                   state.funApps.map { it.packageName }.toSet() + 
                                   state.systemApps.map { it.packageName }.toSet()
            AppPickerSheet(
                installedApps = state.pickerApps,
                targetCategory = state.appPickerCategory!!,
                onDismiss = { viewModel.hideAppPicker() },
                onToggleApp = { app, category, isAssigned -> 
                    if (isAssigned) {
                        viewModel.removeAppConfig(app.packageName)
                    } else {
                        viewModel.addAppToCategory(app.packageName, app.appLabel, category)
                    }
                },
                assignedPackages = assignedPackages,
                surfaceColor = surfaceColor,
                accentColor = accentColor
            )
        }

        hudMessage?.let { msg ->
            HUDPill(
                message = msg.message,
                type = msg.type,
                onDismiss = { hudMessage = null }
            )
        }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SessionDefaultsCard(
    userConfig: UserConfigEntity,
    onDurationChange: (Long) -> Unit,
    onFunBudgetChange: (Int) -> Unit,
    accentColor: Color,
    surfaceColor: Color,
    borderColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Duration presets
        Column {
            Text("Default Duration", color = Color.White, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(1L * 60 * 1000 to "1m", 2L * 60 * 1000 to "2m", 5L * 60 * 1000 to "5m", 10L * 60 * 1000 to "10m", 30L * 60 * 1000 to "30m", 60L * 60 * 1000 to "1h", 120L * 60 * 1000 to "2h", 240L * 60 * 1000 to "4h")
                presets.forEach { (ms, label) ->
                    val isSelected = userConfig.defaultDurationMs == ms
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                            .border(1.dp, if (isSelected) accentColor else borderColor, RoundedCornerShape(16.dp))
                            .clickable { onDurationChange(ms) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(label, color = if (isSelected) accentColor else Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        // Fun Budget Slider
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fun App Budget", color = Color.White, fontSize = 14.sp)
                Text("${userConfig.defaultFunBudgetPercent}%", color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = userConfig.defaultFunBudgetPercent.toFloat(),
                onValueChange = { onFunBudgetChange(it.toInt()) },
                valueRange = 0f..15f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = borderColor
                )
            )
            val funBudgetMs = userConfig.defaultDurationMs * userConfig.defaultFunBudgetPercent / 100
            val totalSeconds = funBudgetMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val budgetStr = when {
                minutes == 0L -> "$seconds seconds"
                seconds == 0L -> if (minutes == 1L) "1 minute" else "$minutes minutes"
                else -> if (minutes == 1L) "1 minute $seconds seconds" else "$minutes minutes $seconds seconds"
            }
            Text("$budgetStr for fun apps", color = Color(0xFF8A8A8A), fontSize = 12.sp)
        }
    }
}

@Composable
fun AppCategorySection(
    title: String,
    apps: List<AppConfigEntity>,
    color: Color,
    surfaceColor: Color,
    borderColor: Color,
    onAddClick: () -> Unit,
    onRemove: (AppConfigEntity) -> Unit,
    isSystem: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .animateContentSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(apps.size.toString(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (!expanded && apps.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        apps.take(5).forEach { app ->
                            AppIcon(app.packageName, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)))
                        }
                        if (apps.size > 5) {
                            Text("...", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                        }
                    }
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White
            )
        }
        
        if (expanded) {
            if (isSystem) {
                Text(
                    "Always present in sessions.",
                    color = Color(0xFF8A8A8A),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                apps.forEach { app ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        positionalThreshold = { totalDistance -> totalDistance * 0.75f }
                    )

                    LaunchedEffect(dismissState.settledValue) {
                        if (dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
                            onRemove(app)
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Red.copy(alpha = 0.5f))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.White)
                            }
                        },
                        enableDismissFromStartToEnd = false
                    ) {
                        AppRowItem(
                            app = app,
                            surfaceColor = surfaceColor,
                            borderColor = borderColor
                        )
                    }
                }
                
                // Add app button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAddClick() }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ Add app", color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun AppRowItem(
    app: AppConfigEntity,
    surfaceColor: Color,
    borderColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.packageName, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.appLabel, color = Color.White, fontSize = 16.sp)
            Text(app.packageName, color = Color(0xFF8A8A8A), fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    installedApps: List<InstalledApp>,
    targetCategory: AppCategory,
    onDismiss: () -> Unit,
    onToggleApp: (InstalledApp, AppCategory, Boolean) -> Unit,
    assignedPackages: Set<String>,
    surfaceColor: Color,
    accentColor: Color
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredApps = remember(searchQuery, installedApps) {
            if (searchQuery.isNotBlank()) {
                installedApps.filter { it.appLabel.contains(searchQuery, ignoreCase = true) }
            } else {
                installedApps
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Assign App to ${targetCategory.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            
            if (installedApps.size > 9) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...", color = Color(0xFF8A8A8A)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color(0xFF2A2A2A)
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(filteredApps) { app ->
                    val isAssigned = app.packageName in assignedPackages
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleApp(app, targetCategory, isAssigned) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(app.packageName, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appLabel, color = Color.White, fontSize = 16.sp)
                            Text(app.packageName, color = Color(0xFF8A8A8A), fontSize = 12.sp)
                        }
                        
                        Box {
                            if (isAssigned) {
                                Icon(Icons.Default.Check, contentDescription = "Assigned", tint = accentColor)
                            } else {
                                Text("Assign", color = accentColor, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 100
                val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 100
                val androidBitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(androidBitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap = androidBitmap.asImageBitmap()
            } catch (e: Exception) {
            }
        }
    }

    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier.background(Color.Gray))
    }
}
