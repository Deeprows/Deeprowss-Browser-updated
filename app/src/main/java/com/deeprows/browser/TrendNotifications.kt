package com.deeprows.browser

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

// =============================================================
// TREND NOTIFICATIONS
// =============================================================
//
// A background job (WorkManager) runs every few hours, reads the
// latest Google Trends for the user's country and posts ONE
// notification with the top trends. A notification is only shown
// when the top trends have changed since the last one, so users
// are never spammed with the same list.
//
// Tapping the notification opens the top trend in the browser.
//
// =============================================================

object TrendNotifications {

    const val EXTRA_OPEN_URL = "open_url"

    private const val CHANNEL_ID = "deeprows_trending"
    private const val WORK_NAME = "deeprows_trend_alerts"
    private const val PREFS = "deeprows_browser"
    private const val KEY_LAST_SIGNATURE = "last_trend_signature"

    fun createChannel(context: Context) {

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O
        ) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trending now",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    "Alerts about what is trending right now"
            }

            context.getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    // Safe to call on every app start: KEEP leaves an already
    // scheduled job untouched.
    fun schedule(context: Context) {

        val request = PeriodicWorkRequestBuilder<TrendWorker>(
            6, TimeUnit.HOURS
        )
            .setInitialDelay(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    private fun canNotify(context: Context): Boolean {

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }

        return NotificationManagerCompat
            .from(context)
            .areNotificationsEnabled()
    }

    internal suspend fun checkAndNotify(context: Context) {

        if (!canNotify(context)) return

        val country = CountryProvider.getCountryCode(context)

        val trends = NewsRepository().getGoogleTrends(
            country.ifBlank { null },
            5
        )

        if (trends.isEmpty()) return

        // Skip if the top trends are the same as the last alert.
        val signature = trends.take(3).joinToString("|") { it.title }

        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        if (prefs.getString(KEY_LAST_SIGNATURE, null) == signature) {
            return
        }

        val top = trends.first()

        val summary = trends
            .take(3)
            .mapIndexed { i, t -> "${i + 1}. ${t.title}" }
            .joinToString("\n")

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (top.link.isNotBlank()) {
                putExtra(EXTRA_OPEN_URL, top.link)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            signature.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        createChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_trend)
            .setContentTitle("\uD83D\uDD25 Trending now: ${top.title}")
            .setContentText(trends.drop(1).firstOrNull()?.title ?: "")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(summary)
            )
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                2001,
                notification
            )
            prefs.edit().putString(KEY_LAST_SIGNATURE, signature).apply()
        } catch (_: SecurityException) {
            // Permission was revoked in the meantime.
        }
    }
}

class TrendWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            TrendNotifications.checkAndNotify(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
