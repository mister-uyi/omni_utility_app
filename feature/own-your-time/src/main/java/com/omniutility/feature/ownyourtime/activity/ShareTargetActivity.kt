package com.omniutility.feature.ownyourtime.activity

import android.content.Intent
import android.os.Bundle
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
import kotlinx.coroutines.delay
import java.util.UUID
import javax.inject.Inject
import com.omniutility.core.ui.HUDPill
import com.omniutility.core.ui.HUDPillMessage
import com.omniutility.core.ui.HUDPillType

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
            setContent {
                var hudMessage by remember { mutableStateOf<HUDPillMessage?>(null) }
                LaunchedEffect(Unit) {
                    hudMessage = HUDPillMessage("No content shared", HUDPillType.ERROR)
                    delay(1500)
                    finish()
                }
                MaterialTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
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
            var hudMessage by remember { mutableStateOf<HUDPillMessage?>(null) }

            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ShareTargetScreen(
                        initialTitle = initialTitle,
                        initialUrl = url ?: "",
                        initialType = initialType,
                        onSave = { title, taskType, taskUrl ->
                            hudMessage = HUDPillMessage("Shared task added!", HUDPillType.SUCCESS)
                            saveTaskAndFinish(title, taskType, taskUrl)
                        },
                        onCancel = {
                            finish()
                        }
                    )

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
            delay(1500) // Delay 1.5s to let the user see the HUDPill!
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
    val coroutineScope = rememberCoroutineScope()
    var input by remember { mutableStateOf(if (initialUrl.isNotBlank()) initialUrl else initialTitle) }
    var isFetching by remember { mutableStateOf(false) }

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
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Task (Text or URL)", color = Color(0xFF8A8A8A)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = borderColor,
                        cursorColor = accentColor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isFetching
                )

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
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isFetching
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val text = input.trim()
                            if (text.isNotBlank()) {
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
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(8.dp),
                        enabled = input.isNotBlank() && !isFetching
                    ) {
                        Text(
                            text = if (isFetching) "Fetching title..." else "Add Task",
                            color = Color(0xFF0D0D0D),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
