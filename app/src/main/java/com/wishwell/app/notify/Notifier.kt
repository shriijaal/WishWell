package com.wishwell.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wishwell.app.MainActivity
import com.wishwell.app.R
import com.wishwell.app.data.model.WishItem
import com.wishwell.app.util.formatMoney

class Notifier(private val context: Context) {

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyPriceChange(
        item: WishItem,
        oldPrice: Double?,
        newPrice: Double,
        targetHit: Boolean,
        newLow: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val price = formatMoney(newPrice, item.currencyCode)
        val was = oldPrice?.let { " (was ${formatMoney(it, item.currencyCode)})" } ?: ""

        val title = when {
            targetHit && newLow -> "Target hit and a new low"
            targetHit -> "Target price reached"
            newLow -> "New all-time low"
            else -> "Price drop"
        }

        val body = buildString {
            append("${item.title.take(60)} is now $price$was at ${item.storeName}.")
            item.targetPrice?.let { target ->
                if (!targetHit) append(" Your target is ${formatMoney(target, item.currencyCode)}.")
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ITEM_ID, item.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            item.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wishwell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(item.id.toInt(), notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "price_alerts"
        const val EXTRA_ITEM_ID = "com.wishwell.app.extra.ITEM_ID"
    }
}
