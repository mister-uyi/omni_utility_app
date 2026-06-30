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

    fun checkSupportAndPrepare() {
        _status.value = AICoreStatus.Checking

        // 1. Basic package check to see if AICore is present on the device
        val isPackageInstalled = try {
            context.packageManager.getPackageInfo("com.google.android.aicore", 0)
            true
        } catch (e: Exception) {
            false
        }

        if (!isPackageInstalled) {
            _status.value = AICoreStatus.Unsupported
            return
        }

        try {
            val generationConfig = generationConfig {
                context = this@AICoreManager.context
                temperature = 0.0f // Keep it deterministic for transaction parsing
            }

            val downloadCallback = object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) {
                    _status.value = AICoreStatus.Downloading(0)
                }

                override fun onDownloadProgress(totalBytesDownloaded: Long) {
                    _status.value = AICoreStatus.Downloading(50)
                }

                override fun onDownloadCompleted() {
                    _status.value = AICoreStatus.Ready
                }

                override fun onDownloadFailed(failureStatus: String, e: GenerativeAIException) {
                    _status.value = AICoreStatus.Error("Model update failed: $failureStatus")
                }
            }

            val model = GenerativeModel(
                generationConfig = generationConfig,
                downloadConfig = DownloadConfig(downloadCallback)
            )
            generativeModel = model

            // Launch preparation asynchronously on Main thread
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    model.prepareInferenceEngine()
                    _status.value = AICoreStatus.Ready
                } catch (e: Exception) {
                    android.util.Log.e("AICoreManager", "AICore engine preparation failed", e)
                    val msg = e.message ?: "AICore binding failed"
                    _status.value = AICoreStatus.Error(msg)
                }
            }

        } catch (e: Exception) {
            _status.value = AICoreStatus.Error(e.message ?: "Failed to initialize GenerativeModel")
        }
    }

    fun getModel(): GenerativeModel? {
        return if (_status.value is AICoreStatus.Ready) generativeModel else null
    }
}
