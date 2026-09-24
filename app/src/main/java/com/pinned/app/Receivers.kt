package com.pinned.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RepostReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(Notifier.EXTRA_ID, -1)
        Store.load(ctx).find { it.id == id }?.let { Notifier.show(ctx, it) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Notifier.showAll(ctx)
}
