package com.wishwell.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wishwell.app.data.repo.WishlistRepository
import com.wishwell.app.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val refreshHours: Int = 12,
    val notificationsEnabled: Boolean = true,
    val notifyOnTarget: Boolean = true,
    val notifyOnNewLow: Boolean = true,
    val defaultCurrency: String = "USD",
    val accentColor: String = "green",
    val navbarStyle: String = "classic",
    val navbarGlassGlow: Boolean = true,
    val lastRefreshAt: Long? = null,
    val itemCount: Int = 0,
    val refreshing: Boolean = false,
    val message: String? = null,
    val loaded: Boolean = false,
    // Backup state
    val backupLocation: String = "app_internal",
    val autoBackupFrequency: String = "off",
    val lastBackupAt: Long? = null,
    val backupFileCount: Int = 0,
    val backupFiles: List<WishlistRepository.BackupFileInfo> = emptyList(),
    // Theme state
    val uiTheme: String = "system"
)

private data class PrefsSnapshot(
    val refreshHours: Int,
    val notificationsEnabled: Boolean,
    val notifyOnTarget: Boolean,
    val notifyOnNewLow: Boolean,
    val defaultCurrency: String,
    val accentColor: String,
    val navbarStyle: String,
    val navbarGlassGlow: Boolean,
    val backupLocation: String,
    val autoBackupFrequency: String,
    val uiTheme: String
)

