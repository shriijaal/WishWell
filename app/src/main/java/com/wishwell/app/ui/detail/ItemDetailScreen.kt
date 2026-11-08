package com.wishwell.app.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.webkit.WebView
import android.webkit.WebViewClient
import com.wishwell.app.data.model.CategoryWithCount
import com.wishwell.app.data.model.DealVerdict
import com.wishwell.app.data.model.ItemWithStats
import com.wishwell.app.data.model.PricePoint
import com.wishwell.app.data.model.assessDeal
import com.wishwell.app.data.net.CompareSearchState
import com.wishwell.app.data.net.FoundCompareProduct
import com.wishwell.app.data.net.StoreSearchLink
import com.wishwell.app.data.suggest.CrossStoreMatch
import com.wishwell.app.data.suggest.SimilarPick
import com.wishwell.app.di.AppContainer
import com.wishwell.app.ui.components.ConfirmDialog
import com.wishwell.app.ui.components.DealBadge
import com.wishwell.app.ui.components.LoadingSpinner
import com.wishwell.app.ui.components.PriceHistoryChart
import com.wishwell.app.ui.components.ProductImageCarousel
import com.wishwell.app.ui.components.ProductThumb
import com.wishwell.app.ui.components.SectionLabel
import com.wishwell.app.ui.components.StatTile
import com.wishwell.app.ui.components.WishDialog
import com.wishwell.app.ui.components.WishPill
import com.wishwell.app.ui.theme.LocalWishExtraColors
import com.wishwell.app.util.currencySymbol
import com.wishwell.app.util.formatDateWithYear
import com.wishwell.app.util.formatMoney
import com.wishwell.app.util.formatRelativeDay
import java.net.URI
import java.util.Locale

@Composable
fun ItemDetailRoute(
    container: AppContainer,
    itemId: Long,
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onTrackProduct: (String) -> Unit = {},
    onEdit: () -> Unit = {}
) {
    val viewModel: ItemDetailViewModel = viewModel(
        key = "detail-$itemId",
        factory = viewModelFactory {
            initializer { ItemDetailViewModel(container, itemId) }
        }
    )
    val item by viewModel.item.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val similar by viewModel.similar.collectAsStateWithLifecycle()
    val comparisons by viewModel.comparisons.collectAsStateWithLifecycle()
    val crossStoreMatches by viewModel.crossStoreMatches.collectAsStateWithLifecycle()
    val compareStates by viewModel.compareStates.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val current = item
    if (current == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        }
        return
    }

    ItemDetailContent(
        container = container,
        detail = current,
        history = history,
        categories = categories,
        similar = similar,
        comparisons = comparisons,
        crossStoreMatches = crossStoreMatches,
        compareStates = compareStates,
        busy = busy,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenItem = onOpenItem,
        onTrackProduct = onTrackProduct,
        onEdit = onEdit,
        onFindProducts = viewModel::findStoreProducts,
        onSearchAllStores = viewModel::searchAllStores,
        onRefresh = viewModel::refresh,
        onSetTarget = viewModel::setTargetPrice,
        onSetCategory = viewModel::setCategory,
        onSetNotes = viewModel::setNotes,
        onDelete = { viewModel.delete(onBack) },
        parsePrice = viewModel::parsePrice,
        onImportHistory = viewModel::importHistory
    )
}

