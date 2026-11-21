package com.pinned.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    private const val CHANNEL = "pinned"
    const val EXTRA_ID = "id"

    // OnePlus / ColorOS injects a 72h timeout (PT72H) on notifications and silently removes them
    // without triggering deleteIntent. Silently refreshing active pins after 24h resets that clock.
    private const val REFRESH_AFTER_MS = 24L * 60 * 60 * 1000

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Pinned items", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Things you pinned so you don't forget"
            }
        )
    }

    /**
     * Posts a pin as an ongoing notification. Android 14+ lets users swipe these away anyway,
     * so a delete intent re-posts it until it is marked done in the app.
     */
    fun show(ctx: Context, pin: Pin, silent: Boolean = true) {
        ensureChannel(ctx)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )
        val repost = PendingIntent.getBroadcast(
            ctx, pin.id,
            Intent(ctx, RepostReceiver::class.java).putExtra(EXTRA_ID, pin.id),
            flags
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_pin)
            .setContentTitle(pin.title)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(silent)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(pin.created)
            .setShowWhen(true)
            .setContentIntent(open)
            .setDeleteIntent(repost)
        if (pin.note.isNotBlank()) {
            builder.setContentText(pin.note)
                .setStyle(NotificationCompat.BigTextStyle().bigText(pin.note))
        }
        val remindAt = pin.remindAt
        if (remindAt != null && remindAt > System.currentTimeMillis()) {
            builder.setSubText("Reminder · ${Reminders.formatTarget(ctx, remindAt)}")
        }
        try {
            NotificationManagerCompat.from(ctx).notify(pin.id, builder.build())
        } catch (_: SecurityException) {
            // Notification permission not granted yet; the app shows a hint for this.
        }
        PinWatchdog.syncSchedule(ctx)
    }

    fun cancel(ctx: Context, id: Int) {
        NotificationManagerCompat.from(ctx).cancel(id)
        PinWatchdog.syncSchedule(ctx)
    }

    /**
     * Reconciles active notifications with stored pins:
     * - Re-posts any stored pin whose notification went missing (timeout, Clear All, SystemUI restart, reboot).
     * - Silently refreshes pins posted more than 24h ago so OS auto-timeouts never expire them.
     * - Cancels orphaned notifications that are no longer in the store.
     */
    fun sync(ctx: Context, skipId: Int = -1) {
        val pins = Store.load(ctx)
        PinWatchdog.syncSchedule(ctx, hasPins = pins.isNotEmpty())

        val nm = ctx.getSystemService(NotificationManager::class.java)
        val activeById = nm.activeNotifications.associateBy { it.id }
        val pinIds = pins.mapTo(HashSet(pins.size)) { it.id }

        // Remove any lingering pin notifications for pins already deleted/completed.
        // Leave full-screen reminder alerts (id >= Reminders.NOTIF_ID_OFFSET) untouched.
        for (id in activeById.keys) {
            if (id < Reminders.NOTIF_ID_OFFSET && id != skipId && id !in pinIds) {
                nm.cancel(id)
            }
        }

        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return

        val now = System.currentTimeMillis()
        for (pin in pins) {
            if (pin.id == skipId) continue
            val existing = activeById[pin.id]
            val missing = existing == null
            val stale = existing != null && (now - existing.postTime) >= REFRESH_AFTER_MS
            if (missing || stale) {
                show(ctx, pin, silent = true)
            }
        }
    }

    fun showAll(ctx: Context) = sync(ctx)
}
