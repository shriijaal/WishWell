package com.wishwell.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WishwellWidgetUpdater {
    suspend fun updateAll(context: Context) {
        runCatching { WishlistedItemsWidget().updateAll(context) }
        runCatching { PriceDropsWidget().updateAll(context) }
    }
}
