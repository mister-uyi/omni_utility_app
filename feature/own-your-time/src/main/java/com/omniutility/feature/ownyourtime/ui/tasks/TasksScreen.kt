package com.omniutility.feature.ownyourtime.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import com.omniutility.feature.ownyourtime.data.db.entity.TaskEntity
import com.omniutility.feature.ownyourtime.data.db.entity.TaskType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    modifier: Modifier = Modifier,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val onDeleteTask: (TaskEntity) -> Unit = { task ->
        viewModel.deleteTask(task)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "Task deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreTask(task)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0D0D0D),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    editingTask = null
                    showSheet = true 
                },
                containerColor = Color(0xFFF5A623),
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Task Templates",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF5A623),
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Create reusable task templates for your sessions.",
                fontSize = 14.sp,
                color = Color(0xFF8A8A8A),
                modifier = Modifier.padding(bottom = 16.dp, top = 4.dp)
            )

            if (tasks.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No tasks yet. Tap + to add one.", color = Color(0xFF8A8A8A))
                }
            } else {
                val completedTaskIds by viewModel.completedTaskIds.collectAsState()
                val openTasks = tasks.filter { it.id !in completedTaskIds }
                val completedTasks = tasks.filter { it.id in completedTaskIds }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (openTasks.isNotEmpty()) {
                        item {
                            Text(
                                "Open",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(openTasks, key = { it.id }) { task ->
                            TaskListItem(task = task, onEdit = {
                                editingTask = task
                                showSheet = true
                            }, onDelete = onDeleteTask)
                        }
                    }
                    
                    if (completedTasks.isNotEmpty()) {
                        item {
                            Text(
                                "Completed",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(completedTasks, key = { it.id }) { task ->
                            TaskListItem(task = task, onEdit = {
                                editingTask = task
                                showSheet = true
                            }, onDelete = onDeleteTask)
                        }
                    }
                }
            }
        }

        if (showSheet) {
            TaskBottomSheet(
                task = editingTask,
                onDismiss = { showSheet = false },
                onSave = { title, type, url ->
                    viewModel.saveTask(editingTask?.id, title, type, url)
                    showSheet = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListItem(task: TaskEntity, onEdit: () -> Unit, onDelete: (TaskEntity) -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.75f }
    )

    LaunchedEffect(dismissState.settledValue) {
        if (dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete(task)
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        TaskCard(
            task = task,
            onClick = onEdit
        )
    }
}

@Composable
fun TaskCard(
    task: TaskEntity,
    onClick: () -> Unit
) {
    val icon = when (task.type) {
        TaskType.TEXT -> Icons.AutoMirrored.Filled.List
        TaskType.WEB_LINK -> Icons.Default.Search
        TaskType.YOUTUBE_LINK -> Icons.Default.PlayArrow
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFF5A623),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
                if (!task.url.isNullOrBlank()) {
                    Text(
                        text = task.url,
                        color = Color(0xFF8A8A8A),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskBottomSheet(
    task: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, type: TaskType, url: String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var input by remember { mutableStateOf(if (task?.type == TaskType.TEXT) task.title else task?.url ?: "") }
    var isFetching by remember { mutableStateOf(false) }
    
    val isFormValid = input.isNotBlank() && !isFetching

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (task == null) "New Task" else "Edit Task",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Task (Text or URL)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF5A623),
                    focusedLabelColor = Color(0xFFF5A623)
                )
            )

            Button(
                onClick = { 
                    val text = input.trim()
                    if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
                        val isYoutube = text.contains("youtube.com", ignoreCase = true) || text.contains("youtu.be", ignoreCase = true)
                        val outType = if (isYoutube) TaskType.YOUTUBE_LINK else TaskType.WEB_LINK
                        
                        isFetching = true
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            var pageTitle = text
                            try {
                                val u = java.net.URL(text)
                                val connection = u.openConnection() as java.net.HttpURLConnection
                                connection.connectTimeout = 5000
                                connection.readTimeout = 5000
                                val inputStream = connection.inputStream
                                val scanner = java.util.Scanner(inputStream).useDelimiter("\\A")
                                if (scanner.hasNext()) {
                                    val html = scanner.next()
                                    val matcher = java.util.regex.Pattern.compile("<title>(.*?)</title>", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html)
                                    if (matcher.find()) {
                                        pageTitle = matcher.group(1)?.trim() ?: text
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                isFetching = false
                                onSave(pageTitle, outType, text)
                            }
                        }
                    } else {
                        onSave(text, TaskType.TEXT, null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF5A623),
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF2A2A2A),
                    disabledContentColor = Color(0xFF8A8A8A)
                )
            ) {
                Text(if (isFetching) "Fetching title..." else "Save Task")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
