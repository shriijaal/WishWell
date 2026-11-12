package com.wishwell.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wishwell_settings")

class UserPreferences(private val context: Context) {

    private object Keys {
        val REFRESH_HOURS = intPreferencesKey("refresh_hours")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val NOTIFY_TARGET = booleanPreferencesKey("notify_target")
        val NOTIFY_NEW_LOW = booleanPreferencesKey("notify_new_low")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val LAST_REFRESH = longPreferencesKey("last_refresh")
        val NAVBAR_STYLE = stringPreferencesKey("navbar_style")
        val NAVBAR_GLASS_GLOW = booleanPreferencesKey("navbar_glass_glow")
        val WISHLIST_VIEW_MODE = stringPreferencesKey("wishlist_view_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val READ_NOTIFICATION_KEYS = stringSetPreferencesKey("read_notification_keys")

        // Backup settings
        val BACKUP_LOCATION = stringPreferencesKey("backup_location")
        val BACKUP_LOCATION_URI = stringPreferencesKey("backup_location_uri")
        val AUTO_BACKUP_FREQUENCY = stringPreferencesKey("auto_backup_frequency")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val BACKUP_FILE_PATHS = stringSetPreferencesKey("backup_file_paths")

        // UI theme: "system", "light", "dark"
        val UI_THEME = stringPreferencesKey("ui_theme")

        // Widget appearance
        val WIDGET_TEXT_SIZE = stringPreferencesKey("widget_text_size")
        val WIDGET_SHOW_IMAGES = booleanPreferencesKey("widget_show_images")
        val WIDGET_BACKGROUND_OPACITY = intPreferencesKey("widget_background_opacity")
        val WIDGET_BORDER_STYLE = stringPreferencesKey("widget_border_style")
        val WIDGET_BORDER_COLOR = stringPreferencesKey("widget_border_color")
        val WIDGET_BORDER_WIDTH = intPreferencesKey("widget_border_width")
    }

    val refreshIntervalHours: Flow<Int> =
        context.dataStore.data.map { it[Keys.REFRESH_HOURS] ?: 12 }

