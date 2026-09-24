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
        try {
            NotificationManagerCompat.from(ctx).notify(pin.id, builder.build())
        } catch (_: SecurityException) {
            // Notification permission not granted yet; the app shows a hint for this.
        }
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)

    fun showAll(ctx: Context) = Store.load(ctx).forEach { show(ctx, it) }
}
