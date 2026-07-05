package com.omniutility.feature.ownyourtime.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.omniutility.feature.ownyourtime.data.db.entity.TaskEntity
import com.omniutility.feature.ownyourtime.data.db.entity.TaskType
import com.omniutility.feature.ownyourtime.data.repository.OwnYourTimeRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class ShareTargetActivity : ComponentActivity() {

    @Inject
    lateinit var repository: OwnYourTimeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make the window background transparent to match the dialog theme overlay
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        val sharedText = handleIntent(intent)
        if (sharedText.isNullOrBlank()) {
            Toast.makeText(this, "No content shared", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val url = extractUrl(sharedText)
        val initialType = when {
            url != null && isYouTubeUrl(url) -> TaskType.YOUTUBE_LINK
            url != null -> TaskType.WEB_LINK
            else -> TaskType.TEXT
        }
        val initialTitle = extractTitle(sharedText, url)

        setContent {
            MaterialTheme {
                ShareTargetScreen(
                    initialTitle = initialTitle,
                    initialUrl = url ?: "",
                    initialType = initialType,
                    onSave = { title, taskType, taskUrl ->
                        saveTaskAndFinish(title, taskType, taskUrl)
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }

    private fun handleIntent(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)
                } else null
            }
            Intent.ACTION_PROCESS_TEXT -> {
                if (intent.type == "text/plain") {
                    intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                } else null
            }
            else -> null
        }
    }

    private fun extractUrl(text: String): String? {
        val regex = "https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?".toRegex()
        val match = regex.find(text)
        return match?.value
    }

    private fun isYouTubeUrl(url: String): Boolean {
        return url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)
    }

    private fun extractTitle(text: String, url: String?): String {
        val rawTitle = if (url == null) text.trim() else {
            val cleaned = text.replace(url, "").trim()
            if (cleaned.isNotEmpty()) cleaned else url
        }
        return if (rawTitle.length > 100) rawTitle.take(97) + "..." else rawTitle
    }

    private fun saveTaskAndFinish(title: String, type: TaskType, url: String?) {
        lifecycleScope.launch {
            val task = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                type = type,
                url = url?.takeIf { it.isNotBlank() }?.trim(),
                createdAt = System.currentTimeMillis(),
                isArchived = false
            )
            repository.saveTask(task)
            Toast.makeText(this@ShareTargetActivity, "Shared task added!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTargetScreen(
    initialTitle: String,
    initialUrl: String,
    initialType: TaskType,
    onSave: (String, TaskType, String?) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var url by remember { mutableStateOf(initialUrl) }
    var isFetchingTitle by remember { mutableStateOf(false) }

    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank() && 
            (initialTitle == initialUrl || initialTitle.isBlank() || 
             initialTitle.startsWith("http://", ignoreCase = true) || 
             initialTitle.startsWith("https://", ignoreCase = true))
        ) {
            isFetchingTitle = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val u = java.net.URL(initialUrl)
                    val connection = u.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    val inputStream = connection.inputStream
                    val scanner = java.util.Scanner(inputStream).useDelimiter("\\A")
                    if (scanner.hasNext()) {
                        val html = scanner.next()
                        val matcher = java.util.regex.Pattern.compile("<title>(.*?)</title>", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html)
                        if (matcher.find()) {
                            val fetchedTitle = matcher.group(1)?.trim()
                            if (!fetchedTitle.isNullOrBlank()) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    if (title == initialTitle) {
                                        title = fetchedTitle
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isFetchingTitle = false
                }
            }
        }
    }
    
    val selectedType = remember(url) {
        val trimmedUrl = url.trim()
        when {
            trimmedUrl.isBlank() -> TaskType.TEXT
            trimmedUrl.contains("youtube.com", ignoreCase = true) || trimmedUrl.contains("youtu.be", ignoreCase = true) -> TaskType.YOUTUBE_LINK
            else -> TaskType.WEB_LINK
        }
    }

    val accentColor = Color(0xFFF5A623)
    val surfaceColor = Color(0xFF1A1A1A)
    val borderColor = Color(0xFF2A2A2A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCancel() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* prevent click propagation */ },
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Add to My Tasks",
                    color = accentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isFetchingTitle) "Fetching Title..." else "Task Title", color = Color(0xFF8A8A8A)) },
                    trailingIcon = {
                        if (isFetchingTitle) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = accentColor,
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = borderColor,
                        cursorColor = accentColor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                if (selectedType != TaskType.TEXT) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Link URL", color = Color(0xFF8A8A8A)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = borderColor,
                            cursorColor = accentColor
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(title, selectedType, url.takeIf { selectedType != TaskType.TEXT })
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(8.dp),
                        enabled = title.isNotBlank()
                    ) {
                        Text("Add Task", color = Color(0xFF0D0D0D), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
