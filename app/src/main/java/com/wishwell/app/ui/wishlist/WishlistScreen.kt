package com.wishwell.app.ui.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wishwell.app.data.model.ItemWithStats
import com.wishwell.app.di.AppContainer
import com.wishwell.app.ui.components.DealBadge
import com.wishwell.app.ui.components.EmptyState
import com.wishwell.app.ui.components.LoadingSpinner
import com.wishwell.app.ui.components.NoticeBar
import com.wishwell.app.ui.components.ConfirmDialog
import com.wishwell.app.ui.components.ProductThumb
import com.wishwell.app.ui.components.SectionLabel
import com.wishwell.app.ui.components.StatTile
import com.wishwell.app.ui.components.SwipeableWishCard
import com.wishwell.app.ui.components.WishCard
import com.wishwell.app.ui.components.WishPill
import com.wishwell.app.ui.components.categoryIcon
import com.wishwell.app.ui.theme.LocalWishExtraColors
import com.wishwell.app.util.currencyDisplayName
import com.wishwell.app.util.formatMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    container: AppContainer,
    onOpenItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (Long) -> Unit,
    onSelectionModeChanged: (Boolean) -> Unit = {}
) {
    val viewModel: WishlistViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WishlistViewModel(container) }
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extra = LocalWishExtraColors.current
    var viewMode by remember { mutableStateOf("list") } // list, grid
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(state.selectionMode) { onSelectionModeChanged(state.selectionMode) }
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) { viewModel.refreshAll() }
    }
    LaunchedEffect(state.refreshing) {
        if (state.refreshing) pullState.startRefresh() else pullState.endRefresh()
    }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var revealedItemId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.selectionMode) { if (state.selectionMode) revealedItemId = null }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) revealedItemId = null }

    val viewConfig = LocalViewConfiguration.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .nestedScroll(pullState.nestedScrollConnection)
            .pointerInput(revealedItemId) {
                if (revealedItemId == null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation()
                    if (up != null && revealedItemId != null) {
                        val moved = (up.position - down.position).getDistance()
                        if (moved < viewConfig.touchSlop && !up.isConsumed) revealedItemId = null
                    }
                }
            }
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = if (state.selectionMode) 190.dp else 120.dp)
    ) {
        item(key = "header") {
            WishlistHeader(state = state)
        }



        state.message?.let { notice ->
            item(key = "notice") {
                NoticeBar(
                    text = notice,
                    onDismiss = viewModel::clearMessage,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
        }

        if (state.hasAnyItems) {
            item(key = "search") {
                SearchAndSort(
                    state = state,
                    onQueryChange = viewModel::setQuery,
                    onSortChange = viewModel::setSort
                )
            }
            item(key = "attention") {
                AttentionFilters(
                    state = state,
                    onSelect = viewModel::setAttentionFilter
                )
            }
            item(key = "chips") {
                CategoryChips(
                    state = state,
                    onSelect = viewModel::setCategory
                )
            }
            item(key = "count") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val filterNote = buildList {
                        if (state.query.isNotBlank()) add("search")
                        if (state.categoryId != null) add("collection")
                        if (state.attentionFilter != AttentionFilter.ALL) add("filter")
                    }
                    Text(
                        text = if (filterNote.isEmpty()) "${state.items.size} shown"
                        else "${state.items.size} shown · ${filterNote.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.muted,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ViewList,
                            contentDescription = "List view",
                            tint = if (viewMode == "list") MaterialTheme.colorScheme.primary else extra.muted,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewMode = "list" }
                                .padding(4.dp)
                        )
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = "Grid view",
                            tint = if (viewMode == "grid") MaterialTheme.colorScheme.primary else extra.muted,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewMode = "grid" }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }

        val showAttentionSection = !state.isFiltered &&
            state.attentionFilter == AttentionFilter.ALL &&
            state.sort == SortMode.RECENT &&
            state.items.any(::needsAttention) &&
            state.items.any { !needsAttention(it) }

        when {
            state.loading -> item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingSpinner(size = 26.dp)
                }
            }

            !state.hasAnyItems -> item(key = "empty") {
                EmptyState(
                    title = "Nothing saved yet",
                    body = "Paste a product link from any store, or add it by hand. Wishwell watches the price so you don't have to.",
                    actionLabel = "Save your first item",
                    onAction = onAddItem,
                    secondaryLabel = "Load a demo list",
                    onSecondary = viewModel::loadDemoData
                )
            }

            state.items.isEmpty() -> item(key = "no-matches") {
                EmptyState(
                    title = "No matches",
                    body = "Nothing in your list fits that search, filter, or collection.",
                    actionLabel = "Clear filters",
                    onAction = viewModel::clearFilters
                )
            }

            viewMode == "grid" -> {
                if (showAttentionSection) {
                    val attention = state.items.filter(::needsAttention)
                    val rest = state.items.filter { !needsAttention(it) }
                    item(key = "section-attention") {
                        SectionLabel(
                            text = "Needs attention",
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                            trailing = {
                                Text(
                                    text = "${attention.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.muted
                                )
                            }
                        )
                    }
                    gridRows(
                        items = attention,
                        keyPrefix = "attn",
                        selectedIds = state.selectedIds,
                        selectionMode = state.selectionMode,
                        onOpenItem = onOpenItem,
                        onToggleSelection = viewModel::toggleSelection,
                        onEnterSelection = viewModel::enterSelection
                    )
                    item(key = "section-library") {
                        SectionLabel(
                            text = "Library",
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)
                        )
                    }
                    gridRows(
                        items = rest,
                        keyPrefix = "lib",
                        selectedIds = state.selectedIds,
                        selectionMode = state.selectionMode,
                        onOpenItem = onOpenItem,
                        onToggleSelection = viewModel::toggleSelection,
                        onEnterSelection = viewModel::enterSelection
                    )
                } else {
                    gridRows(
                        items = state.items,
                        keyPrefix = "grid",
                        selectedIds = state.selectedIds,
                        selectionMode = state.selectionMode,
                        onOpenItem = onOpenItem,
                        onToggleSelection = viewModel::toggleSelection,
                        onEnterSelection = viewModel::enterSelection
                    )
                }
            }

            else -> {
                if (showAttentionSection) {
                    val attention = state.items.filter(::needsAttention)
                    val rest = state.items.filter { !needsAttention(it) }
                    item(key = "section-attention") {
                        SectionLabel(
                            text = "Needs attention",
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                            trailing = {
                                Text(
                                    text = "${attention.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.muted
                                )
                            }
                        )
                    }
                    items(attention, key = { "attn-${it.item.id}" }) { item ->
                        val id = item.item.id
                        val selected = id in state.selectedIds
                        SwipeableWishCard(
                            item = item,
                            onClick = {
                                when {
                                    revealedItemId != null && revealedItemId != id -> revealedItemId = null
                                    state.selectionMode -> viewModel.toggleSelection(id)
                                    else -> onOpenItem(id)
                                }
                            },
                            onEdit = { onEditItem(id) },
                            onRemove = { pendingDeleteId = id },
                            selected = selected,
                            selectionMode = state.selectionMode,
                            onLongClick = {
                                revealedItemId = null
                                if (!state.selectionMode) viewModel.enterSelection(id) else viewModel.toggleSelection(id)
                            },
                            swipeEnabled = !state.selectionMode,
                            isRevealed = revealedItemId == id,
                            onRevealedChange = { open -> revealedItemId = if (open) id else if (revealedItemId == id) null else revealedItemId },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                        )
                    }
                    item(key = "section-library") {
                        SectionLabel(
                            text = "Library",
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)
                        )
                    }
                    items(rest, key = { "lib-${it.item.id}" }) { item ->
                        val id = item.item.id
                        val selected = id in state.selectedIds
                        SwipeableWishCard(
                            item = item,
                            onClick = {
                                when {
                                    revealedItemId != null && revealedItemId != id -> revealedItemId = null
                                    state.selectionMode -> viewModel.toggleSelection(id)
                                    else -> onOpenItem(id)
                                }
                            },
                            onEdit = { onEditItem(id) },
                            onRemove = { pendingDeleteId = id },
                            selected = selected,
                            selectionMode = state.selectionMode,
                            onLongClick = {
                                revealedItemId = null
                                if (!state.selectionMode) viewModel.enterSelection(id) else viewModel.toggleSelection(id)
                            },
                            swipeEnabled = !state.selectionMode,
                            isRevealed = revealedItemId == id,
                            onRevealedChange = { open -> revealedItemId = if (open) id else if (revealedItemId == id) null else revealedItemId },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                        )
                    }
                } else {
                    items(state.items, key = { it.item.id }) { item ->
                        val id = item.item.id
                        val selected = id in state.selectedIds
                        SwipeableWishCard(
                            item = item,
                            onClick = {
                                when {
                                    revealedItemId != null && revealedItemId != id -> revealedItemId = null
                                    state.selectionMode -> viewModel.toggleSelection(id)
                                    else -> onOpenItem(id)
                                }
                            },
                            onEdit = { onEditItem(id) },
                            onRemove = { pendingDeleteId = id },
                            selected = selected,
                            selectionMode = state.selectionMode,
                            onLongClick = {
                                revealedItemId = null
                                if (!state.selectionMode) viewModel.enterSelection(id) else viewModel.toggleSelection(id)
                            },
                            swipeEnabled = !state.selectionMode,
                            isRevealed = revealedItemId == id,
                            onRevealedChange = { open -> revealedItemId = if (open) id else if (revealedItemId == id) null else revealedItemId },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
        PullToRefreshContainer(
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = state.selectionMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 }
        ) {
            SelectionBar(
                count = state.selectedIds.size,
                onCancel = viewModel::clearSelection,
                onSelectAll = { viewModel.selectAll(state.items.map { it.item.id }) },
                onMove = { showMoveDialog = true },
                onRemove = { showBulkDeleteConfirm = true },
                floating = true
            )
        }

        pendingDeleteId?.let { id ->
            ConfirmDialog(
                title = "Remove item?",
                body = "This saved item will be removed from your wishlist.",
                confirmLabel = "Remove",
                onConfirm = {
                    viewModel.deleteItem(id)
                    pendingDeleteId = null
                },
                onDismiss = { pendingDeleteId = null }
            )
        }
        if (showBulkDeleteConfirm && state.selectionMode) {
            ConfirmDialog(
                title = "Remove ${state.selectedIds.size} items?",
                body = "The selected items will be removed from your wishlist.",
                confirmLabel = "Remove",
                onConfirm = {
                    viewModel.deleteSelected()
                    showBulkDeleteConfirm = false
                },
                onDismiss = { showBulkDeleteConfirm = false }
            )
        }
        if (showMoveDialog && state.selectionMode) {
            AlertDialog(
                onDismissRequest = { showMoveDialog = false },
                title = { Text("Move to collection") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = {
                            viewModel.moveSelectedToCategory(null)
                            showMoveDialog = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Unsorted", modifier = Modifier.fillMaxWidth())
                        }
                        state.categories.forEach { cat ->
                            TextButton(onClick = {
                                viewModel.moveSelectedToCategory(cat.category.id)
                                showMoveDialog = false
                            }, modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(categoryIcon(cat.category.name), contentDescription = null, modifier = Modifier.size(16.dp), tint = extra.muted)
                                    Spacer(Modifier.width(8.dp))
                                    Text(cat.category.name)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showMoveDialog = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    floating: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (floating) colors.surface else extra.sunken.copy(alpha = 0.55f),
        tonalElevation = if (floating) 6.dp else 0.dp,
        shadowElevation = if (floating) 10.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (floating) extra.hairline.copy(alpha = 0.9f) else extra.hairline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel selection", tint = extra.muted, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "$count selected",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSelectAll) { Text("Select all", color = colors.primary) }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onMove,
                    shape = RoundedCornerShape(12.dp),
                    color = colors.primary.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.DriveFileMove, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Move", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                }
                Surface(
                    onClick = onRemove,
                    shape = RoundedCornerShape(12.dp),
                    color = extra.terracotta.copy(alpha = 0.14f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = extra.terracotta, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove", style = MaterialTheme.typography.labelLarge, color = extra.terracotta)
                    }
                }
            }
        }
    }
}

@Composable
private fun WishlistHeader(state: WishlistUiState) {
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    val summary = state.summary

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "WISHWELL",
            style = MaterialTheme.typography.titleLarge.copy(
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold
            ),
            color = colors.onBackground
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Your library, watched for price drops.",
            style = MaterialTheme.typography.bodyMedium,
            color = extra.muted
        )

        if (summary.itemCount > 0) {
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatTile(
                    label = "Tracked",
                    value = if (summary.trackedValue > 0.0) formatMoney(summary.trackedValue, summary.currencyCode)
                    else "${summary.itemCount}",
                    caption = if (summary.trackedValue > 0.0) "${summary.itemCount} item${if (summary.itemCount == 1) "" else "s"}" else "items saved",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Below peak",
                    value = if (summary.belowPeak >= 1.0) formatMoney(summary.belowPeak, summary.currencyCode) else "—",
                    caption = if (summary.belowPeak >= 1.0) "potential savings" else "no drops yet",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatTile(
                    label = "All-time lows",
                    value = summary.atLowCount.toString(),
                    caption = if (summary.atLowCount == 1) "item at its low" else "items at lows",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Deals",
                    value = maxOf(summary.onSaleCount, summary.belowTargetCount).toString(),
                    caption = when {
                        summary.belowTargetCount > 0 -> "${summary.belowTargetCount} below target"
                        summary.onSaleCount > 0 -> "on sale / dropped"
                        summary.checkFailedCount > 0 -> "${summary.checkFailedCount} need a check"
                        else -> "nothing flagged"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            if (summary.mixedCurrencies) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Totals use ${currencyDisplayName(summary.currencyCode)}. Other currencies aren't summed.",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted
                )
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SearchAndSort(
    state: WishlistUiState,
    onQueryChange: (String) -> Unit,
    onSortChange: (SortMode) -> Unit
) {
    val extra = LocalWishExtraColors.current
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search your list", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = extra.muted,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = if (state.query.isNotBlank()) {
                {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = extra.muted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = extra.hairline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        Box {
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { menuOpen = true },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, extra.hairline)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.SwapVert,
                        contentDescription = "Sort",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                SortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label, style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            onSortChange(mode)
                            menuOpen = false
                        },
                        trailingIcon = {
                            if (mode == state.sort) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun needsAttention(item: ItemWithStats): Boolean {
    val price = item.price
    val belowTarget = item.item.targetPrice?.let { t -> price != null && price <= t + 0.005 } == true
    return item.isAtLowest ||
        (item.discountPercent ?: 0) > 0 ||
        belowTarget ||
        item.item.lastCheckFailed
}

private fun LazyListScope.gridRows(
    items: List<ItemWithStats>,
    keyPrefix: String,
    selectedIds: Set<Long>,
    selectionMode: Boolean,
    onOpenItem: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onEnterSelection: (Long) -> Unit
) {
    val rows = items.chunked(2)
    items(rows, key = { row -> "$keyPrefix-${row.joinToString("-") { it.item.id.toString() }}" }) { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            row.forEach { item ->
                val id = item.item.id
                val selected = id in selectedIds
                WishlistGridCard(
                    item = item,
                    onClick = { if (selectionMode) onToggleSelection(id) else onOpenItem(id) },
                    selected = selected,
                    selectionMode = selectionMode,
                    onLongClick = { if (!selectionMode) onEnterSelection(id) else onToggleSelection(id) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AttentionFilters(
    state: WishlistUiState,
    onSelect: (AttentionFilter) -> Unit
) {
    val options = buildList {
        add(AttentionFilter.ALL)
        if (state.summary.onSaleCount > 0) add(AttentionFilter.ON_SALE)
        if (state.summary.atLowCount > 0) add(AttentionFilter.AT_LOW)
        if (state.summary.belowTargetCount > 0) add(AttentionFilter.BELOW_TARGET)
        if (state.summary.checkFailedCount > 0) add(AttentionFilter.CHECK_FAILED)
    }
    if (options.size <= 1) return

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options, key = { it.name }) { filter ->
            WishPill(
                text = filter.label,
                selected = state.attentionFilter == filter,
                onClick = { onSelect(filter) }
            )
        }
        item(key = "attn-end") { Spacer(Modifier.width(4.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WishlistGridCard(
    item: ItemWithStats,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    val price = item.price
    val listPrice = item.item.listPrice
    val borderColor = if (selected) colors.primary else extra.hairline

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (onLongClick != null || selectionMode) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else Modifier.clickable(onClick = onClick)
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.primary.copy(alpha = 0.08f) else colors.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box {
                ProductThumb(
                    imageUrl = item.item.imageUrl,
                    storeName = item.item.storeName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(14.dp)
                )
                val badge = when {
                    item.isAtLowest -> "Low" to com.wishwell.app.ui.components.BadgeTone.LOW
                    item.discountPercent != null && item.discountPercent!! >= 1 ->
                        "-${item.discountPercent}%" to com.wishwell.app.ui.components.BadgeTone.GOOD
                    item.item.lastCheckFailed -> "!" to com.wishwell.app.ui.components.BadgeTone.HIGH
                    else -> null
                }
                if (badge != null) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                        com.wishwell.app.ui.components.DealBadge(badge.first, badge.second)
                    }
                }
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors.primary else colors.surface.copy(alpha = 0.92f))
                            .border(1.dp, if (selected) colors.primary else extra.hairline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = "Selected",
                                tint = colors.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.item.storeName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.item.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatMoney(price, item.item.currencyCode),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 1
            )
            if (listPrice != null && price != null && listPrice > price + 0.01) {
                Text(
                    text = formatMoney(listPrice, item.item.currencyCode),
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CategoryChips(
    state: WishlistUiState,
    onSelect: (Long?) -> Unit
) {
    val hasUnsorted = state.allItems.any { it.item.categoryId == null }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            WishPill(
                text = "All",
                selected = state.categoryId == null,
                onClick = { onSelect(null) }
            )
        }
        items(state.categories, key = { it.category.id }) { category ->
            WishPill(
                text = category.category.name,
                leadingIcon = categoryIcon(category.category.name),
                selected = state.categoryId == category.category.id,
                onClick = { onSelect(category.category.id) }
            )
        }
        if (hasUnsorted) {
            item(key = "unsorted") {
                WishPill(
                    text = "Unsorted",
                    selected = state.categoryId == -1L,
                    onClick = { onSelect(-1L) }
                )
            }
        }
        item(key = "spacer-end") {
            Spacer(Modifier.width(4.dp))
        }
    }
}
