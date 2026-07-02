package com.omniutility.feature.finance.data.ai

import android.content.Context
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.content
import com.omniutility.feature.finance.platform.AICoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedTransaction(
    val amount: Double,
    val vendor: String,
    val type: String, // CR or DR
    val category: String,
    val dateStr: String? = null
)

data class ParsedSearchFilters(
    val vendor: String?,
    val category: String?,
    val type: String?
)

@Singleton
class OfflineAIEngine @Inject constructor(
    private val aiCoreManager: AICoreManager,
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences("finance_prefs", Context.MODE_PRIVATE)
    }

    fun getApiKey(): String {
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key).apply()
        aiCoreManager.notifyApiKeyUpdated()
    }
    fun getBasePrompt(): String {
        return prefs.getString("gemini_base_prompt", "") ?: ""
    }

    fun saveBasePrompt(prompt: String) {
        prefs.edit().putString("gemini_base_prompt", prompt).apply()
    }

    private suspend fun generateCloudContent(prompt: String): String? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) return@withContext null
        
        var attempt = 0
        val maxAttempts = 3
        while (attempt < maxAttempts) {
            try {
                val url = java.net.URL("https://generativelanguage.googleapis.com/v1/models/gemini-3.1-flash-lite:generateContent?key=${java.net.URLEncoder.encode(apiKey, "UTF-8")}")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                
                val escapedPrompt = org.json.JSONObject.quote(prompt)
                val jsonBody = """
                    {
                      "contents": [
                        {
                          "parts": [
                            {
                              "text": ${escapedPrompt}
                            }
                          ]
                        }
                      ]
                    }
                """.trimIndent()
                
                conn.outputStream.use { os ->
                    os.write(jsonBody.toByteArray(Charsets.UTF_8))
                }
                
                val code = conn.responseCode
                if (code == 200) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = org.json.JSONObject(responseText)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val contentObj = firstCandidate.optJSONObject("content")
                        if (contentObj != null) {
                            val parts = contentObj.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                return@withContext parts.getJSONObject(0).optString("text")
                            }
                        }
                    }
                    return@withContext null
                } else if (code == 429) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    android.util.Log.w("OfflineAIEngine", "Rate limited (429). Attempt ${attempt + 1} of $maxAttempts. Body: $err")
                    
                    var sleepMs = 5000L
                    try {
                        if (err != null) {
                            val errJson = org.json.JSONObject(err)
                            val errorObj = errJson.optJSONObject("error")
                            if (errorObj != null) {
                                val details = errorObj.optJSONArray("details")
                                if (details != null) {
                                    for (i in 0 until details.length()) {
                                        val detail = details.getJSONObject(i)
                                        val retryInfo = detail.optJSONObject("retryInfo")
                                        if (retryInfo != null) {
                                            val delayStr = retryInfo.optString("retryDelay") // e.g. "24s" or "24.6s"
                                            if (delayStr.endsWith("s")) {
                                                val secsStr = delayStr.dropLast(1)
                                                val secs = secsStr.toDoubleOrNull() ?: 5.0
                                                sleepMs = (secs * 1000).toLong()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    android.util.Log.i("OfflineAIEngine", "Sleeping for ${sleepMs}ms before retry...")
                    kotlinx.coroutines.delay(sleepMs)
                    attempt++
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    android.util.Log.e("OfflineAIEngine", "Cloud API Error code: $code, body: $err")
                    return@withContext null
                }
            } catch (e: Exception) {
                android.util.Log.e("OfflineAIEngine", "Cloud API invocation failed on attempt ${attempt + 1}", e)
                kotlinx.coroutines.delay(2000)
                attempt++
            }
        }
        null
    }

    suspend fun parseTransaction(rawNarration: String): ParsedTransaction? = withContext(Dispatchers.Default) {
        val prompt = """
            You are an offline transaction parser. Parse this transaction notification and output the details in the exact format shown below. Do not output any other text.

            Categories: Food & Dining, Shopping, Groceries, Utilities & Bills, Transport & Travel, Entertainment, Income & Salary, Others.

            Raw Notification: "$rawNarration"

            Output Format:
            AMOUNT: <value as double, e.g. 45.00>
            VENDOR: <cleaned brand/merchant name, e.g. Starbucks>
            TYPE: <CR for credit/deposit, DR for debit/spend>
            CATEGORY: <one of the Categories listed above>
        """.trimIndent()

        val cloudResponse = generateCloudContent(prompt)
        if (cloudResponse != null) {
            return@withContext parseTransactionResponse(cloudResponse)
        }

        val model = aiCoreManager.getModel()
        if (model == null) {
            return@withContext parseTransactionMock(rawNarration)
        }

        try {
            val response = model.generateContent(content { text(prompt) })
            val text = response.text ?: return@withContext null
            parseTransactionResponse(text)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun parseSearchQuery(userQuery: String): ParsedSearchFilters? = withContext(Dispatchers.Default) {
        val prompt = """
            You are an offline query parser. Convert the user's financial search query into filter parameters. Output only the keys and values as specified. Do not output any other text.

            Categories: Food & Dining, Shopping, Groceries, Utilities & Bills, Transport & Travel, Entertainment, Income & Salary, Others.

            Query: "$userQuery"

            Output Format:
            VENDOR: <merchant name to search for, or NONE>
            CATEGORY: <one of the Categories, or NONE>
            TYPE: <CR for credit, DR for debit, or NONE>
        """.trimIndent()

        val cloudResponse = generateCloudContent(prompt)
        if (cloudResponse != null) {
            return@withContext parseSearchResponse(cloudResponse)
        }

        val model = aiCoreManager.getModel()
        if (model == null) {
            return@withContext parseSearchQueryMock(userQuery)
        }

        try {
            val response = model.generateContent(content { text(prompt) })
            val text = response.text ?: return@withContext null
            parseSearchResponse(text)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun generateInsights(transactionsSummary: String, goalsSummary: String): String = withContext(Dispatchers.Default) {
        val prompt = """
            You are a private offline finance advisor. Analyze the user's transactions and active goals, then provide 3 concise bullet-point insights. 
            Identify patterns, monthly forecasts, or anomalies. Do not use markdown styling other than bullets.

            Transactions:
            $transactionsSummary

            Active Goals:
            $goalsSummary
        """.trimIndent()

        val cloudResponse = generateCloudContent(prompt)
        if (cloudResponse != null) {
            return@withContext cloudResponse
        }

        val model = aiCoreManager.getModel()
        if (model == null) {
            return@withContext generateInsightsMock(transactionsSummary, goalsSummary)
        }

        try {
            val response = model.generateContent(content { text(prompt) })
            response.text ?: "No insights generated."
        } catch (e: Exception) {
            "Error generating insights: ${e.message}"
        }
    }

    suspend fun generateGoalAdvice(goalText: String, recentTransactionsSummary: String): String = withContext(Dispatchers.Default) {
        val prompt = """
            You are a private offline finance advisor. Provide a concise, actionable recommendation (max 2 sentences) on how the user can adjust their spend patterns to achieve this goal: "$goalText".

            Recent spend patterns:
            $recentTransactionsSummary
        """.trimIndent()

        val cloudResponse = generateCloudContent(prompt)
        if (cloudResponse != null) {
            return@withContext cloudResponse
        }

        val model = aiCoreManager.getModel()
        if (model == null) {
            return@withContext generateGoalAdviceMock(goalText)
        }

        try {
            val response = model.generateContent(content { text(prompt) })
            response.text ?: "No advice generated."
        } catch (e: Exception) {
            "Error generating advice: ${e.message}"
        }
    }

    suspend fun parseTransactionChunk(textChunk: String): List<ParsedTransaction> = withContext(Dispatchers.Default) {
        val prompt = """
            You are an offline finance statement parser. Convert the raw bank statement lines into a valid JSON array of transaction objects.
            Each object must contain keys: "amount" (double), "vendor" (string), "type" ("CR" for deposit, "DR" for debit), "category" (one of the Categories below), "date" (string in format DD/MM/YYYY or DD/MM/YY as extracted from the line).
            Do not output any explanation or markdown formatting, just the raw JSON.

            Categories: Food & Dining, Shopping, Groceries, Utilities & Bills, Transport & Travel, Entertainment, Income & Salary, Others.

            Statement Lines:
            $textChunk
        """.trimIndent()

        val cloudResponse = generateCloudContent(prompt)
        if (cloudResponse != null) {
            return@withContext parseJsonTransactions(cloudResponse)
        }

        val model = aiCoreManager.getModel()
        if (model == null) {
            return@withContext parseTransactionChunkMock(textChunk)
        }

        try {
            val response = model.generateContent(content { text(prompt) })
            val text = response.text ?: return@withContext emptyList()
            parseJsonTransactions(text)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun generateChatReply(prompt: String, categoryContext: String?): String = withContext(Dispatchers.Default) {
        val fullPrompt = if (categoryContext != null) {
            "Category Context: $categoryContext\nUser Query: $prompt"
        } else {
            prompt
        }

        val cloudResponse = generateCloudContent(fullPrompt)
        if (cloudResponse != null) {
            return@withContext cloudResponse
        }

        val model = aiCoreManager.getModel()
        if (model == null) {
            return@withContext generateChatReplyMock(prompt, categoryContext)
        }

        try {
            val response = model.generateContent(content { text(prompt) })
            response.text ?: "No response generated."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun parseJsonTransactions(jsonText: String): List<ParsedTransaction> {
        val list = mutableListOf<ParsedTransaction>()
        try {
            val cleaned = jsonText.replace("```json", "").replace("```", "").trim()
            val array = org.json.JSONArray(cleaned)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val amount = obj.optDouble("amount", 0.0)
                val vendor = obj.optString("vendor", "Unknown")
                val type = if (obj.optString("type", "DR").uppercase() == "CR") "CR" else "DR"
                val category = obj.optString("category", "Others")
                val date = if (obj.has("date")) obj.optString("date") else null
                if (amount > 0.0) {
                    list.add(ParsedTransaction(amount, vendor, type, category, date))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun parseTransactionResponse(text: String): ParsedTransaction? {
        val lines = text.split("\n")
        var amount = 0.0
        var vendor = "Unknown"
        var type = "DR"
        var category = "Others"

        for (line in lines) {
            val cleaned = line.trim()
            when {
                cleaned.startsWith("AMOUNT:", ignoreCase = true) -> {
                    amount = cleaned.substringAfter("AMOUNT:").trim().toDoubleOrNull() ?: 0.0
                }
                cleaned.startsWith("VENDOR:", ignoreCase = true) -> {
                    vendor = cleaned.substringAfter("VENDOR:").trim()
                }
                cleaned.startsWith("TYPE:", ignoreCase = true) -> {
                    type = cleaned.substringAfter("TYPE:").trim().uppercase()
                }
                cleaned.startsWith("CATEGORY:", ignoreCase = true) -> {
                    category = cleaned.substringAfter("CATEGORY:").trim()
                }
            }
        }
        return if (amount > 0.0) ParsedTransaction(amount, vendor, type, category) else null
    }

    private fun parseSearchResponse(text: String): ParsedSearchFilters? {
        val lines = text.split("\n")
        var vendor: String? = null
        var category: String? = null
        var type: String? = null

        for (line in lines) {
            val cleaned = line.trim()
            when {
                cleaned.startsWith("VENDOR:", ignoreCase = true) -> {
                    val v = cleaned.substringAfter("VENDOR:").trim()
                    if (v.uppercase() != "NONE") vendor = v
                }
                cleaned.startsWith("CATEGORY:", ignoreCase = true) -> {
                    val c = cleaned.substringAfter("CATEGORY:").trim()
                    if (c.uppercase() != "NONE") category = c
                }
                cleaned.startsWith("TYPE:", ignoreCase = true) -> {
                    val t = cleaned.substringAfter("TYPE:").trim()
                    if (t.uppercase() != "NONE") type = t
                }
            }
        }
        return ParsedSearchFilters(vendor, category, type)
    }

    // --- Mock Fallbacks ---

    private fun parseTransactionMock(rawNarration: String): ParsedTransaction {
        val dateRegex = """\b\d{2}/\d{2}/\d{2,4}\b""".toRegex()
        val timeRegex = """\b\d{2}:\d{2}(?::\d{2})?\b""".toRegex()

        val dateStr = dateRegex.find(rawNarration)?.value
        
        // Strip date & time to prevent amount matcher from matching date/time digits!
        var cleanNarration = dateRegex.replace(rawNarration, "")
        cleanNarration = timeRegex.replace(cleanNarration, "")

        val amountRegex = """(?i)(?:USD|NGN|EUR|\u20A6|\$)\s*([\d,]+(?:\.\d{2})?)""".toRegex()
        val decimalRegex = """\b([\d,]+\.\d{2})\b""".toRegex()
        val match = amountRegex.find(cleanNarration) ?: decimalRegex.find(cleanNarration) ?: """([\d,]+)""".toRegex().find(cleanNarration)
        val amount = match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 15.0

        val lower = rawNarration.lowercase()
        val isCredit = lower.contains("salary") || 
                       lower.contains("deposit") || 
                       lower.contains("received") || 
                       lower.contains("refund") || 
                       lower.contains("credit") || 
                       (lower.contains("interest") && !lower.contains("interest application"))
        val type = if (isCredit) "CR" else "DR"

        val category = when {
            lower.contains("starbucks") || lower.contains("restaurant") || lower.contains("food") || lower.contains("dining") || lower.contains("uber eats") || lower.contains("kfc") -> "Food & Dining"
            lower.contains("amazon") || lower.contains("shopping") || lower.contains("nike") || lower.contains("mall") || lower.contains("store") -> "Shopping"
            lower.contains("walmart") || lower.contains("groceries") || lower.contains("supermarket") || lower.contains("grocery") || lower.contains("shoprite") -> "Groceries"
            lower.contains("netflix") || lower.contains("spotify") || lower.contains("steam") || lower.contains("entertainment") || lower.contains("cinema") || lower.contains("game") -> "Entertainment"
            lower.contains("uber") || lower.contains("bolt") || lower.contains("transport") || lower.contains("travel") || lower.contains("flight") || lower.contains("taxi") -> "Transport & Travel"
            lower.contains("salary") || lower.contains("payroll") || lower.contains("income") || lower.contains("interest") -> "Income & Salary"
            lower.contains("electricity") || lower.contains("water") || lower.contains("bill") || lower.contains("utilities") || lower.contains("rent") -> "Utilities & Bills"
            else -> "Others"
        }

        val ignoreWords = setOf(
            "local", "funds", "transfer", "outward", "inward", "spend", "save", "sent",
            "from", "received", "to", "charge", "charges", "vat", "stamp", "duty",
            "interest", "application", "overdraft", "loan", "statement", "balance"
        )
        val vendorTokens = cleanNarration.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { token ->
                token.length > 2 &&
                !token.contains(Regex("[0-9]")) &&
                !token.contains(":") &&
                !token.contains("\u20A6") &&
                !token.contains("$") &&
                !ignoreWords.contains(token.lowercase())
            }
        val vendor = vendorTokens.take(2).joinToString(" ")
            .ifEmpty { "Unknown Merchant" }

        return ParsedTransaction(amount, vendor, type, category, dateStr)
    }

    private fun parseSearchQueryMock(userQuery: String): ParsedSearchFilters {
        val lower = userQuery.lowercase()
        val category = when {
            lower.contains("food") || lower.contains("dining") || lower.contains("restaurant") -> "Food & Dining"
            lower.contains("shopping") -> "Shopping"
            lower.contains("groceries") || lower.contains("grocery") -> "Groceries"
            lower.contains("bills") || lower.contains("rent") -> "Utilities & Bills"
            lower.contains("travel") || lower.contains("transit") -> "Transport & Travel"
            lower.contains("entertainment") || lower.contains("games") -> "Entertainment"
            lower.contains("salary") || lower.contains("income") -> "Income & Salary"
            else -> null
        }
        val type = when {
            lower.contains("credit") || lower.contains("deposit") || lower.contains("received") -> "CR"
            lower.contains("debit") || lower.contains("spend") || lower.contains("sent") -> "DR"
            else -> null
        }
        val vendor = userQuery.split(" ").firstOrNull { it.length > 3 && !it.contains(Regex("[0-9]")) }
        return ParsedSearchFilters(vendor, category, type)
    }

    private fun generateInsightsMock(transactionsSummary: String, goalsSummary: String): String {
        return """
            • Spend Heuristics: Your overall spend speed is stable. Food & Dining and Groceries represent your main outflows.
            • Budget Advice: Try setting caps on non-essential Shopping to increase your month-end delta.
            • Local safe engine mode is active. Secure wallets are encrypted via SQLCipher on-device.
        """.trimIndent()
    }

    private fun generateGoalAdviceMock(goalText: String): String {
        val lower = goalText.lowercase()
        return when {
            lower.contains("rent") -> "Prioritize setting aside a fixed portion of your Income & Salary immediately on pay day. Consider reducing Transport & Travel expenses."
            lower.contains("save") || lower.contains("savings") -> "Review your recurring subscriptions under Entertainment. Setting up automated vault caps will accelerate this goal."
            lower.contains("car") || lower.contains("vehicle") -> "Saving for a car requires consistent ledger delta. Try cutting down Food & Dining spend by cooking at home more."
            else -> "Review your current monthly expenses category allocations. Small cuts in non-essential Shopping can significantly help you reach this target."
        }
    }

    private fun parseTransactionChunkMock(textChunk: String): List<ParsedTransaction> {
        return textChunk.split("\n").filter { it.trim().isNotEmpty() }.map { line ->
            parseTransactionMock(line)
        }
    }

    private fun generateChatReplyMock(prompt: String, categoryContext: String?): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") -> {
                "Hello! I am your safe offline financial assistant. Running in local fallback mode. How can I help you analyze your transactions today?"
            }
            lower.contains("spend") || lower.contains("spent") || lower.contains("most") || lower.contains("highest") -> {
                "Based on local heuristics, your highest spending category recently has been ${categoryContext ?: "Food & Dining"}. Try setting a Vault cap to curb outflows."
            }
            lower.contains("save") || lower.contains("saving") || lower.contains("budget") -> {
                "To optimize your savings, review your recurring subscriptions under Entertainment and Bills. Keeping a ledger delta above 15% is recommended."
            }
            else -> {
                "Offline Mock Chat: I analyzed your request about '${if (categoryContext != null) "$categoryContext - " else ""}$prompt'. Try checking your Analytics tab for visual insights."
            }
        }
    }
}
