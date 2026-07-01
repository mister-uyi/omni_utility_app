package com.omniutility.feature.finance.data.ai

import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.content
import com.omniutility.feature.finance.platform.AICoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedTransaction(
    val amount: Double,
    val vendor: String,
    val type: String, // CR or DR
    val category: String
)

data class ParsedSearchFilters(
    val vendor: String?,
    val category: String?,
    val type: String?
)

@Singleton
class OfflineAIEngine @Inject constructor(
    private val aiCoreManager: AICoreManager
) {
    suspend fun parseTransaction(rawNarration: String): ParsedTransaction? = withContext(Dispatchers.Default) {
        val model = aiCoreManager.getModel()
        if (model == null) {
            return@withContext parseTransactionMock(rawNarration)
        }
        
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
        val model = aiCoreManager.getModel()
        if (model == null) {
            return@withContext parseSearchQueryMock(userQuery)
        }

        val prompt = """
            You are an offline query parser. Convert the user's financial search query into filter parameters. Output only the keys and values as specified. Do not output any other text.

            Categories: Food & Dining, Shopping, Groceries, Utilities & Bills, Transport & Travel, Entertainment, Income & Salary, Others.

            Query: "$userQuery"

            Output Format:
            VENDOR: <merchant name to search for, or NONE>
            CATEGORY: <one of the Categories, or NONE>
            TYPE: <CR for credit, DR for debit, or NONE>
        """.trimIndent()

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
        val model = aiCoreManager.getModel()
        if (model == null) {
            return@withContext generateInsightsMock(transactionsSummary, goalsSummary)
        }

        val prompt = """
            You are a private offline finance advisor. Analyze the user's transactions and active goals, then provide 3 concise bullet-point insights. 
            Identify patterns, monthly forecasts, or anomalies. Do not use markdown styling other than bullets.

            Transactions:
            $transactionsSummary

            Active Goals:
            $goalsSummary
        """.trimIndent()

        try {
            val response = model.generateContent(content { text(prompt) })
            response.text ?: "No insights generated."
        } catch (e: Exception) {
            "Error generating insights: ${e.message}"
        }
    }

    suspend fun generateGoalAdvice(goalText: String, recentTransactionsSummary: String): String = withContext(Dispatchers.Default) {
        val model = aiCoreManager.getModel()
        if (model == null) {
            return@withContext generateGoalAdviceMock(goalText)
        }

        val prompt = """
            You are a private offline finance advisor. Provide a concise, actionable recommendation (max 2 sentences) on how the user can adjust their spend patterns to achieve this goal: "$goalText".

            Recent spend patterns:
            $recentTransactionsSummary
        """.trimIndent()

        try {
            val response = model.generateContent(content { text(prompt) })
            response.text ?: "No advice generated."
        } catch (e: Exception) {
            "Error generating advice: ${e.message}"
        }
    }

    suspend fun parseTransactionChunk(textChunk: String): List<ParsedTransaction> = withContext(Dispatchers.Default) {
        val model = aiCoreManager.getModel()
        if (model == null) {
            return@withContext parseTransactionChunkMock(textChunk)
        }

        val prompt = """
            You are an offline finance statement parser. Convert the raw bank statement lines into a valid JSON array of transaction objects.
            Each object must contain keys: "amount" (double), "vendor" (string), "type" ("CR" for deposit, "DR" for debit), "category" (one of the Categories below).
            Do not output any explanation or markdown formatting, just the raw JSON.

            Categories: Food & Dining, Shopping, Groceries, Utilities & Bills, Transport & Travel, Entertainment, Income & Salary, Others.

            Statement Lines:
            $textChunk
        """.trimIndent()

        try {
            val response = model.generateContent(content { text(prompt) })
            val text = response.text ?: return@withContext emptyList()
            parseJsonTransactions(text)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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
                if (amount > 0.0) {
                    list.add(ParsedTransaction(amount, vendor, type, category))
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
        val amountRegex = """(?i)(?:USD|NGN|EUR|₦|\$)\s*([\d,]+(?:\.\d{2})?)""".toRegex()
        val match = amountRegex.find(rawNarration) ?: """([\d]+(?:\.\d{2})?)""".toRegex().find(rawNarration)
        val amount = match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 15.0

        val lower = rawNarration.lowercase()
        val type = if (lower.contains("credit") || lower.contains("deposit") || lower.contains("salary") || lower.contains("refund")) "CR" else "DR"

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

        val vendor = rawNarration.split(" ")
            .filter { it.length > 2 && !it.contains(Regex("[0-9]")) && !it.contains(":") }
            .take(2).joinToString(" ")
            .ifEmpty { "Unknown Merchant" }

        return ParsedTransaction(amount, vendor, type, category)
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
}
