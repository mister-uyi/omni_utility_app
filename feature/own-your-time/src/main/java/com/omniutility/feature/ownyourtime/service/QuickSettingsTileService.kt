package com.omniutility.feature.ownyourtime.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.omniutility.feature.ownyourtime.data.db.entity.SessionEntity
import com.omniutility.feature.ownyourtime.data.repository.OwnYourTimeRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class QuickSettingsTileService : TileService() {

    @Inject
    lateinit var repository: OwnYourTimeRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        serviceScope.launch {
            val sessions = repository.observeRecentSessions(1).firstOrNull()
            val activeSession = sessions?.firstOrNull { it.endedAt == null }
            
            withContext(Dispatchers.Main) {
                val tile = qsTile ?: return@withContext
                if (activeSession != null) {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Session Active"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Focusing"
                    }
                } else {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "Start Session"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Own Your Time"
                    }
                }
                tile.updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val sessions = repository.observeRecentSessions(1).firstOrNull()
            val activeSession = sessions?.firstOrNull { it.endedAt == null }

            if (activeSession != null) {
                // If active, just launch the app to show the active session screen
                launchApp()
            } else {
                // If inactive, start a new session with user config defaults
                val config = repository.getUserConfig()
                val durationMs = config.defaultDurationMs
                val funBudgetPercent = config.defaultFunBudgetPercent
                val funBudgetMs = (durationMs * funBudgetPercent / 100)
                
                val newSession = SessionEntity(
                    id = UUID.randomUUID().toString(),
                    startedAt = System.currentTimeMillis(),
                    plannedDurationMs = durationMs,
                    actualDurationMs = 0L,
                    funBudgetPercent = funBudgetPercent,
                    funBudgetMs = funBudgetMs
                )
                repository.saveSession(newSession)
                
                // Start SessionService
                val serviceIntent = Intent(this@QuickSettingsTileService, SessionService::class.java).apply {
                    putExtra(SessionService.EXTRA_DURATION_MS, durationMs)
                    putExtra(SessionService.EXTRA_SESSION_ID, newSession.id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                
                // Launch the app
                launchApp()
                
                // Refresh tile
                updateTileState()
            }
        }
    }

    private fun launchApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
