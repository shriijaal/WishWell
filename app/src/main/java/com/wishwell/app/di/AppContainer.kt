package com.wishwell.app.di

import android.content.Context
import com.wishwell.app.data.db.WishwellDatabase
import com.wishwell.app.data.net.ProductScraper
import com.wishwell.app.data.net.StoreRegistry
import com.wishwell.app.data.net.StoreSearchScraper
import com.wishwell.app.data.prefs.UserPreferences
import com.wishwell.app.data.repo.WishlistRepository
import com.wishwell.app.data.suggest.RecommendationEngine
import com.wishwell.app.notify.Notifier
import com.wishwell.app.work.WorkScheduler

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database = WishwellDatabase.build(appContext)

    val prefs = UserPreferences(appContext)
    val storeRegistry = StoreRegistry()
    val scraper = ProductScraper(stores = storeRegistry)
    val storeSearchScraper = StoreSearchScraper(stores = storeRegistry)
    val notifier = Notifier(appContext)
    val recommendations = RecommendationEngine(storeRegistry)
    val scheduler = WorkScheduler(appContext)

    val repository = WishlistRepository(
        itemDao = database.itemDao(),
        categoryDao = database.categoryDao(),
        scraper = scraper,
        stores = storeRegistry,
        prefs = prefs,
        notifier = notifier,
        appContext = appContext
    )
}