@Composable
private fun ItemDetailContent(
    container: AppContainer,
    detail: ItemWithStats,
    history: List<PricePoint>,
    categories: List<CategoryWithCount>,
    similar: List<SimilarPick>,
    comparisons: List<StoreSearchLink>,
    crossStoreMatches: List<CrossStoreMatch>,
    compareStates: Map<String, CompareSearchState>,
    busy: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onTrackProduct: (String) -> Unit,
    onEdit: () -> Unit,
    onFindProducts: (StoreSearchLink) -> Unit,
    onSearchAllStores: () -> Unit,
    onRefresh: () -> Unit,
    onSetTarget: (Double?) -> Unit,
    onSetCategory: (Long?) -> Unit,
    onSetNotes: (String) -> Unit,
    onDelete: () -> Unit,
    parsePrice: (String) -> Double?,
    onImportHistory: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    val uriHandler = LocalUriHandler.current

    val item = detail.item
    val currency = item.currencyCode
    val assessment = assessDeal(detail)

    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.getOrNull()
            if (!text.isNullOrBlank()) onImportHistory(text)
        }
    }
    var rangeDays by remember { mutableStateOf(0) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var compareViewMode by remember { mutableStateOf("list") } // list, carousel, grid
    var inAppBrowser by remember { mutableStateOf<Pair<String, String>?>(null) } // url, title
    var showNotesDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var targetInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf(item.notes) }

    val visibleHistory = remember(history, rangeDays) {
        if (rangeDays == 0) history
        else history.filter { it.recordedAt >= System.currentTimeMillis() - rangeDays * 86_400_000L }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.onBackground
                    )
                }
                Text(
                    text = "SAVED ITEM",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = extra.muted,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Edit item",
                        tint = extra.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Remove item",
                        tint = extra.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = extra.hairline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            runCatching { uriHandler.openUri(item.productUrl) }
                        },
                        enabled = item.productUrl.isNotBlank(),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, extra.hairline)
                    ) {
                        Text("Open store", color = colors.onSurface)
                    }
                    Button(
                        onClick = onRefresh,
                        enabled = !busy,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (busy) "Checking…" else "Check price")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            ProductImageCarousel(
                imageUrls = item.imageUrls(),
                storeName = item.storeName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.storeName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted
                )
                if (detail.categoryName != null) {
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        shape = CircleShape,
                        color = extra.sunken
                    ) {
                        Text(
                            text = detail.categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.muted,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            val isLongTitle = item.title.length > 90 || item.title.count { it == '\n' } > 1
            var titleExpanded by remember(item.id) { mutableStateOf(false) }
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineLarge,
                color = colors.onBackground,
                maxLines = if (!isLongTitle || titleExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (isLongTitle) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { titleExpanded = !titleExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (titleExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (titleExpanded) "Show less" else "Show more",
                        tint = extra.muted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (titleExpanded) "Show less" else "Show full title",
                        style = MaterialTheme.typography.labelLarge,
                        color = extra.muted
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatMoney(detail.price, currency),
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.onBackground
                )
                if (detail.discountPercent != null) {
                    Spacer(Modifier.width(10.dp))
                    DealBadge(
                        text = "-${detail.discountPercent}%",
                        tone = if (detail.isAtLowest) {
                            com.wishwell.app.ui.components.BadgeTone.LOW
                        } else {
                            com.wishwell.app.ui.components.BadgeTone.GOOD
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val listPrice = item.listPrice
                    val currentPrice = detail.price
                    if (listPrice != null && currentPrice != null && listPrice > currentPrice + 0.01) {
                        Text(
                            text = formatMoney(listPrice, currency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = extra.muted,
                            textDecoration = TextDecoration.LineThrough
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = "Lowest ${formatMoney(detail.lowest, currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted
                    )
                }
                if (item.inStock != null) {
                    Spacer(Modifier.width(10.dp))
                    val inStock = item.inStock == true
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (inStock) {
                            colors.primary.copy(alpha = 0.12f)
                        } else {
                            extra.terracotta.copy(alpha = 0.16f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (inStock) colors.primary else extra.terracotta)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (inStock) "In stock" else "Out of stock",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (inStock) colors.primary else extra.terracotta
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val targetPrice = item.targetPrice
                val currentPrice = detail.price
                InfoTile(
                    label = "Target price",
                    value = targetPrice?.let { formatMoney(it, currency) } ?: "Not set",
                    hint = when {
                        targetPrice == null -> "Tap to add a target"
                        currentPrice != null && currentPrice <= targetPrice -> "Reached — at or below target"
                        currentPrice != null -> {
                            val gap = ((currentPrice - targetPrice) / targetPrice * 100).toInt()
                            "$gap% above your target"
                        }

                        else -> "Tap to edit"
                    },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        targetInput = editablePrice(targetPrice)
                        showTargetDialog = true
                    }
                )
                Box(modifier = Modifier.weight(1f)) {
                    val collectionValue = detail.categoryName ?: "Unsorted"
                    InfoTile(
                        label = "Collection",
                        value = collectionValue,
                        hint = "Tap to move",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { categoryMenuOpen = true }
                    )
                    DropdownMenu(
                        expanded = categoryMenuOpen,
                        onDismissRequest = { categoryMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Unsorted") },
                            onClick = {
                                onSetCategory(null)
                                categoryMenuOpen = false
                            }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.category.name) },
                                onClick = {
                                    onSetCategory(category.category.id)
                                    categoryMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            DealVerdictCard(assessment)

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "All-time low",
                    value = formatMoney(detail.lowest, currency),
                    caption = "Since ${formatRelativeDay(item.createdAt)}",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "All-time high",
                    value = formatMoney(detail.highest, currency),
                    caption = "Peak recorded",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Average",
                    value = formatMoney(detail.average, currency),
                    caption = "Across ${detail.pointCount} checks",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Below peak",
                    value = detail.discountPercent?.let { "$it%" } ?: "—",
                    caption = "Current discount",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(18.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, extra.hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PRICE HISTORY",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.muted,
                            modifier = Modifier.weight(1f)
                        )
                        listOf(30 to "1M", 90 to "3M", 0 to "All").forEach { (days, label) ->
                            Box(modifier = Modifier.padding(start = 6.dp)) {
                                WishPill(
                                    text = label,
                                    selected = rangeDays == days,
                                    onClick = { rangeDays = days }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    PriceHistoryChart(
                        points = visibleHistory,
                        currencyCode = currency
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${history.size} checks",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.muted,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = item.lastCheckedAt?.let { "Checked ${formatRelativeDay(it).lowercase()}" }
                                ?: "Not checked yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.muted
                        )
                    }
                    TextButton(
                        onClick = { importLauncher.launch(arrayOf("text/*", "application/json", "text/csv", "*/*")) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("Import history (CSV/JSON)", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                    if (item.lastCheckFailed) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "The last check couldn't read this page. The store may block automated requests — your saved price is still shown.",
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.terracotta
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            InfoTile(
                label = "Notes",
                value = item.notes.ifBlank { "No note yet" },
                hint = "Tap to edit",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    notesInput = item.notes
                    showNotesDialog = true
                }
            )

            if (item.description.isNotBlank() || item.specs().isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionLabel("Product details")
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, extra.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (item.description.isNotBlank()) {
                            var descExpanded by remember(item.id, item.description) { mutableStateOf(false) }
                            val longDesc = item.description.length > 220
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface,
                                maxLines = if (!longDesc || descExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (longDesc) {
                                Text(
                                    text = if (descExpanded) "Show less" else "Show more",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.primary,
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { descExpanded = !descExpanded }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }

                        val specs = item.specs()
                        if (specs.isNotEmpty()) {
                            if (item.description.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = extra.hairline.copy(alpha = 0.7f))
                                Spacer(Modifier.height(10.dp))
                            }
                            Text(
                                text = "SPECS",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.muted
                            )
                            Spacer(Modifier.height(8.dp))
                            specs.take(40).forEachIndexed { index, (k, v) ->
                                if (index > 0) Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = k,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = extra.muted,
                                        modifier = Modifier.weight(0.95f)
                                    )
                                    Text(
                                        text = v,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurface,
                                        modifier = Modifier.weight(1.05f)
                                    )
                                }
                            }
                            if (specs.size > 40) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "+${specs.size - 40} more specs",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.muted
                                )
                            }
                        }
                    }
                }
            }

            if (crossStoreMatches.isNotEmpty() || comparisons.isNotEmpty() || similar.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                SectionLabel("Compare prices & better picks")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "VIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.muted,
                        modifier = Modifier.weight(1f)
                    )
                    listOf(
                        "list" to Icons.Rounded.ViewList,
                        "carousel" to Icons.Rounded.ViewCarousel,
                        "grid" to Icons.Rounded.GridView
                    ).forEach { (mode, icon) ->
                        val sel = compareViewMode == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (sel) colors.primary.copy(alpha = 0.14f) else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { compareViewMode = mode }
                        ) {
                            Icon(
                                icon,
                                contentDescription = mode,
                                tint = if (sel) colors.primary else extra.muted,
                                modifier = Modifier.padding(6.dp).size(18.dp)
                            )
                        }
                    }
                }

                if (comparisons.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    val searchingStores = compareStates.values.any { it is CompareSearchState.Loading }
                    OutlinedButton(
                        onClick = onSearchAllStores,
                        enabled = !searchingStores,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (searchingStores) extra.hairline else colors.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (searchingStores) extra.muted else colors.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (searchingStores) "Searching stores…" else "Search stores for this item",
                            color = if (searchingStores) extra.muted else colors.primary
                        )
                    }
                }

                @Composable
                fun FoundProductCard(
                    product: FoundCompareProduct,
                    modifier: Modifier = Modifier,
                    compact: Boolean = false
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, extra.hairline),
                        modifier = modifier.clip(RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (compact) {
                                ProductThumb(
                                    imageUrl = product.imageUrl,
                                    storeName = product.storeName,
                                    modifier = Modifier.fillMaxWidth().height(74.dp),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ProductThumb(
                                        imageUrl = product.imageUrl,
                                        storeName = product.storeName,
                                        modifier = Modifier.size(58.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(product.storeName.uppercase(), style = MaterialTheme.typography.labelSmall, color = extra.muted, maxLines = 1)
                                        Text(product.title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            if (compact) {
                                Text(product.storeName.uppercase(), style = MaterialTheme.typography.labelSmall, color = extra.muted, maxLines = 1)
                                Text(product.title, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                            }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = formatMoney(product.price, product.currencyCode),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                if (product.listPrice != null && product.price != null && product.listPrice > product.price + 0.01) {
                                    Text(
                                        text = formatMoney(product.listPrice, product.currencyCode),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extra.muted,
                                        textDecoration = TextDecoration.LineThrough
                                    )
                                }
                            }
                            val currentPrice = item.currentPrice
                            if (product.price != null && currentPrice != null &&
                                product.currencyCode.equals(item.currencyCode, ignoreCase = true)
                            ) {
                                val diff = currentPrice - product.price
                                val label = when {
                                    diff > 0.01 -> "Lower than saved"
                                    diff < -0.01 -> "Higher than saved"
                                    else -> "Same price"
                                }
                                val labelColor = when {
                                    diff > 0.01 -> extra.moss
                                    diff < -0.01 -> extra.terracotta
                                    else -> extra.muted
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = labelColor,
                                    maxLines = 1
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = extra.sunken.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { inAppBrowser = product.productUrl to "${product.storeName} · details" }
                                ) {
                                    Text(
                                        "Details",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.primary.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onTrackProduct(product.productUrl) }
                                ) {
                                    Icon(
                                        Icons.Rounded.Add,
                                        contentDescription = "Track this product",
                                        tint = colors.primary,
                                        modifier = Modifier.padding(7.dp).size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                @Composable
                fun FoundProductsLayout(products: List<FoundCompareProduct>) {
                    when (compareViewMode) {
                        "carousel" -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(products, key = { it.productUrl }) { p ->
                                FoundProductCard(p, modifier = Modifier.width(188.dp), compact = true)
                            }
                        }
                        "grid" -> products.chunked(2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                rowItems.forEach { p -> FoundProductCard(p, modifier = Modifier.weight(1f), compact = true) }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        else -> products.forEach { p ->
                            FoundProductCard(p, modifier = Modifier.fillMaxWidth(), compact = false)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                if (comparisons.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))

                    comparisons.forEach { link ->
                        val state = compareStates[link.storeName] ?: CompareSearchState.Idle
                        // Hidden by default; show only when searching/results are available.
                        if (state is CompareSearchState.Idle || state is CompareSearchState.Empty || state is CompareSearchState.Error) {
                            return@forEach
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, extra.hairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = link.storeName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (state is CompareSearchState.Loading) {
                                        LoadingSpinner(size = 14.dp)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = if (state is CompareSearchState.Success) "Refresh" else "Find products",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onFindProducts(link) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Search in-app: \"${container.recommendations.searchQuery(item)}\"",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                when (state) {
                                    is CompareSearchState.Idle -> {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "Open search in-app",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = extra.muted,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { inAppBrowser = link.url to "Compare on ${link.storeName}" }
                                                .padding(vertical = 2.dp)
                                        )
                                    }
                                    is CompareSearchState.Loading -> {
                                        Spacer(Modifier.height(8.dp))
                                        Text("Finding actual products…", style = MaterialTheme.typography.bodySmall, color = extra.muted)
                                    }
                                    is CompareSearchState.Success -> {
                                        Spacer(Modifier.height(10.dp))
                                        FoundProductsLayout(state.products)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Open full search in-app",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = extra.muted,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { inAppBrowser = link.url to "Compare on ${link.storeName}" }
                                                .padding(vertical = 2.dp)
                                        )
                                    }
                                    is CompareSearchState.Empty, is CompareSearchState.Error -> Unit
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                val betterPicks = (crossStoreMatches.map { it.item to it.reason } + similar.map { it.item to it.reason })
                    .distinctBy { it.first.item.id }

                if (betterPicks.isNotEmpty()) {
                    Spacer(Modifier.height(if (comparisons.isNotEmpty()) 2.dp else 12.dp))
                    Text(
                        text = "BETTER PICKS & PRICES (SAVED)",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.muted
                    )
                    Spacer(Modifier.height(6.dp))

                    @Composable
                    fun PickCard(pickItem: ItemWithStats, reason: String, modifier: Modifier = Modifier, compact: Boolean = false) {
                        val canOpenPage = pickItem.item.productUrl.isNotBlank()
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, extra.hairline),
                            modifier = modifier.clip(RoundedCornerShape(16.dp))
                        ) {
                            if (compact) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box(modifier = Modifier.clickable { onOpenItem(pickItem.item.id) }) {
                                        ProductThumb(
                                            imageUrl = pickItem.item.imageUrl,
                                            storeName = pickItem.item.storeName,
                                            modifier = Modifier.fillMaxWidth().height(72.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Column(modifier = Modifier.clickable { onOpenItem(pickItem.item.id) }) {
                                        Text(pickItem.item.title, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(2.dp))
                                        Text(formatMoney(pickItem.price, pickItem.item.currencyCode), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
                                        Text(reason, style = MaterialTheme.typography.labelSmall, color = extra.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (canOpenPage) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "Open page in-app",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.primary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { inAppBrowser = pickItem.item.productUrl to pickItem.item.title }
                                                .padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProductThumb(
                                        imageUrl = pickItem.item.imageUrl,
                                        storeName = pickItem.item.storeName,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onOpenItem(pickItem.item.id) },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f).clickable { onOpenItem(pickItem.item.id) }) {
                                        Text(pickItem.item.title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(3.dp))
                                        Text("${formatMoney(pickItem.price, pickItem.item.currencyCode)} · ${pickItem.item.storeName}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(reason, style = MaterialTheme.typography.labelSmall, color = extra.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (canOpenPage) {
                                        IconButton(onClick = { inAppBrowser = pickItem.item.productUrl to pickItem.item.title }) {
                                            Icon(Icons.Rounded.OpenInNew, contentDescription = "Open product page in-app", tint = extra.muted, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    when (compareViewMode) {
                        "carousel" -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(betterPicks, key = { it.first.item.id }) { (pickItem, reason) ->
                                PickCard(pickItem = pickItem, reason = reason, modifier = Modifier.width(184.dp), compact = true)
                            }
                        }
                        "grid" -> betterPicks.chunked(2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                rowItems.forEach { (pickItem, reason) ->
                                    PickCard(pickItem = pickItem, reason = reason, modifier = Modifier.weight(1f), compact = true)
                                }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        else -> betterPicks.forEach { (pickItem, reason) ->
                            PickCard(pickItem = pickItem, reason = reason, modifier = Modifier.fillMaxWidth(), compact = false)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            if (history.size >= 2) {
                Spacer(Modifier.height(26.dp))
                SectionLabel("Recent price changes")
                Spacer(Modifier.height(6.dp))
                val recent = history.takeLast(7).reversed()
                recent.forEach { point ->
                    val index = history.indexOfFirst { it.id == point.id }
                    val previous = if (index > 0) history[index - 1] else null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDateWithYear(point.recordedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.muted,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatMoney(point.price, point.currencyCode),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            color = colors.onSurface
                        )
                        if (previous != null && previous.price != point.price) {
                            Spacer(Modifier.width(10.dp))
                            val down = point.price < previous.price
                            val changeColor = if (down) extra.moss else extra.terracotta
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (down) Icons.Rounded.TrendingDown else Icons.Rounded.TrendingUp,
                                    contentDescription = null,
                                    tint = changeColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = formatMoney(kotlin.math.abs(point.price - previous.price), currency),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = changeColor
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = extra.hairline.copy(alpha = 0.7f))
                }
            }

            Spacer(Modifier.height(22.dp))
            val host = remember(item.productUrl) {
                runCatching { URI(item.productUrl).host }.getOrNull()
            }
            if (host != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { runCatching { uriHandler.openUri(item.productUrl) } }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Source: $host",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.muted
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = extra.muted,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            TextButton(onClick = { showDeleteDialog = true }) {
                Text("Remove from wishlist", color = colors.error)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showTargetDialog) {
        WishDialog(
            title = "Target price",
            confirmLabel = "Save",
            confirmEnabled = targetInput.isBlank() || parsePrice(targetInput) != null,
            onDismiss = { showTargetDialog = false },
            onConfirm = {
                onSetTarget(targetInput.trim().takeIf { it.isNotEmpty() }?.let(parsePrice))
                showTargetDialog = false
            }
        ) {
            Column {
                Text(
                    "We'll highlight this item when it reaches your target.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { targetInput = it },
                    label = { Text("Target price (${currencySymbol(currency)})") },
                    singleLine = true,
                    isError = targetInput.isNotBlank() && parsePrice(targetInput) == null,
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = extra.hairline
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Leave empty to clear the target.",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted
                )
            }
        }
    }

    if (showNotesDialog) {
        WishDialog(
            title = "Note",
            confirmLabel = "Save",
            onDismiss = { showNotesDialog = false },
            onConfirm = {
                onSetNotes(notesInput)
                showNotesDialog = false
            }
        ) {
            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it.take(600) },
                label = { Text("Size, colour, why you want it…") },
                minLines = 3,
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = extra.hairline
                )
            )
        }
    }

    inAppBrowser?.let { (url, title) ->
        InAppBrowserDialog(
            url = url,
            title = title,
            onDismiss = { inAppBrowser = null }
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Remove this item?",
            body = "Its price history goes with it. This can't be undone.",
            confirmLabel = "Remove",
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun DealVerdictCard(assessment: com.wishwell.app.data.model.DealAssessment) {
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    val background = when (assessment.verdict) {
        DealVerdict.ALL_TIME_LOW -> extra.lime
        DealVerdict.GOOD -> extra.moss.copy(alpha = 0.14f)
        DealVerdict.HIGH -> extra.terracotta.copy(alpha = 0.12f)
        DealVerdict.FAIR, DealVerdict.UNKNOWN -> extra.sunken
    }
    val contentColor = if (assessment.verdict == DealVerdict.ALL_TIME_LOW) extra.limeInk else colors.onSurface

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(contentColor, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = assessment.headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = assessment.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun InfoTile(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val extra = LocalWishExtraColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extra.hairline),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted
            )
        }
    }
}

@Composable
private fun SimilarRow(pick: SimilarPick, onClick: () -> Unit) {
    val extra = LocalWishExtraColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extra.hairline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductThumb(
                imageUrl = pick.item.item.imageUrl,
                storeName = pick.item.item.storeName,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pick.item.item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatMoney(pick.item.price, pick.item.item.currencyCode),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = pick.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun editablePrice(value: Double?): String {
    if (value == null) return ""
    return if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.2f", value)
}

@Composable
private fun InAppBrowserDialog(
    url: String,
    title: String,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = colors.background,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.onSurface)
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(color = extra.hairline)
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.userAgentString = settings.userAgentString?.replace("; wv", "") ?: settings.userAgentString
                            webViewClient = WebViewClient()
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
