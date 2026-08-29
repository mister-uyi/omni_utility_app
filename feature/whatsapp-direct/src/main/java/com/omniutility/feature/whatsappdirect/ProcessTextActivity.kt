package com.omniutility.feature.whatsappdirect

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

class ProcessTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val processText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        val shareText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val text = processText ?: shareText

        if (text != null && text.isNotBlank()) {
            handleText(text)
        } else {
            Toast.makeText(this, "No text selected.", Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    private fun handleText(rawText: String) {
        val number = extractAndFormatNumber(rawText)
        if (number != null) {
            openWhatsApp(number)
        } else {
            Toast.makeText(this, "Not a valid phone number.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractAndFormatNumber(text: String): String? {
        // Strip out non-digit and non-plus characters
        val cleanText = text.replace(Regex("[^0-9+]"), "")
        
        if (cleanText.isEmpty()) return null

        // Remove leading '+' as WhatsApp API expects international format without it
        val numberWithoutPlus = cleanText.removePrefix("+")

        // Basic check to ensure it's somewhat long enough to be a phone number
        if (numberWithoutPlus.length < 5) return null

        return numberWithoutPlus
    }

    private fun openWhatsApp(formattedNumber: String) {
        try {
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.whatsapp")
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback if WhatsApp package is not found or fails
            try {
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "Unable to open WhatsApp.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
