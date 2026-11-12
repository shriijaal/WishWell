package com.wishwell.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wishwell.app.notify.Notifier
import com.wishwell.app.ui.navigation.WishwellRoot
import com.wishwell.app.ui.theme.WishwellTheme

class MainActivity : ComponentActivity() {

    private var sharedUrl by mutableStateOf<String?>(null)
    private var targetItemId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        sharedUrl = intent.extractSharedUrl()
        targetItemId = intent.extractItemId()

        setContent {
            val context = LocalContext.current
            val container = (context.applicationContext as WishwellApp).container
            val accent by container.prefs.accentColor.collectAsStateWithLifecycle(initialValue = "green")
            val uiTheme by container.prefs.uiTheme.collectAsStateWithLifecycle(initialValue = "system")
            val isSystemDark = isSystemInDarkTheme()
            val darkTheme = when (uiTheme) {
                "light" -> false
                "dark" -> true
                else -> isSystemDark
            }
            WishwellTheme(accentKey = accent, darkTheme = darkTheme) {
                val permissionLauncher = rememberNotificationPermissionLauncher()

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                WishwellRoot(
                    container = container,
                    startUrl = sharedUrl,
                    startItemId = targetItemId,
                    onStartConsumed = {
                        sharedUrl = null
                        targetItemId = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.extractSharedUrl()?.let { sharedUrl = it }
        intent.extractItemId()?.let { targetItemId = it }
    }

    private fun Intent.extractItemId(): Long? =
        getLongExtra(Notifier.EXTRA_ITEM_ID, -1L).takeIf { it > 0L }


    private fun Intent.extractSharedUrl(): String? {
        val text = when (action) {
            Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> dataString
            else -> null
        } ?: return null

        val match = Regex("https?://\\S+").find(text) ?: return null
        return match.value.trimEnd('.', ',', ')', ']', '}', '"', '\'')
    }
}

@androidx.compose.runtime.Composable
private fun rememberNotificationPermissionLauncher() =
    androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* The app works fine without it; alerts are just silent. */ }
