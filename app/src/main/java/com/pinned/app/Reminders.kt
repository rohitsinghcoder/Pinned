package com.pinned.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Date

object Reminders {
    private const val CHANNEL = "pinned_reminder_v2"
    const val NOTIF_ID_OFFSET = 1_000_000
    const val ACTION_FIRE = "com.pinned.app.ACTION_REMINDER_FIRE"
    const val ACTION_SNOOZE_5M = "com.pinned.app.ACTION_REMINDER_SNOOZE_5M"
    const val ACTION_DONE = "com.pinned.app.ACTION_REMINDER_DONE"
    const val EXTRA_PIN_ID = "pin_id"
    private const val MISSED_WINDOW_MS = 12L * 60 * 60 * 1000

    fun canDrawOverlays(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun canUseFullScreenIntent(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 34) {
            ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else true

    fun canScheduleExactAlarms(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else true

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("pinned_reminder_v1")
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Full-screen reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Timed full-screen reminders (vibration + visual only)"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 120, 400, 120, 550)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
        )
    }

    private fun alarmIntent(ctx: Context, pinId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            NOTIF_ID_OFFSET + pinId,
            Intent(ctx, ReminderReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_PIN_ID, pinId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun schedule(ctx: Context, pin: Pin) {
        val at = pin.remindAt
        if (at == null) {
            cancel(ctx, pin.id)
            return
        }
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx, pin.id)
        val triggerAt = at.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        if (canScheduleExactAlarms(ctx)) {
            val showApp = PendingIntent.getActivity(
                ctx,
                pin.id,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // setAlarmClock bypasses Doze and ColorOS background restrictions reliably.
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showApp), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(ctx: Context, pinId: Int) {
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx, pinId))
        dismissAlert(ctx, pinId)
    }

    fun dismissAlert(ctx: Context, pinId: Int) {
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ID_OFFSET + pinId)
    }

    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        for (pin in Store.load(ctx)) {
            val at = pin.remindAt ?: continue
            when {
                at > now -> schedule(ctx, pin)
                now - at <= MISSED_WINDOW_MS -> trigger(ctx, pin)
                else -> {
                    Store.updateReminder(ctx, pin.id, null)
                    Notifier.show(ctx, pin.copy(remindAt = null), silent = true)
                }
            }
        }
    }

    fun trigger(ctx: Context, pin: Pin) {
        // Clear the scheduled timestamp once fired so it won't refire on resume/boot.
        val cleared = Store.updateReminder(ctx, pin.id, null) ?: return
        Notifier.show(ctx, cleared, silent = true)

        val pm = ctx.getSystemService(PowerManager::class.java)
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Pinned:ReminderWake")
        wakeLock.acquire(5_000L)

        try {
            ensureChannel(ctx)
            val fullScreenIntent = Intent(ctx, ReminderActivity::class.java).apply {
                putExtra(EXTRA_PIN_ID, cleared.id)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                )
            }
            val fullScreenPi = PendingIntent.getActivity(
                ctx,
                NOTIF_ID_OFFSET + cleared.id,
                fullScreenIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val snoozePi = PendingIntent.getBroadcast(
                ctx,
                NOTIF_ID_OFFSET + 100_000 + cleared.id,
                Intent(ctx, ReminderReceiver::class.java)
                    .setAction(ACTION_SNOOZE_5M)
                    .putExtra(EXTRA_PIN_ID, cleared.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val donePi = PendingIntent.getBroadcast(
                ctx,
                NOTIF_ID_OFFSET + 200_000 + cleared.id,
                Intent(ctx, ReminderReceiver::class.java)
                    .setAction(ACTION_DONE)
                    .putExtra(EXTRA_PIN_ID, cleared.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            val builder = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_pin)
                .setContentTitle(cleared.title)
                .setContentText(cleared.note.ifBlank { "Pinned reminder" })
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(fullScreenPi)
                .setFullScreenIntent(fullScreenPi, true)
                .addAction(0, "+5m", snoozePi)
                .addAction(0, "Mark done", donePi)

            try {
                NotificationManagerCompat.from(ctx).notify(NOTIF_ID_OFFSET + cleared.id, builder.build())
            } catch (_: SecurityException) {
            }

            // When Display over other apps is granted, launch full-screen immediately even if unlocked.
            if (canDrawOverlays(ctx)) {
                try {
                    ctx.startActivity(fullScreenIntent)
                } catch (_: Exception) {
                }
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    fun formatDuration(totalSeconds: Long): String {
        val clamped = totalSeconds.coerceAtLeast(0L)
        val h = clamped / 3600
        val m = (clamped % 3600) / 60
        val s = clamped % 60
        return buildList {
            if (h > 0) add("${h}h")
            if (m > 0) add("${m}m")
            if (s > 0 || isEmpty()) add("${s}s")
        }.joinToString(" ")
    }

    fun formatBadge(ctx: Context, remindAt: Long, now: Long = System.currentTimeMillis()): String {
        val diffSec = ((remindAt - now + 500L) / 1000L).coerceAtLeast(1L)
        val diffMin = diffSec / 60L
        val remSec = diffSec % 60L
        val timeText = DateFormat.getTimeFormat(ctx).format(Date(remindAt))
        val isTomorrow = remindAt >= startOfDay(-1)
        return when {
            diffSec < 60L -> "in ${diffSec}s"
            diffMin < 10L && remSec != 0L -> "in ${diffMin}m ${remSec}s"
            diffMin < 60L -> "in ${diffMin}m · $timeText"
            isTomorrow -> "Tomorrow · $timeText"
            else -> timeText
        }
    }

    fun formatTarget(ctx: Context, remindAt: Long): String {
        val timeText = DateFormat.getTimeFormat(ctx).format(Date(remindAt))
        return if (remindAt >= startOfDay(-1)) "Tomorrow, $timeText" else timeText
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pinId = intent.getIntExtra(Reminders.EXTRA_PIN_ID, -1)
        if (pinId < 0) return

        when (intent.action) {
            Reminders.ACTION_SNOOZE_5M -> {
                Reminders.dismissAlert(ctx, pinId)
                val target = System.currentTimeMillis() + 5L * 60 * 1000
                Store.updateReminder(ctx, pinId, target)?.let { updated ->
                    Reminders.schedule(ctx, updated)
                    Notifier.show(ctx, updated, silent = true)
                }
            }
            Reminders.ACTION_DONE -> {
                Reminders.cancel(ctx, pinId)
                Store.markDone(ctx, pinId)
                Notifier.cancel(ctx, pinId)
            }
            else -> {
                Store.load(ctx).find { it.id == pinId }?.let { Reminders.trigger(ctx, it) }
            }
        }
    }
}
