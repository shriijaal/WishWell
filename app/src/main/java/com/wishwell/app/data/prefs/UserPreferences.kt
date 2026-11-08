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
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val READ_NOTIFICATION_KEYS = stringSetPreferencesKey("read_notification_keys")
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

    val accentColor: Flow<String> =
        context.dataStore.data.map { it[Keys.ACCENT_COLOR] ?: "green" }

    val readNotificationKeys: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.READ_NOTIFICATION_KEYS] ?: emptySet() }

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
}
