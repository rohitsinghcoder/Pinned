package com.pinned.app

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class RepostReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(Notifier.EXTRA_ID, -1)
        val pin = Store.load(ctx).find { it.id == id }
        if (pin != null) {
            Notifier.show(ctx, pin, silent = true)
        }
        // Also heal any sibling pins that were dropped (e.g. via Clear All).
        Notifier.sync(ctx, skipId = id)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notifier.sync(ctx)
        Reminders.rescheduleAll(ctx)
    }
}

/**
 * Periodic watchdog job that keeps pinned notifications alive across OS timeouts
 * (e.g. ColorOS 72h auto-dismiss), SystemUI restarts, and reboots.
 */
class SyncJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Notifier.sync(this)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}

object PinWatchdog {
    private const val JOB_ID = 7001
    private const val INTERVAL_MS = 6L * 60 * 60 * 1000
    private const val FLEX_MS = 1L * 60 * 60 * 1000

    fun syncSchedule(ctx: Context, hasPins: Boolean = Store.load(ctx).isNotEmpty()) {
        val js = ctx.getSystemService(JobScheduler::class.java)
        val scheduled = js.getPendingJob(JOB_ID) != null
        if (!hasPins) {
            if (scheduled) js.cancel(JOB_ID)
            return
        }
        if (scheduled) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, SyncJobService::class.java))
            .setPeriodic(INTERVAL_MS, FLEX_MS)
            .setPersisted(true)
            .build()
        js.schedule(job)
    }
}