    val notificationsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIFICATIONS] ?: true }

    val notifyOnTarget: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIFY_TARGET] ?: true }

    val notifyOnNewLow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIFY_NEW_LOW] ?: true }

    val defaultCurrency: Flow<String> =
        context.dataStore.data.map { it[Keys.DEFAULT_CURRENCY] ?: "USD" }

    val lastRefreshAt: Flow<Long?> =
        context.dataStore.data.map { it[Keys.LAST_REFRESH] }

    val navbarStyle: Flow<String> =
        context.dataStore.data.map {
            when (val v = it[Keys.NAVBAR_STYLE] ?: "classic") {
                "liquid_glass" -> "glass_adaptive"
                else -> v
            }
        }

    val navbarGlassGlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NAVBAR_GLASS_GLOW] ?: true }

    /** Default wishlist layout: "list", "grid", or "carousel". */
    val wishlistViewMode: Flow<String> =
        context.dataStore.data.map { it[Keys.WISHLIST_VIEW_MODE] ?: "list" }

    suspend fun setWishlistViewMode(mode: String) {
        context.dataStore.edit { it[Keys.WISHLIST_VIEW_MODE] = mode }
    }

    val accentColor: Flow<String> =
        context.dataStore.data.map { it[Keys.ACCENT_COLOR] ?: "green" }

    val readNotificationKeys: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.READ_NOTIFICATION_KEYS] ?: emptySet() }

    // ---- Backup preferences ----

    /** Where backups are written: "app_internal" or "user_picked". */
    val backupLocation: Flow<String> =
        context.dataStore.data.map { it[Keys.BACKUP_LOCATION] ?: "app_internal" }

    /** Persisted SAF URI for user-picked location (may be null). */
    val backupLocationUri: Flow<String?> =
        context.dataStore.data.map { it[Keys.BACKUP_LOCATION_URI] }

    /** Auto backup frequency: "off", "daily", "weekly", "monthly". */
    val autoBackupFrequency: Flow<String> =
        context.dataStore.data.map { it[Keys.AUTO_BACKUP_FREQUENCY] ?: "off" }

    val lastBackupAt: Flow<Long?> =
        context.dataStore.data.map { it[Keys.LAST_BACKUP_AT] }

    val backupFilePaths: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.BACKUP_FILE_PATHS] ?: emptySet() }

    /** App theme: "system", "light", "dark". */
    val uiTheme: Flow<String> =
        context.dataStore.data.map { it[Keys.UI_THEME] ?: "system" }

    // Legacy global widget appearance. Widgets now keep their own settings in Glance state;
    // these values only seed the defaults for widgets that were never reconfigured.

    /** Widget text size: "small", "default", "large". */
    val widgetTextSize: Flow<String> =
        context.dataStore.data.map { it[Keys.WIDGET_TEXT_SIZE] ?: "default" }

    val widgetShowImages: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.WIDGET_SHOW_IMAGES] ?: true }

    /** Widget background opacity, 20-100 percent. */
    val widgetBackgroundOpacity: Flow<Int> =
        context.dataStore.data.map { (it[Keys.WIDGET_BACKGROUND_OPACITY] ?: 100).coerceIn(20, 100) }

    suspend fun setRefreshInterval(hours: Int) {
        context.dataStore.edit { it[Keys.REFRESH_HOURS] = hours }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun setNotifyOnTarget(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_TARGET] = enabled }
    }

    suspend fun setNotifyOnNewLow(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_NEW_LOW] = enabled }
    }

    suspend fun setDefaultCurrency(code: String) {
        context.dataStore.edit { it[Keys.DEFAULT_CURRENCY] = code }
    }

    suspend fun setLastRefresh(epochMillis: Long) {
        context.dataStore.edit { it[Keys.LAST_REFRESH] = epochMillis }
    }

    suspend fun setNavbarStyle(style: String) {
        context.dataStore.edit { it[Keys.NAVBAR_STYLE] = style }
    }

    suspend fun setNavbarGlassGlow(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NAVBAR_GLASS_GLOW] = enabled }
    }

    suspend fun setAccentColor(key: String) {
        context.dataStore.edit { it[Keys.ACCENT_COLOR] = key }
    }

    suspend fun markNotificationsRead(keys: Set<String>) {
        if (keys.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.READ_NOTIFICATION_KEYS] ?: emptySet()
            prefs[Keys.READ_NOTIFICATION_KEYS] = current + keys
        }
    }

    // ---- Backup preference setters ----

    suspend fun setBackupLocation(location: String, uri: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BACKUP_LOCATION] = location
            if (uri == null) {
                prefs.remove(Keys.BACKUP_LOCATION_URI)
            } else {
                prefs[Keys.BACKUP_LOCATION_URI] = uri
            }
        }
    }

    suspend fun setAutoBackupFrequency(frequency: String) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP_FREQUENCY] = frequency }
    }

    suspend fun setLastBackupAt(epochMillis: Long) {
        context.dataStore.edit { it[Keys.LAST_BACKUP_AT] = epochMillis }
    }

    suspend fun recordBackupFile(path: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.BACKUP_FILE_PATHS] ?: emptySet()
            val updated = (current.toList() + path).takeLast(50).toSet()
            prefs[Keys.BACKUP_FILE_PATHS] = updated
        }
    }

    suspend fun removeBackupFilePath(path: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.BACKUP_FILE_PATHS] ?: emptySet()
            prefs[Keys.BACKUP_FILE_PATHS] = current - path
        }
    }

    suspend fun setUiTheme(theme: String) {
        context.dataStore.edit { it[Keys.UI_THEME] = theme }
    }

    /** Widget border: "none", "accent", or "custom". */
    val widgetBorderStyle: Flow<String> =
        context.dataStore.data.map { it[Keys.WIDGET_BORDER_STYLE] ?: "none" }

    /** Hex color used when the border style is "custom". */
    val widgetBorderColor: Flow<String> =
        context.dataStore.data.map { it[Keys.WIDGET_BORDER_COLOR] ?: "#1F3D2E" }

    /** Widget border thickness in dp, 1-8. */
    val widgetBorderWidth: Flow<Int> =
        context.dataStore.data.map { (it[Keys.WIDGET_BORDER_WIDTH] ?: 2).coerceIn(1, 8) }
}
