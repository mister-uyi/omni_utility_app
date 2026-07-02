package com.omniutility.feature.finance.platform

import android.content.Context
import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.GenerationConfig
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import com.google.ai.edge.aicore.GenerativeAIException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AICoreStatus {
    object Checking : AICoreStatus
    object Ready : AICoreStatus
    data class Downloading(val progressPercent: Int) : AICoreStatus
    data class Error(val message: String) : AICoreStatus
    object Unsupported : AICoreStatus
    data class Fallback(val message: String) : AICoreStatus
    data class CloudActive(val keyPreview: String) : AICoreStatus
}

@Singleton
class AICoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _status = MutableStateFlow<AICoreStatus>(AICoreStatus.Checking)
    val status: StateFlow<AICoreStatus> = _status

    private var generativeModel: GenerativeModel? = null

    init {
        checkSupportAndPrepare()
    }

    fun notifyApiKeyUpdated() {
        checkSupportAndPrepare()
    }

    fun checkSupportAndPrepare() {
        _status.value = AICoreStatus.Checking

        val prefs = context.getSharedPreferences("finance_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        if (apiKey.isNotEmpty()) {
            val preview = if (apiKey.length > 8) apiKey.take(4) + "..." + apiKey.takeLast(4) else "Active"
            _status.value = AICoreStatus.CloudActive(preview)
            return
        }

        android.util.Log.i("AICoreManager", "No Cloud API key found. Starting local AI Core preparation diagnostics...")

        // 1. Basic package check to see if AICore is present on the device
        android.util.Log.i("AICoreManager", "Checking if package com.google.android.aicore is installed...")
        val isPackageInstalled = try {
            context.packageManager.getPackageInfo("com.google.android.aicore", 0)
            android.util.Log.i("AICoreManager", "Package com.google.android.aicore is INSTALLED.")
            true
        } catch (e: Exception) {
            android.util.Log.w("AICoreManager", "Package com.google.android.aicore is NOT installed on this device.")
            false
        }

        if (!isPackageInstalled) {
            android.util.Log.w("AICoreManager", "Device does not support local AICore (Package missing). Status set to Unsupported.")
            _status.value = AICoreStatus.Unsupported
            return
        }

        try {
            android.util.Log.i("AICoreManager", "Initializing GenerativeModel config with local context...")
            val generationConfig = generationConfig {
                context = this@AICoreManager.context
                temperature = 0.0f // Keep it deterministic for transaction parsing
            }

            val downloadCallback = object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) {
                    android.util.Log.i("AICoreManager", "Download Callback: onDownloadStarted. bytesToDownload = $bytesToDownload")
                    _status.value = AICoreStatus.Downloading(0)
                }

                override fun onDownloadProgress(totalBytesDownloaded: Long) {
                    android.util.Log.d("AICoreManager", "Download Callback: onDownloadProgress. totalBytesDownloaded = $totalBytesDownloaded")
                    _status.value = AICoreStatus.Downloading(50)
                }

                override fun onDownloadCompleted() {
                    android.util.Log.i("AICoreManager", "Download Callback: onDownloadCompleted! Model is successfully downloaded locally.")
                    _status.value = AICoreStatus.Ready
                }

                override fun onDownloadFailed(failureStatus: String, e: GenerativeAIException) {
                    android.util.Log.e("AICoreManager", "Download Callback: onDownloadFailed. status = $failureStatus", e)
                    _status.value = AICoreStatus.Error("Model update failed: $failureStatus")
                }
            }

            android.util.Log.i("AICoreManager", "Creating GenerativeModel instance with DownloadCallback listener...")
            val model = GenerativeModel(
                generationConfig = generationConfig,
                downloadConfig = DownloadConfig(downloadCallback)
            )
            generativeModel = model

            // Launch preparation asynchronously on Main thread
            android.util.Log.i("AICoreManager", "Launching prepareInferenceEngine() coroutine...")
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    model.prepareInferenceEngine()
                    android.util.Log.i("AICoreManager", "prepareInferenceEngine() returned successfully. Local model is READY.")
                    _status.value = AICoreStatus.Ready
                } catch (e: Exception) {
                    android.util.Log.e("AICoreManager", "prepareInferenceEngine() failed with exception", e)
                    val msg = e.message ?: "AICore binding failed"
                    if (msg.contains("NOT_AVAILABLE", ignoreCase = true) || msg.contains("feature not found", ignoreCase = true)) {
                        android.util.Log.w("AICoreManager", "Inference engine reported NOT_AVAILABLE / download pending. Transitioning to Fallback.")
                        _status.value = AICoreStatus.Fallback("On-device model download pending. Safe offline fallback engine is active.")
                    } else {
                        _status.value = AICoreStatus.Error(msg)
                    }
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("AICoreManager", "Failed to build GenerativeModel config", e)
            _status.value = AICoreStatus.Error(e.message ?: "Failed to initialize GenerativeModel")
        }
    }

    fun getModel(): GenerativeModel? {
        return if (_status.value is AICoreStatus.Ready) generativeModel else null
    }
}
