package com.wishwell.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.wishwell.app.action.SYNC_WIDGET") return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                WishwellWidgetUpdater.updateAll(context)
            }
        }
    }
}
