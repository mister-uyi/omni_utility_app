package com.omniutility.feature.ownyourtime.service

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.omniutility.feature.ownyourtime.data.db.entity.AppCategory
import com.omniutility.feature.ownyourtime.data.db.entity.PassiveBudgetEntity
import com.omniutility.feature.ownyourtime.data.repository.OwnYourTimeRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Foreground service that passively tracks fun-app usage against a
 * fixed clock-hour budget. Runs 24/7 when enabled.
 *
 * Pauses accumulation whenever [SessionService] is active to avoid
 * double-counting or interfering with the active session mode.
 */
@AndroidEntryPoint
class PassiveTrackingService : Service() {

    @Inject
    lateinit var repository: OwnYourTimeRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var funPackages: Set<String> = emptySet()
    private var lastCheckTime = 0L
    private var funTimeUsedMs = 0L
    private var cycleStartMs = 0L
    private var periodMinutes = 60
    private var budgetPercent = 20
    private var lastPersistTime = 0L

    // ── Lifecycle ───────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(0L, 0L))

        serviceScope.launch {
            // Load fun-app packages
            val funApps = repository.getAppsByCategory(AppCategory.FUN).map { it.packageName }
            val geminiAliases = setOf(
                "com.google.android.googlequicksearchbox",
                "com.google.android.apps.googleassistant"
            )
            val hasGemini = "com.google.android.apps.bard" in funApps
            funPackages = if (hasGemini) funApps.toSet() + geminiAliases else funApps.toSet()

            // Restore persisted budget state
            val budget = repository.getPassiveBudget()
            periodMinutes = budget.periodMinutes
            budgetPercent = budget.budgetPercent

            val now = System.currentTimeMillis()
            val periodMs = periodMinutes * 60_000L
            val alignedCycleStart = alignToPeriodBoundary(now, periodMs)

            if (budget.cycleStartMs == alignedCycleStart) {
                // Same cycle — restore accumulated time
                funTimeUsedMs = budget.funTimeUsedMs
                cycleStartMs = budget.cycleStartMs
            } else {
                // New cycle — reset
                funTimeUsedMs = 0L
                cycleStartMs = alignedCycleStart
            }

            lastCheckTime = now
            lastPersistTime = now

            // Start the polling loop
            startTracking()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Core tracking loop ──────────────────────────────────────────────

    private suspend fun startTracking() {
        while (true) {
            delay(POLL_INTERVAL_MS)
            tick()
        }
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        val periodMs = periodMinutes * 60_000L
        val budgetMs = periodMs * budgetPercent / 100

        // ── Cycle boundary reset ────────────────────────────────────
        val currentCycleBoundary = alignToPeriodBoundary(now, periodMs)
        if (currentCycleBoundary != cycleStartMs) {
            funTimeUsedMs = 0L
            cycleStartMs = currentCycleBoundary
            clearBudgetExhaustedFlag()
            persistState()
        }

        // ── Pause while active session is running ───────────────────
        if (SessionService.isRunning) {
            lastCheckTime = now
            return
        }

        // ── Accumulate fun-app time ─────────────────────────────────
        val elapsed = now - lastCheckTime
        lastCheckTime = now

        if (hasUsageStatsPermission()) {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val topApp = getTopApp(usm)

            val isLauncher = topApp?.contains("launcher", ignoreCase = true) == true
            val isFunApp = topApp != null && topApp in funPackages && !isLauncher

            if (isFunApp) {
                funTimeUsedMs += elapsed
            }
        }

        // ── Check budget exhaustion ─────────────────────────────────
        if (funTimeUsedMs >= budgetMs) {
            setBudgetExhaustedFlag()
        }

        // ── Persist state every PERSIST_INTERVAL_MS ─────────────────
        if (now - lastPersistTime >= PERSIST_INTERVAL_MS) {
            persistState()
            lastPersistTime = now
        }

        // ── Update notification ─────────────────────────────────────
        val remainingMs = maxOf(0L, budgetMs - funTimeUsedMs)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(remainingMs, budgetMs))
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Aligns [timestampMs] to the start of the current period boundary.
     * E.g., for periodMs = 3600000 (1 hour), timestamps are floored to
     * the top of the clock hour.
     */
    private fun alignToPeriodBoundary(timestampMs: Long, periodMs: Long): Long {
        return timestampMs - (timestampMs % periodMs)
    }

    private fun getTopApp(usm: UsageStatsManager): String? {
        val time = System.currentTimeMillis()
        val events = usm.queryEvents(time - 1000 * 60, time + 1000)
        var topApp: String? = null
        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                topApp = event.packageName
            }
        }
        return topApp
    }

    private suspend fun persistState() {
        val current = repository.getPassiveBudget()
        repository.savePassiveBudget(
            current.copy(
                funTimeUsedMs = funTimeUsedMs,
                cycleStartMs = cycleStartMs
            )
        )
    }

    private fun setBudgetExhaustedFlag() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_BUDGET_EXHAUSTED, true)
            .apply()
    }

    private fun clearBudgetExhaustedFlag() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_BUDGET_EXHAUSTED, false)
            .apply()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // ── Notification ────────────────────────────────────────────────────

    private fun buildNotification(
        remainingMs: Long,
        budgetMs: Long
    ): android.app.Notification {
        val remainingSecs = maxOf(0L, remainingMs / 1000)
        val m = remainingSecs / 60
        val s = remainingSecs % 60
        val timeString = String.format("%02d:%02d", m, s)

        val text = if (budgetMs > 0L && remainingMs <= 0L) {
            "Fun-time budget exhausted for this cycle"
        } else {
            "Budget remaining: $timeString"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Passive tracking active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Passive Fun-Time Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "PassiveTrackingChannel"
        const val NOTIFICATION_ID = 2
        private const val POLL_INTERVAL_MS = 500L
        private const val PERSIST_INTERVAL_MS = 5_000L
        const val PREFS_NAME = "passive_tracking_prefs"
        const val PREF_BUDGET_EXHAUSTED = "passive_budget_exhausted"
    }
}
