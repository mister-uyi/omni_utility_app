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
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.omniutility.feature.ownyourtime.data.db.entity.AppCategory
import com.omniutility.feature.ownyourtime.data.repository.OwnYourTimeRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class SessionService : Service() {

    @Inject
    lateinit var repository: OwnYourTimeRepository

    private var countDownTimer: CountDownTimer? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sessionId: String? = null
    private var funPackages: Set<String> = emptySet()
    private var allowedPackages: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val durationMs = intent?.getLongExtra(EXTRA_DURATION_MS, 0L) ?: 0L
        sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        
        if (durationMs > 0 && sessionId != null) {
            serviceScope.launch {
                val prod = repository.getAppsByCategory(AppCategory.PRODUCTIVITY).map { it.packageName }
                val sys = repository.getAppsByCategory(AppCategory.SYSTEM).map { it.packageName }
                val funApps = repository.getAppsByCategory(AppCategory.FUN).map { it.packageName }
                funPackages = funApps.toSet()
                allowedPackages = (prod + sys + funApps + packageName + "com.android.launcher" + "com.google.android.apps.nexuslauncher").toSet()
            }
            startForeground(NOTIFICATION_ID, buildNotification(durationMs))
            startTimer(durationMs)
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startTimer(durationMs: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val notification = buildNotification(millisUntilFinished)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
                
                sessionId?.let { sid ->
                    serviceScope.launch {
                        val session = repository.getSession(sid) ?: return@launch
                        if (hasUsageStatsPermission()) {
                            val funTimeUsed = calculateFunTimeUsed(session.startedAt, funPackages)
                            repository.saveSession(session.copy(funTimeUsedMs = funTimeUsed))
                            
                            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                            val topApp = getTopApp(usm)
                            
                            // Let the launcher be accessible to pick apps
                            val isLauncher = topApp?.contains("launcher", ignoreCase = true) == true
                            
                            if (topApp != null && topApp != packageName && !isLauncher) {
                                if (topApp !in allowedPackages) {
                                    launchApp()
                                } else if (topApp in funPackages && funTimeUsed >= session.funBudgetMs) {
                                    launchApp()
                                }
                            }
                        }
                    }
                }
            }

            override fun onFinish() {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notification = NotificationCompat.Builder(this@SessionService, CHANNEL_ID)
                    .setContentTitle("Session Ended")
                    .setContentText("Your focus session has finished.")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
                manager.notify(NOTIFICATION_ID, notification)
                stopSelf()
            }
        }.start()
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

    private fun launchApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
    }

    private fun buildNotification(millisUntilFinished: Long): android.app.Notification {
        val remainingSecs = maxOf(0L, millisUntilFinished / 1000)
        val h = remainingSecs / 3600
        val m = (remainingSecs % 3600) / 60
        val s = remainingSecs % 60
        val timeString = String.format("%02d:%02d:%02d", h, m, s)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Session Active")
            .setContentText("Time remaining: $timeString")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Session Timer",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun calculateFunTimeUsed(startedAt: Long, funPackages: Set<String>): Long {
        if (funPackages.isEmpty()) return 0L
        
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(startedAt, System.currentTimeMillis())
        var totalTime = 0L
        val packageStartTimes = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            
            if (funPackages.contains(event.packageName)) {
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    if (!packageStartTimes.containsKey(event.packageName)) {
                        packageStartTimes[event.packageName] = event.timeStamp
                    }
                } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED || event.eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                    val startTime = packageStartTimes.remove(event.packageName)
                    if (startTime != null) {
                        totalTime += (event.timeStamp - startTime)
                    }
                }
            }
        }
        
        val currentTime = System.currentTimeMillis()
        for ((pkg, startTime) in packageStartTimes) {
            totalTime += (currentTime - startTime)
        }
        return totalTime
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "SessionTimerChannel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_DURATION_MS = "EXTRA_DURATION_MS"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
    }
}