private data class StatusSnapshot(
    val refreshing: Boolean,
    val message: String?,
    val itemCount: Int
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val prefs = container.prefs

    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val storageUsage = MutableStateFlow<WishlistRepository.StorageUsage?>(null)
    private val diskVolumes = MutableStateFlow<List<WishlistRepository.DiskVolume>>(emptyList())
    private val backupFiles = MutableStateFlow<List<WishlistRepository.BackupFileInfo>>(emptyList())

    private val prefsCore1 = combine(
        prefs.refreshIntervalHours,
        prefs.notificationsEnabled,
        prefs.notifyOnTarget
    ) { hours, enabled, target -> Triple(hours, enabled, target) }

    private val prefsCore2 = combine(
        prefs.notifyOnNewLow,
        prefs.defaultCurrency,
        prefs.accentColor
    ) { low, currency, accent -> Triple(low, currency, accent) }

    private val navPrefs = combine(prefs.navbarStyle, prefs.navbarGlassGlow) { style, glow -> style to glow }

    private val backupPrefs = combine(
        prefs.backupLocation,
        prefs.autoBackupFrequency
    ) { location, frequency -> location to frequency }

    private val prefsSnapshot = combine(prefsCore1, prefsCore2, navPrefs, backupPrefs, prefs.uiTheme) { c1, c2, nav, bk, theme ->
        PrefsSnapshot(
            refreshHours = c1.first,
            notificationsEnabled = c1.second,
            notifyOnTarget = c1.third,
            notifyOnNewLow = c2.first,
            defaultCurrency = c2.second,
            accentColor = c2.third,
            navbarStyle = nav.first,
            navbarGlassGlow = nav.second,
            backupLocation = bk.first,
            autoBackupFrequency = bk.second,
            uiTheme = theme
        )
    }

    private val statusSnapshot = combine(
        refreshing,
        message,
        repo.observeItems()
    ) { isRefreshing, notice, items ->
        StatusSnapshot(isRefreshing, notice, items.size)
    }

    val state: StateFlow<SettingsUiState> = combine(
        prefsSnapshot,
        statusSnapshot,
        prefs.lastRefreshAt,
        prefs.lastBackupAt,
        prefs.backupFilePaths
    ) { p, s, lastRefresh, lastBackup, backupPaths ->
        SettingsUiState(
            refreshHours = p.refreshHours,
            notificationsEnabled = p.notificationsEnabled,
            notifyOnTarget = p.notifyOnTarget,
            notifyOnNewLow = p.notifyOnNewLow,
            defaultCurrency = p.defaultCurrency,
            accentColor = p.accentColor,
            navbarStyle = p.navbarStyle,
            navbarGlassGlow = p.navbarGlassGlow,
            lastRefreshAt = lastRefresh,
            itemCount = s.itemCount,
            refreshing = s.refreshing,
            message = s.message,
            loaded = true,
            backupLocation = p.backupLocation,
            autoBackupFrequency = p.autoBackupFrequency,
            lastBackupAt = lastBackup,
            backupFileCount = backupPaths.size,
            uiTheme = p.uiTheme,
            backupFiles = backupFiles.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    val storageUsageState: StateFlow<WishlistRepository.StorageUsage?> = storageUsage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val diskVolumesState: StateFlow<List<WishlistRepository.DiskVolume>> = diskVolumes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ---------------------------------------------------------------- preferences

    fun setRefreshInterval(hours: Int) {
        viewModelScope.launch {
            prefs.setRefreshInterval(hours)
            container.scheduler.schedulePeriodic(hours)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotificationsEnabled(enabled) }
    }

    fun setNotifyOnTarget(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotifyOnTarget(enabled) }
    }

    fun setNotifyOnNewLow(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotifyOnNewLow(enabled) }
    }

    fun setDefaultCurrency(code: String) {
        viewModelScope.launch { prefs.setDefaultCurrency(code) }
    }

    fun setAccentColor(key: String) {
        viewModelScope.launch { prefs.setAccentColor(key) }
    }

    fun setNavbarStyle(style: String) {
        viewModelScope.launch { prefs.setNavbarStyle(style) }
    }

    fun setNavbarGlassGlow(enabled: Boolean) {
        viewModelScope.launch { prefs.setNavbarGlassGlow(enabled) }
    }

    fun setUiTheme(theme: String) {
        viewModelScope.launch { prefs.setUiTheme(theme) }
    }

    // ---------------------------------------------------------------- backup

    fun setBackupLocation(location: String, uri: String? = null) {
        viewModelScope.launch { prefs.setBackupLocation(location, uri) }
        if (location == "app_internal") {
            container.scheduler.cancelAutoBackup()
        } else {
            container.scheduler.scheduleAutoBackup(state.value.autoBackupFrequency)
        }
    }

    fun setAutoBackupFrequency(frequency: String) {
        viewModelScope.launch {
            prefs.setAutoBackupFrequency(frequency)
            container.scheduler.scheduleAutoBackup(frequency)
        }
    }

    fun loadStorageUsage() {
        viewModelScope.launch {
            val usage = withContext(Dispatchers.IO) {
                repo.getStorageUsage()
            }
            storageUsage.value = usage
        }
    }

    fun loadDiskVolumes() {
        viewModelScope.launch {
            val volumes = withContext(Dispatchers.IO) {
                repo.getDiskVolumes()
            }
            diskVolumes.value = volumes
        }
    }

    fun loadBackupFiles() {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                repo.listBackupFiles()
            }
            val info = files.map { f ->
                WishlistRepository.BackupFileInfo(
                    path = f.absolutePath,
                    name = f.name,
                    size = f.length(),
                    lastModified = f.lastModified()
                )
            }
            backupFiles.value = info
        }
    }

    fun restoreFromBackupFile(path: String) {
        if (backupBusy.value) return
        viewModelScope.launch {
            backupBusy.value = true
            val result = runCatching {
                repo.importFromFile(path)
            }
            backupBusy.value = false
            loadBackupFiles()
            reportMessage(
                result.fold(
                    onSuccess = { r ->
                        when {
                            r.itemsAdded == 0 && r.itemsMerged == 0 && r.categoriesAdded == 0 && r.historyPointsAdded == 0 ->
                                "Nothing new to import."
                            else -> buildString {
                                append("Import complete: ")
                                val parts = mutableListOf<String>()
                                if (r.itemsAdded > 0) parts += "${r.itemsAdded} item${if (r.itemsAdded == 1) "" else "s"} added"
                                if (r.itemsMerged > 0) parts += "${r.itemsMerged} merged"
                                if (r.categoriesAdded > 0) parts += "${r.categoriesAdded} categor${if (r.categoriesAdded == 1) "y" else "ies"} added"
                                if (r.historyPointsAdded > 0) parts += "${r.historyPointsAdded} price point${if (r.historyPointsAdded == 1) "" else "s"}"
                                append(parts.joinToString(", ").ifBlank { "done" })
                                append(".")
                            }
                        }
                    },
                    onFailure = { it.message ?: "Couldn't restore backup." }
                )
            )
        }
    }

    private val backupBusy = MutableStateFlow(false)

    // ---------------------------------------------------------------- existing

    fun refreshNow() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val summary = repo.refreshAll()
            message.value = when {
                summary.checked == 0 -> "Nothing saved to check yet."
                summary.failed == 0 && summary.updated == 0 ->
                    "Checked ${summary.checked} items. Prices are unchanged."
                summary.failed == 0 ->
                    "Checked ${summary.checked} · ${summary.updated} updated"
                summary.failed == summary.checked ->
                    "Couldn't read any store pages. Most retailers block automated checks."
                else -> "Checked ${summary.checked} · ${summary.failed} couldn't be read"
            }
            refreshing.value = false
        }
    }

    fun loadDemo() {
        viewModelScope.launch {
            val added = repo.seedDemoData()
            message.value = if (added) "Demo list added." else "You already have items saved."
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repo.clearAll()
            message.value = "All items removed."
        }
    }

    fun clearMessage() {
        message.value = null
    }

    fun reportMessage(text: String) {
        message.value = text
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
        if (bytes < 1024L * 1024 * 1024) return "%.1f MB".format(bytes / (1024.0 * 1024))
        return "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
