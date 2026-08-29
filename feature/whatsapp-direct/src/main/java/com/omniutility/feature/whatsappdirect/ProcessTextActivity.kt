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
        val number = extractAndFormatNigerianNumber(rawText)
        if (number != null) {
            openWhatsApp(number)
        } else {
            Toast.makeText(this, "Not a valid Nigerian phone number.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractAndFormatNigerianNumber(text: String): String? {
        // Strip out non-digit and non-plus characters
        val cleanText = text.replace(Regex("[^0-9+]"), "")
        
        if (cleanText.isEmpty()) return null

        // If it already starts with +234
        if (cleanText.startsWith("+234") && cleanText.length >= 13 && cleanText.length <= 14) {
            return cleanText.removePrefix("+")
        }
        
        // If it starts with 234
        if (cleanText.startsWith("234") && cleanText.length >= 12 && cleanText.length <= 13) {
            return cleanText
        }

        // If it starts with 0 and is 11 digits long (e.g. 08012345678)
        if (cleanText.startsWith("0") && cleanText.length == 11) {
            return "234" + cleanText.substring(1)
        }
        
        // If it's a 10 digit number without the leading 0 (e.g. 8012345678)
        if (cleanText.length == 10 && (cleanText.startsWith("8") || cleanText.startsWith("7") || cleanText.startsWith("9"))) {
            return "234" + cleanText
        }

        return null
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
