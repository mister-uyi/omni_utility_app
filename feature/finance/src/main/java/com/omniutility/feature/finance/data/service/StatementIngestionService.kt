package com.omniutility.feature.finance.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.omniutility.feature.finance.data.ai.ParsedTransaction
import com.omniutility.feature.finance.data.db.TransactionRecordEntity
import com.omniutility.feature.finance.data.repository.FinanceRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class StatementIngestionService : Service() {

    @Inject
    lateinit var repository: FinanceRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val notificationId = 1001
    private val channelId = "statement_ingestion_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uriString = intent?.getStringExtra("file_uri")
        val accountId = intent?.getStringExtra("account_id")

        if (uriString == null || accountId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val uri = Uri.parse(uriString)
        val notification = createNotification("Initializing statement processing...", 0)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(notificationId, notification)
        }

        _isProcessing.value = true

        serviceScope.launch {
            try {
                processFile(uri, accountId)
            } catch (e: Exception) {
                e.printStackTrace()
                updateNotification("Processing failed: ${e.message}", 0)
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun getDisplayFilename(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "statement"
    }

    private suspend fun processFile(uri: Uri, accountId: String) {
        val filename = getDisplayFilename(uri)
        updateNotification("Extracting text from $filename...", 10)

        val isPdf = filename.endsWith(".pdf", ignoreCase = true) ||
                    uri.toString().contains(".pdf", ignoreCase = true) ||
                    contentResolver.getType(uri) == "application/pdf"

        val rawLines = if (isPdf) {
            val pdfLines = extractPdfText(uri)
            reconstructTransactions(pdfLines)
        } else {
            extractCsvText(uri)
        }

        if (rawLines.isEmpty()) {
            updateNotification("No text found in statement.", 100)
            return
        }

        // Clean & Filter noise: only keep rows that contain a transaction date (e.g. 25/05/26 or 25/05/2026)
        val dateRegex = """\b\d{2}/\d{2}/\d{2,4}\b""".toRegex()
        val cleanLines = rawLines.filter { line ->
            line.trim().isNotEmpty() && dateRegex.containsMatchIn(line)
        }

        if (cleanLines.isEmpty()) {
            updateNotification("No transaction rows matched date filter.", 100)
            return
        }

        // Chunk lines to 20 rows
        val chunkSize = 20
        val chunks = cleanLines.chunked(chunkSize)
        val totalChunks = chunks.size

        for ((index, chunk) in chunks.withIndex()) {
            val progress = 10 + ((index.toFloat() / totalChunks) * 80).toInt()
            updateNotification("Analyzing transaction chunk ${index + 1} of $totalChunks...", progress)

            val chunkText = chunk.joinToString("\n")
            repository.addRawTransactionChunk(accountId, chunkText)
        }

        updateNotification("Statement ingested successfully!", 100)
    }

    private fun extractPdfText(uri: Uri): List<String> {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return emptyList()
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            val text = stripper.getText(document)
            document.close()
            text.split("\n")
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun extractCsvText(uri: Uri): List<String> {
        val lines = mutableListOf<String>()
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return emptyList()
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    lines.add(line)
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return lines
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Statement Ingestion Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Private Statement Ingestion")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String, progress: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, createNotification(contentText, progress))
    }

    private fun reconstructTransactions(rawLines: List<String>): List<String> {
        val dateRegex = """\b\d{2}/\d{2}/\d{2,4}\b""".toRegex()
        val timeRegex = """\b\d{2}:\d{2}(?::\d{2})?\b""".toRegex()
        val amountRegex = """(?i)(?:USD|NGN|EUR|\u20A6|\$)\s*[\d,]+(?:\.\d{2})?""".toRegex()
        
        val result = mutableListOf<String>()
        var i = 0
        val n = rawLines.size

        while (i < n) {
            val line = rawLines[i].trim()
            
            if (dateRegex.matches(line)) {
                val dateStr = line
                var timeStr = ""
                var amountStr = ""
                val descriptionParts = mutableListOf<String>()
                var balanceStr = ""
                
                i++
                if (i < n && timeRegex.matches(rawLines[i].trim())) {
                    timeStr = rawLines[i].trim()
                    i++
                }
                
                if (i < n && (amountRegex.matches(rawLines[i].trim()) || rawLines[i].trim().startsWith("\u20A6") || rawLines[i].trim().startsWith("$"))) {
                    amountStr = rawLines[i].trim()
                    i++
                }
                
                while (i < n) {
                    val current = rawLines[i].trim()
                    if (current.isEmpty()) {
                        i++
                        continue
                    }
                    
                    if (dateRegex.matches(current)) {
                        break
                    }
                    
                    if (amountRegex.matches(current) || current.startsWith("\u20A6") || current.startsWith("$") || current.matches("""^[\d,]+\.\d{2}$""".toRegex())) {
                        balanceStr = current
                        i++
                        break
                    }
                    
                    descriptionParts.add(current)
                    i++
                }
                
                val joinedDescription = descriptionParts.joinToString(" ")
                val reconstructedLine = "$dateStr $timeStr $amountStr $joinedDescription balance: $balanceStr"
                result.add(reconstructedLine)
            } else {
                i++
            }
        }
        return result
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        _isProcessing.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val _isProcessing = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isProcessing: kotlinx.coroutines.flow.StateFlow<Boolean> = _isProcessing
    }
}
