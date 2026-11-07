package com.wishwell.app

import android.app.Application
import com.wishwell.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class WishwellApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        appScope.launch {
            container.repository.ensureDefaultCategories()
        }

        appScope.launch {
            container.prefs.refreshIntervalHours
                .distinctUntilChanged()
                .collect { hours -> container.scheduler.schedulePeriodic(hours) }
        }
    }
}
