package com.boardsprep.onboard.core.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.boardsprep.onboard.MainActivity

object DownloadNotificationHelper {

    const val CHANNEL_ID = "channel_lecture_downloads"
    private const val CHANNEL_NAME = "Lecture Downloads"
    private const val CHANNEL_DESC = "Shows real-time download progress, speed, and completion status for offline lectures"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESC
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun getNotificationId(lectureId: String): Int {
        return (lectureId.hashCode() and 0x7FFFFFFF)
    }

    private fun createContentPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    fun notifyProgress(
        context: Context,
        lectureId: String,
        title: String,
        progress: DownloadState.Progress
    ) {
        try {
            val pendingIntent = createContentPendingIntent(context)
            val notificationId = getNotificationId(lectureId)

            val stageText = progress.stage.label
            val detailsText = "${progress.percentage}% • ${progress.speedFormatted} • ${progress.etaFormatted}"
            val subText = if (progress.qualityLabel.isNotBlank()) {
                "${progress.qualityLabel} (${progress.sizeFormatted})"
            } else {
                progress.sizeFormatted
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(detailsText)
                .setSubText(stageText)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("$stageText\n$detailsText\nTotal: $subText")
                )
                .setProgress(100, progress.percentage, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)

            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission not granted yet on Android 13+
        } catch (_: Exception) {
            // Failsafe
        }
    }

    fun notifySuccess(
        context: Context,
        lectureId: String,
        title: String
    ) {
        try {
            val pendingIntent = createContentPendingIntent(context)
            val notificationId = getNotificationId(lectureId)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download Complete")
                .setContentText("$title is ready for offline study")
                .setSubText("Ready Offline")
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    fun notifyError(
        context: Context,
        lectureId: String,
        title: String,
        errorMessage: String
    ) {
        try {
            val pendingIntent = createContentPendingIntent(context)
            val notificationId = getNotificationId(lectureId)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Download Interrupted")
                .setContentText(errorMessage)
                .setSubText("Failed")
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    fun cancelNotification(context: Context, lectureId: String) {
        try {
            val notificationId = getNotificationId(lectureId)
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (_: Exception) {
        }
    }
}
