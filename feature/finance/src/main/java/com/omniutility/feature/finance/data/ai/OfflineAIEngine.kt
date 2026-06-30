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
        val model = aiCoreManager.getModel() ?: return@withContext null
        
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
        val model = aiCoreManager.getModel() ?: return@withContext null

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
        val model = aiCoreManager.getModel() ?: return@withContext "AI engine not ready."

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
        val model = aiCoreManager.getModel() ?: return@withContext "AI engine not ready."

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
        val model = aiCoreManager.getModel() ?: return@withContext emptyList()

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
            val parts = line.split(":", limit = 2)
            if (parts.size < 2) continue
            val key = parts[0].trim().uppercase()
            val value = parts[1].trim()

            when (key) {
                "AMOUNT" -> amount = value.toDoubleOrNull() ?: 0.0
                "VENDOR" -> vendor = value
                "TYPE" -> type = if (value.uppercase() == "CR") "CR" else "DR"
                "CATEGORY" -> category = value
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
            val parts = line.split(":", limit = 2)
            if (parts.size < 2) continue
            val key = parts[0].trim().uppercase()
            val value = parts[1].trim()

            if (value.uppercase() == "NONE" || value.isEmpty()) continue

            when (key) {
                "VENDOR" -> vendor = value
                "CATEGORY" -> category = value
                "TYPE" -> type = if (value.uppercase() == "CR") "CR" else "DR"
            }
        }

        return ParsedSearchFilters(vendor, category, type)
    }
}
