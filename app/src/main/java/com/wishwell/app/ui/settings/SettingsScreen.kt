package com.wishwell.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wishwell.app.data.repo.WishlistRepository
import com.wishwell.app.di.AppContainer
import com.wishwell.app.ui.components.ConfirmDialog
import com.wishwell.app.ui.components.LoadingSpinner
import com.wishwell.app.ui.components.NoticeBar
import com.wishwell.app.ui.components.SectionLabel
import com.wishwell.app.ui.components.WishPill
import com.wishwell.app.ui.theme.AccentPresets
import com.wishwell.app.ui.theme.LocalWishExtraColors
import com.wishwell.app.ui.theme.isCustomAccentKey
import com.wishwell.app.ui.theme.parseHexColor
import com.wishwell.app.util.formatDateWithYear
import com.wishwell.app.util.formatRelativeDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(container: AppContainer) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemNotificationsEnabled = remember {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCustomPicker by remember { mutableStateOf(false) }
    var backupBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadStorageUsage()
        viewModel.loadDiskVolumes()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (backupBusy) return@rememberLauncherForActivityResult
        scope.launch {
            backupBusy = true
            val result = runCatching {
                val json = container.repository.exportBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Couldn't open the selected file.")
                }
            }
            backupBusy = false
            viewModel.reportMessage(
                result.fold(
                    onSuccess = { "Backup exported." },
                    onFailure = { it.message ?: "Couldn't export backup." }
                )
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (backupBusy) return@rememberLauncherForActivityResult
        scope.launch {
            backupBusy = true
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Couldn't read the selected file.")
                }
                container.repository.importBackupJson(text)
            }
            backupBusy = false
            viewModel.reportMessage(
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
                    onFailure = { it.message ?: "Couldn't import backup." }
                )
            )
        }
    }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setBackupLocation("user_picked", uri.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 120.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "SETTINGS",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
            color = colors.onBackground
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "A few knobs.",
            style = MaterialTheme.typography.displayMedium,
            color = colors.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Nearly everything in Wishwell runs on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = extra.muted
        )

        state.message?.let { notice ->
            Spacer(Modifier.height(14.dp))
            NoticeBar(text = notice, onDismiss = viewModel::clearMessage)
        }

        Spacer(Modifier.height(26.dp))
        SectionLabel("Price checks")
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            Text(
                text = "Check for price changes",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Wishwell wakes up in the background and reads the latest price.",
                style = MaterialTheme.typography.bodySmall,
                color = extra.muted
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6 to "Every 6h", 12 to "12h", 24 to "Daily").forEach { (hours, label) ->
                    WishPill(
                        text = label,
                        selected = state.refreshHours == hours,
                        onClick = { viewModel.setRefreshInterval(hours) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.lastRefreshAt?.let { "Last check ${formatRelativeDay(it).lowercase()}" }
                    ?: "No automatic check has run yet.",
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Android may delay background work to save battery. Opening an item checks it on demand.",
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("Alerts")
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            SwitchRow(
                title = "Price alerts",
                subtitle = "Notifications for drops you care about",
                checked = state.notificationsEnabled,
                onCheckedChange = viewModel::setNotificationsEnabled
            )
            RowDivider()
            SwitchRow(
                title = "When I hit my target",
                subtitle = "Notify when a price reaches the target you set",
                checked = state.notifyOnTarget,
                enabled = state.notificationsEnabled,
                onCheckedChange = viewModel::setNotifyOnTarget
            )
            RowDivider()
            SwitchRow(
                title = "New all-time lows",
                subtitle = "Notify when an item beats its previous low",
                checked = state.notifyOnNewLow,
                enabled = state.notificationsEnabled,
                onCheckedChange = viewModel::setNotifyOnNewLow
            )
            if (!systemNotificationsEnabled) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Notifications are turned off for Wishwell in Android settings, so these alerts won't appear.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.terracotta
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("Appearance")
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            Text(
                text = "Accent color",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Choose a preset or pick any custom highlight color (default: Green).",
                style = MaterialTheme.typography.bodySmall,
                color = extra.muted
            )
            Spacer(Modifier.height(12.dp))
            val customSelected = isCustomAccentKey(state.accentColor)
            val customColor = parseHexColor(state.accentColor) ?: colors.primary
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(AccentPresets, key = { it.key }) { preset ->
                    val selected = !customSelected && state.accentColor == preset.key
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.setAccentColor(preset.key) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (selected) 36.dp else 32.dp)
                                .clip(CircleShape)
                                .background(preset.lightPrimary)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) colors.onSurface.copy(alpha = 0.75f) else extra.hairline,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (preset.lightPrimary.luminance() > 0.45f) Color(0xFF1A1C18) else Color.White)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) colors.onSurface else extra.muted,
                            maxLines = 1
                        )
                    }
                }
                item(key = "custom") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showCustomPicker = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (customSelected) 36.dp else 32.dp)
                                .clip(CircleShape)
                                .background(if (customSelected) customColor else extra.sunken)
                                .border(
                                    width = if (customSelected) 3.dp else 1.5.dp,
                                    color = if (customSelected) colors.onSurface.copy(alpha = 0.75f) else extra.hairline,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (customSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (customColor.luminance() > 0.45f) Color(0xFF1A1C18) else Color.White)
                                )
                            } else {
                                // simple "+" indicator for custom picker
                                Box(modifier = Modifier.size(12.dp, 2.dp).background(extra.muted, RoundedCornerShape(1.dp)))
                                Box(modifier = Modifier.size(2.dp, 12.dp).background(extra.muted, RoundedCornerShape(1.dp)))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Custom",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (customSelected) colors.onSurface else extra.muted,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showCustomPicker = true }
                    .background(if (customSelected) colors.primary.copy(alpha = 0.08f) else extra.sunken.copy(alpha = 0.45f))
                    .border(
                        1.dp,
                        if (customSelected) colors.primary.copy(alpha = 0.35f) else extra.hairline,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (customSelected) customColor else extra.sunken)
                        .border(1.dp, extra.hairline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!customSelected) {
                        Box(modifier = Modifier.size(10.dp, 2.dp).background(extra.muted, RoundedCornerShape(1.dp)))
                        Box(modifier = Modifier.size(2.dp, 10.dp).background(extra.muted, RoundedCornerShape(1.dp)))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (customSelected) "Custom color selected" else "Pick a custom color",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface
                    )
                    Text(
                        text = if (customSelected) "Tap to change (spectrum & hue picker)" else "Open spectrum & hue color picker",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.HorizontalDivider(color = extra.hairline.copy(alpha = 0.8f))
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Navigation bar style",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Choose how the navbar looks and behaves.",
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted
            )
            Spacer(Modifier.height(10.dp))

            val navStyles = listOf(
                Triple("classic", "Classic", "Classic normal app default."),
                Triple("glass_adaptive", "Glass · Adaptive", "Adaptive automatically chooses compact or expanded."),
                Triple("glass_expanded", "Glass · Always expanded", "Always expanded never compacts the nav bar pill."),
                Triple("glass_compact", "Glass · Always compact", "Always compact always compacts navbar pill.")
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                navStyles.forEach { (styleKey, title, subtitle) ->
                    val selected = state.navbarStyle == styleKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.setNavbarStyle(styleKey) }
                            .background(if (selected) colors.primary.copy(alpha = 0.08f) else Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (selected) 6.dp else 1.5.dp,
                                    color = if (selected) colors.primary else extra.muted.copy(alpha = 0.55f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(colors.onPrimary)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.muted
                            )
                        }
                    }
                }
            }

            if (state.navbarStyle != "classic") {
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.HorizontalDivider(
                    color = extra.hairline.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(10.dp))
                SwitchRow(
                    title = "Glass glow",
                    subtitle = "Soft light around the floating navbar pill",
                    checked = state.navbarGlassGlow,
                    onCheckedChange = viewModel::setNavbarGlassGlow
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("Data")
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            OutlinedButton(
                onClick = viewModel::refreshNow,
                enabled = !state.refreshing,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, extra.hairline)
            ) {
                if (state.refreshing) {
                    LoadingSpinner(size = 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Checking…", color = colors.onSurface)
                } else {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = colors.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Check all prices now", color = colors.onSurface)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Backup",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Export or import your saved items, collections, and price history as a file.",
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val name = "wishwell-backup-${java.time.LocalDate.now()}.json"
                        exportLauncher.launch(name)
                    },
                    enabled = !backupBusy,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, extra.hairline)
                ) {
                    Text(if (backupBusy) "Working…" else "Backup now", color = if (backupBusy) extra.muted else colors.onSurface)
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    enabled = !backupBusy,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, extra.hairline)
                ) {
                    Text(if (backupBusy) "Working…" else "Restore now", color = if (backupBusy) extra.muted else colors.onSurface)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::loadDemo,
                enabled = state.itemCount == 0,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, extra.hairline)
            ) {
                Text(
                    text = if (state.itemCount == 0) "Load a demo list" else "Demo list already loaded",
                    color = if (state.itemCount == 0) colors.onSurface else extra.muted
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Delete all items",
                    color = colors.error,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("Backup & storage")
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            // Backup location
            Text(
                text = "Backup location",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Choose where automatic backups are written.",
                style = MaterialTheme.typography.bodySmall,
                color = extra.muted
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.backupLocation == "app_internal") "App internal storage" else "Picked folder",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (state.backupLocation == "app_internal") "Private to this app (auto-rotates last 3)" else "Picked folder",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.muted
                        )
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = {
                        if (state.backupLocation == "user_picked") {
                            pickFolderLauncher.launch(null)
                        } else {
                            viewModel.setBackupLocation("user_picked")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, extra.hairline)
                ) {
                    Text(
                        text = if (state.backupLocation == "user_picked") "Change folder" else "Pick folder",
                        color = colors.onSurface
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.HorizontalDivider(color = extra.hairline.copy(alpha = 0.8f))
            Spacer(Modifier.height(12.dp))

            // Auto backup frequency
            Text(
                text = "Auto-backup frequency",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Wishwell runs a background backup on the schedule you choose.",
                style = MaterialTheme.typography.bodySmall,
                color = extra.muted
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "off" to "Off",
                    "6h" to "6 hrs",
                    "12h" to "12 hrs",
                    "daily" to "Daily",
                    "weekly" to "Weekly",
                    "monthly" to "Monthly"
                ).forEach { (key, label) ->
                    WishPill(
                        text = label,
                        selected = state.autoBackupFrequency == key,
                        onClick = { viewModel.setAutoBackupFrequency(key) }
                    )
                }
            }

            state.lastBackupAt?.let { lastBackup ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Last backup: ${formatDateWithYear(lastBackup)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("Theme")
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            Text(
                text = "App theme",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Choose how the app looks. System follows your device's light/dark setting.",
                style = MaterialTheme.typography.bodySmall,
                color = extra.muted
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "system" to "System",
                    "light" to "Light",
                    "dark" to "Dark"
                ).forEach { (key, label) ->
                    WishPill(
                        text = label,
                        selected = state.uiTheme == key,
                        onClick = { viewModel.setUiTheme(key) }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("Storage usage")
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                val usage by viewModel.storageUsageState.collectAsStateWithLifecycle(null)
                val volumes by viewModel.diskVolumesState.collectAsStateWithLifecycle(emptyList())

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "App database",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${usage?.itemCount ?: 0} items · ${usage?.categoryCount ?: 0} categories · ${usage?.pricePointCount ?: 0} price points",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.muted
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = usage?.let { viewModel.formatBytes(it.databaseBytes) } ?: "–",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Backups",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${usage?.backupCount ?: 0} backup file${if (usage?.backupCount != 1) "s" else ""} kept (last 3)",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.muted
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = usage?.let { viewModel.formatBytes(it.backupBytes) } ?: "–",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Device storage",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface
                )
                Spacer(Modifier.height(4.dp))
                volumes.filter { !it.isRemovable }.forEach { volume ->
                    StorageVolumeRow(volume = volume, formatBytes = viewModel::formatBytes)
                }
                volumes.filter { it.isRemovable }.forEach { volume ->
                    StorageVolumeRow(volume = volume, formatBytes = viewModel::formatBytes)
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.loadStorageUsage()
                            viewModel.loadDiskVolumes()
                        },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, extra.hairline)
                    ) {
                        Text("Refresh", color = colors.onSurface)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("About")
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            Text(
                text = "Wishwell 1.0",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Everything you save lives in a private database on this phone. No account, no cloud, no tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = extra.muted
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Price history comes from reading public product pages plus anything you correct by hand. Some retailers block automated checks, so a failed check never changes your saved price.",
                style = MaterialTheme.typography.bodySmall,
                color = extra.muted
            )
            state.lastRefreshAt?.let { last ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Last price check: ${formatDateWithYear(last)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete everything?",
            body = "All saved items, notes and price history will be removed. Collections reset to the defaults.",
            confirmLabel = "Delete all",
            onConfirm = {
                viewModel.clearAll()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showCustomPicker) {
        val initial = if (isCustomAccentKey(state.accentColor)) state.accentColor else null
        AccentColorPickerDialog(
            initialHex = initial,
            onDismiss = { showCustomPicker = false },
            onApply = { hex ->
                viewModel.setAccentColor(hex)
                showCustomPicker = false
            },
            onResetDefault = {
                viewModel.setAccentColor("green")
                showCustomPicker = false
            }
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val extra = LocalWishExtraColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extra.hairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) colors.onSurface else extra.muted
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = extra.muted,
                uncheckedTrackColor = extra.sunken
            )
        )
    }
}

@Composable
private fun RowDivider() {
    Spacer(Modifier.height(10.dp))
    androidx.compose.material3.HorizontalDivider(
        color = LocalWishExtraColors.current.hairline.copy(alpha = 0.8f)
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun StorageVolumeRow(
    volume: WishlistRepository.DiskVolume,
    formatBytes: (Long) -> String
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = volume.label,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${if (volume.isRemovable) "Removable" else "Internal"} · ${volume.path}",
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "${formatBytes(volume.usedBytes)} used · ${formatBytes(volume.freeBytes)} free",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${volume.usedPercent.roundToInt()}% of ${formatBytes(volume.totalBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    androidx.compose.material3.LinearProgressIndicator(
        progress = volume.usedPercent.coerceIn(0f, 100f) / 100f,
        modifier = Modifier.fillMaxWidth(),
        color = colors.primary,
        trackColor = extra.hairline.copy(alpha = 0.5f)
    )
    Spacer(Modifier.height(12.dp))
}
