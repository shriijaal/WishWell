package com.wishwell.app.ui.tracking

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wishwell.app.di.AppContainer
import com.wishwell.app.ui.components.ConfirmDialog
import com.wishwell.app.ui.components.EmptyState
import com.wishwell.app.ui.components.LoadingSpinner
import com.wishwell.app.ui.components.NoticeBar
import com.wishwell.app.ui.components.ReorderItemsSheet
import com.wishwell.app.ui.components.SectionLabel
import com.wishwell.app.ui.components.StatTile
import com.wishwell.app.ui.components.SwipeableWishCard
import com.wishwell.app.ui.components.categoryIcon
import com.wishwell.app.ui.theme.LocalWishExtraColors
import com.wishwell.app.util.currencyDisplayName
import com.wishwell.app.util.formatMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceTrackingScreen(
    container: AppContainer,
    onOpenItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (Long) -> Unit
) {
    val viewModel: PriceTrackingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PriceTrackingViewModel(container) }
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) { viewModel.refreshAll() }
    }
    LaunchedEffect(state.refreshing) {
        if (state.refreshing) pullState.startRefresh() else pullState.endRefresh()
    }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showReorderSheet by remember { mutableStateOf(false) }
    var revealedItemId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.selectionMode) {
        if (state.selectionMode) {
            revealedItemId = null
            showReorderSheet = true
        } else {
            showReorderSheet = false
        }
    }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) revealedItemId = null }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .nestedScroll(pullState.nestedScrollConnection)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 6.dp, bottom = if (state.selectionMode) 190.dp else 120.dp)
        ) {
            item(key = "header") {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "PRICE TRACKING",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                        color = colors.onBackground
                    )

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Price watch.",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Sales, drops, all-time lows, and targets reached — all in one place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = extra.muted
                )
                Spacer(Modifier.height(18.dp))

                if (state.hasAnyItems) {
                    val summary = state.summary
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            label = "Tracked",
                            value = summary.trackedCount.toString(),
                            caption = "Saved items",
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "At low",
                            value = summary.atLowCount.toString(),
                            caption = "All-time lows",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            label = "Targets hit",
                            value = summary.targetReachedCount.toString(),
                            caption = "At or below target",
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "Below peak",
                            value = if (summary.belowPeakTotal >= 1.0) {
                                formatMoney(summary.belowPeakTotal, summary.currencyCode)
                            } else {
                                "—"
                            },
                            caption = "Potential savings",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (summary.mixedCurrencies) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Totals use ${currencyDisplayName(summary.currencyCode)}. Other currencies aren't summed.",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.muted
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
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
                    title = "Nothing to track yet",
                    body = "Save items to your wishlist and Wishwell will watch their prices here.",
                    actionLabel = "Save your first item",
                    onAction = onAddItem
                )
            }

            !state.hasSignals -> item(key = "calm") {
                EmptyState(
                    title = "No price moves right now",
                    body = "We'll show sales, drops, all-time lows, and target hits here as they happen. Tap refresh to check prices now.",
                    actionLabel = "Refresh prices",
                    onAction = viewModel::refreshAll
                )
            }

            else -> {
                if (state.targetReached.isNotEmpty()) {
                    item(key = "target-label") {
                        SectionLabel(
                            text = "Target reached",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(state.targetReached, key = { "target-${it.item.id}" }) { item ->
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

                if (state.atLow.isNotEmpty()) {
                    item(key = "low-label") {
                        SectionLabel(
                            text = "All-time lows",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(state.atLow, key = { "low-${it.item.id}" }) { item ->
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

                if (state.onSale.isNotEmpty()) {
                    item(key = "drops-label") {
                        SectionLabel(
                            text = "On sale now · ${state.onSale.size}",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(state.onSale, key = { "drop-${it.item.id}" }) { item ->
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

                if (state.checkFailed.isNotEmpty()) {
                    item(key = "failed-label") {
                        SectionLabel(
                            text = "Needs attention",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    item(key = "failed-note") {
                        Text(
                            text = "These items couldn't be checked. Some stores block automated price reads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.muted,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                        )
                    }
                    items(state.checkFailed, key = { "failed-${it.item.id}" }) { item ->
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
            containerColor = colors.surface,
            contentColor = colors.primary
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = state.selectionMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 }
        ) {
            TrackingSelectionBar(
                count = state.selectedIds.size,
                onCancel = viewModel::clearSelection,
                onSelectAll = { viewModel.selectAll(state.allItems.map { it.item.id }) },
                onReorder = { showReorderSheet = true },
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
        if (showReorderSheet && state.selectionMode) {
            ReorderItemsSheet(
                items = state.allItems,
                selectedCount = state.selectedIds.size,
                onSelectAll = { viewModel.selectAll(state.allItems.map { it.item.id }) },
                onSave = { orderedIds ->
                    viewModel.persistVisibleOrder(orderedIds)
                    showReorderSheet = false
                    viewModel.clearSelection()
                },
                onDismiss = { showReorderSheet = false }
            )
        }
    }
}

@Composable
private fun TrackingSelectionBar(
    count: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onReorder: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    floating: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (floating) colors.surface else extra.sunken.copy(alpha = 0.55f),
        tonalElevation = if (floating) 6.dp else 0.dp,
        shadowElevation = if (floating) 10.dp else 0.dp,
        border = BorderStroke(1.dp, if (floating) extra.hairline.copy(alpha = 0.9f) else extra.hairline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel selection", tint = extra.muted, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "$count selected",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSelectAll) {
                    Text("Select all", color = colors.primary)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(onClick = onReorder, shape = RoundedCornerShape(12.dp), color = extra.sunken.copy(alpha = 0.55f), border = BorderStroke(1.dp, extra.hairline), modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SwapVert, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reorder", style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                    }
                }
                Surface(onClick = onMove, shape = RoundedCornerShape(12.dp), color = colors.primary.copy(alpha = 0.12f), modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DriveFileMove, contentDescription = null, tint = colors.primary, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Move", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                }
                Surface(onClick = onRemove, shape = RoundedCornerShape(12.dp), color = extra.terracotta.copy(alpha = 0.14f), modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = extra.terracotta, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove", style = MaterialTheme.typography.labelLarge, color = extra.terracotta)
                    }
                }
            }
        }
    }
}
