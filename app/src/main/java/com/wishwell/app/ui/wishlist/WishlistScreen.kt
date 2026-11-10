package com.wishwell.app.ui.wishlist

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wishwell.app.data.model.Category
import com.wishwell.app.data.model.ItemWithStats
import com.wishwell.app.di.AppContainer
import com.wishwell.app.ui.components.DealBadge
import com.wishwell.app.ui.components.EmptyState
import com.wishwell.app.ui.components.LoadingSpinner
import com.wishwell.app.ui.components.MoveToCollectionSheet
import com.wishwell.app.ui.components.NoticeBar
import com.wishwell.app.ui.components.ConfirmDialog
import com.wishwell.app.ui.components.ProductThumb
import com.wishwell.app.ui.components.ReorderItemsSheet
import com.wishwell.app.ui.components.RollingPriceText
import com.wishwell.app.ui.components.SectionLabel
import com.wishwell.app.ui.components.StoreLogo
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
    val vm: WishlistViewModel = composeViewModel(
        factory = viewModelFactory {
            initializer { WishlistViewModel(container) }
        }
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val extra = LocalWishExtraColors.current
    var viewMode by remember { mutableStateOf("list") } // list, grid, carousel
    val sectionExpanded = remember { mutableStateMapOf<String, Boolean>() }
    fun toggleSection(key: String) {
        sectionExpanded[key] = !(sectionExpanded[key] ?: true)
    }
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(state.selectionMode) { onSelectionModeChanged(state.selectionMode) }
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) { vm.refreshAll() }
    }
    LaunchedEffect(state.refreshing) {
        if (state.refreshing) pullState.startRefresh() else pullState.endRefresh()
    }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var showReorderSheet by remember { mutableStateOf(false) }
    var revealedItemId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.selectionMode) {
        if (state.selectionMode) revealedItemId = null else {
            showReorderSheet = false
            showMoveSheet = false
        }
    }
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
            WishlistHeader(
                state = state,
                onOpenItem = { id ->
                    revealedItemId = null
                    onOpenItem(id)
                },
                onMarkNotificationsRead = vm::markAllNotificationsRead
            )
        }



        state.message?.let { notice ->
            item(key = "notice") {
                NoticeBar(
                    text = notice,
                    onDismiss = vm::clearMessage,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
        }

        if (state.hasAnyItems) {
            item(key = "search") {
                SearchAndSort(
                    state = state,
                    onQueryChange = vm::setQuery,
                    onSortChange = vm::setSort
                )
            }
            item(key = "filters") {
                FilterBar(
                    state = state,
                    onCategoryToggle = vm::toggleCategory,
                    onClearStores = vm::clearStores,
                    onStoreToggle = vm::toggleStore,
                    onAttention = vm::setAttentionFilter,
                    onToggleShowPurchased = vm::toggleShowPurchased
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
                        if (state.categoryIds.isNotEmpty()) add("${state.categoryIds.size} categories")
                        if (state.selectedStores.isNotEmpty()) add("${state.selectedStores.size} stores")
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
                        Icon(
                            imageVector = Icons.Rounded.ViewCarousel,
                            contentDescription = "Carousel view",
                            tint = if (viewMode == "carousel") MaterialTheme.colorScheme.primary else extra.muted,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewMode = "carousel" }
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

        val multiCategory = state.categoryIds.size >= 2
        val categoryGroups: List<Pair<Category?, List<ItemWithStats>>> = if (multiCategory) {
            val selectedCats = state.categories.filter { state.categoryIds.contains(it.category.id) }
            val groups = selectedCats.map { cw ->
                val catItems = state.items.filter { item ->
                    state.tagMap[item.item.id]?.contains(cw.category.id) == true
                }.sortedByDescending { it.item.createdAt }
                cw.category to catItems
            }
            if (state.categoryIds.contains(-1L)) {
                val unsorted = state.items.filter { item ->
                    (state.tagMap[item.item.id] ?: emptySet()).isEmpty()
                }.sortedByDescending { it.item.createdAt }
                groups + (null to unsorted)
            } else groups
        } else emptyList()

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
                    onSecondary = vm::loadDemoData
                )
            }

            state.items.isEmpty() -> item(key = "no-matches") {
                EmptyState(
                    title = "No matches",
                    body = "Nothing in your list fits that search, filter, or collection.",
                    actionLabel = "Clear filters",
                    onAction = vm::clearFilters
                )
            }

            viewMode == "grid" -> {
                if (multiCategory) {
                    categoryGroups.forEachIndexed { index, (category, catItems) ->
                        val catKey = "cat-grid-$index"
                        item(key = catKey) {
                            SectionLabel(
                                text = (category?.name ?: "Unsorted").uppercase(),
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${catItems.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extra.muted
                                        )
                                        IconButton(onClick = { toggleSection(catKey) }) {
                                            Icon(
                                                imageVector = if (sectionExpanded[catKey] ?: true) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                                contentDescription = if (sectionExpanded[catKey] ?: true) "Collapse ${category?.name ?: "Unsorted"}" else "Expand ${category?.name ?: "Unsorted"}",
                                                tint = extra.muted
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        gridRows(
                            items = catItems,
                            keyPrefix = catKey,
                            selectedIds = state.selectedIds,
                            selectionMode = state.selectionMode,
                            onOpenItem = onOpenItem,
                            onToggleSelection = vm::toggleSelection,
                            onEnterSelection = vm::enterSelection,
                            onItemPurchasedChanged = { id, purchased -> vm.togglePurchased(id, purchased) },
                            expanded = sectionExpanded[catKey] ?: true
                        )
                    }
                } else if (showAttentionSection) {
                    val attention = state.items.filter(::needsAttention)
                    val rest = state.items.filter { !needsAttention(it) }
                    item(key = "section-attention") {
                        SectionLabel(
                            text = "Needs attention",
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${attention.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extra.muted
                                    )
                                    IconButton(onClick = { toggleSection("attention") }) {
                                        Icon(
                                            imageVector = if (sectionExpanded["attention"] ?: true) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                            contentDescription = if (sectionExpanded["attention"] ?: true) "Collapse attention" else "Expand attention",
                                            tint = extra.muted
                                        )
                                    }
                                }
                            }
                        )
                    }
                    gridRows(
                        items = attention,
                        keyPrefix = "attn",
                        selectedIds = state.selectedIds,
                        selectionMode = state.selectionMode,
                        onOpenItem = onOpenItem,
                        onToggleSelection = vm::toggleSelection,
                        onEnterSelection = vm::enterSelection,
                        onItemPurchasedChanged = { id, purchased -> vm.togglePurchased(id, purchased) },
                        expanded = sectionExpanded["attention"] ?: true
                    )
                    item(key = "section-library") {
                        SectionLabel(
                            text = "Library",
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                            trailing = {
                                IconButton(onClick = { toggleSection("library") }) {
                                    Icon(
                                        imageVector = if (sectionExpanded["library"] ?: true) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                        contentDescription = if (sectionExpanded["library"] ?: true) "Collapse library" else "Expand library",
                                        tint = extra.muted
                                    )
                                }
                            }
                        )
                    }
                    gridRows(
                        items = rest,
                        keyPrefix = "lib",
                        selectedIds = state.selectedIds,
                        selectionMode = state.selectionMode,
                        onOpenItem = onOpenItem,
                        onToggleSelection = vm::toggleSelection,
                        onEnterSelection = vm::enterSelection,
                        onItemPurchasedChanged = { id, purchased -> vm.togglePurchased(id, purchased) },
                        expanded = sectionExpanded["library"] ?: true
                    )
                } else {
                    gridRows(
                        items = state.items,
                        keyPrefix = "grid",
                        selectedIds = state.selectedIds,
                        selectionMode = state.selectionMode,
                        onOpenItem = onOpenItem,
                        onToggleSelection = vm::toggleSelection,
                        onEnterSelection = vm::enterSelection,
                        onItemPurchasedChanged = { id, purchased -> vm.togglePurchased(id, purchased) }
                    )
                }
            }

            viewMode == "carousel" -> {
                if (multiCategory) {
                    categoryGroups.forEachIndexed { index, (category, catItems) ->
                        val catKey = "cat-carousel-$index"
                        item(key = catKey) {
                            SectionLabel(
                                text = (category?.name ?: "Unsorted").uppercase(),
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${catItems.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extra.muted
                                        )
                                        IconButton(onClick = { toggleSection(catKey) }) {
                                            Icon(
                                                imageVector = if (sectionExpanded[catKey] ?: true) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                                contentDescription = if (sectionExpanded[catKey] ?: true) "Collapse ${category?.name ?: "Unsorted"}" else "Expand ${category?.name ?: "Unsorted"}",
                                                tint = extra.muted
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        carouselRows(
                            items = catItems,
                            keyPrefix = catKey,
                            selectedIds = state.selectedIds,
                            selectionMode = state.selectionMode,
                            onOpenItem = onOpenItem,
                            onToggleSelection = vm::toggleSelection,
                            onEnterSelection = vm::enterSelection,
                            onItemPurchasedChanged = { id, purchased -> vm.togglePurchased(id, purchased) },
                            expanded = sectionExpanded[catKey] ?: true
                        )
                    }
                } else if (showAttentionSection) {
                    val attention = state.items.filter(::needsAttention)
                    val rest = state.items.filter { !needsAttention(it) }
                    item(key = "section-attention") {
                        SectionLabel(
                            text = "Needs attention",
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${attention.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extra.muted
                                    )
                                    IconButton(onClick = { toggleSection("attention") }) {
                                        Icon(
                                            imageVector = if (sectionExpanded["attention"] ?: true) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                            contentDescription = if (sectionExpanded["attention"] ?: true) "Collapse attention" else "Expand attention",
                                            tint = extra.muted
                                        )
                                    }
                                }
                            }
                        )
                    }
                    carouselRows(
                        items = attention,
                        keyPrefix = "attn",
                        selectedIds = state.selectedIds,
                        selectionMode = state.selectionMode,
                        onOpenItem = onOpenItem,
                        onToggleSelection = vm::toggleSelection,
                        onEnterSelection = vm::enterSelection,
                        onItemPurchasedChanged = { id, purchased -> vm.togglePurchased(id, purchased) },
                        expanded = sectionExpanded["attention"] ?: true
                    )
                    item(key = "section-library") {
                        SectionLabel(
                            text = "Library",
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                            trailing = {
                                IconButton(onClick = { toggleSection("library") }) {
                                    Icon(
                                        imageVector = if (sectionExpanded["library"] ?: true) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                        contentDescription = if (sectionExpanded["library"] ?: true) "Collapse library" else "Expand library",
                                        tint = extra.muted
                                    )
                                }
                            }
                        )
                    }
                    carouselRows(
                        items = rest,
                        keyPrefix = "lib",
                        selectedIds = state.selectedIds,
                        selectionMode = state.selectionMode,
                        onOpenItem = onOpenItem,
                        onToggleSelection = vm::toggleSelection,
                        onEnterSelection = vm::enterSelection,
                        onItemPurchasedChanged = { id, purchased -> vm.togglePurchased(id, purchased) },
                        expanded = sectionExpanded["library"] ?: true
                    )
                } else {
                    carouselRows(
                        items = state.items,
                        keyPrefix = "carousel",
                        selectedIds = state.selectedIds,
                        selectionMode = state.selectionMode,
                        onOpenItem = onOpenItem,
                        onToggleSelection = vm::toggleSelection,
                        onEnterSelection = vm::enterSelection,
                        onItemPurchasedChanged = { id, purchased -> vm.togglePurchased(id, purchased) }
                    )
                }
            }

            else -> {
                if (multiCategory) {
                    categoryGroups.forEachIndexed { index, (category, catItems) ->
                        val catKey = "cat-list-$index"
                        item(key = catKey) {
                            SectionLabel(
                                text = (category?.name ?: "Unsorted").uppercase(),
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${catItems.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extra.muted
                                        )
                                        IconButton(onClick = { toggleSection(catKey) }) {
                                            Icon(
                                                imageVector = if (sectionExpanded[catKey] ?: true) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                                contentDescription = if (sectionExpanded[catKey] ?: true) "Collapse ${category?.name ?: "Unsorted"}" else "Expand ${category?.name ?: "Unsorted"}",
                                                tint = extra.muted
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        items(if (sectionExpanded[catKey] ?: true) catItems else emptyList(), key = { "$catKey-${it.item.id}" }) { item ->
                            val id = item.item.id
                            val selected = id in state.selectedIds
                            SwipeableWishCard(
                                item = item,
                                onClick = {
                                    when {
                                        revealedItemId != null && revealedItemId != id -> revealedItemId = null
                                        state.selectionMode -> vm.toggleSelection(id)
                                        else -> onOpenItem(id)
                                    }
                                },
                                onEdit = { onEditItem(id) },
                                onRemove = { pendingDeleteId = id },
                                selected = selected,
                                selectionMode = state.selectionMode,
                                onLongClick = {
                                    revealedItemId = null
                                    if (!state.selectionMode) vm.enterSelection(id) else vm.toggleSelection(id)
                                },
                                swipeEnabled = !state.selectionMode,
                                isRevealed = revealedItemId == id,
                                onRevealedChange = { open -> revealedItemId = if (open) id else if (revealedItemId == id) null else revealedItemId },
                                onTogglePurchased = { vm.togglePurchased(id, !item.item.isPurchased) },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                            )
                        }
                    }
                } else if (showAttentionSection) {
                    val attention = state.items.filter(::needsAttention)
                    val rest = state.items.filter { !needsAttention(it) }
                    item(key = "section-attention") {
                        SectionLabel(
                            text = "Needs attention",
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${attention.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extra.muted
                                    )
                                    IconButton(onClick = { toggleSection("attention") }) {
                                        Icon(
                                            imageVector = if (sectionExpanded["attention"] ?: true) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                            contentDescription = if (sectionExpanded["attention"] ?: true) "Collapse attention" else "Expand attention",
                                            tint = extra.muted
                                        )
                                    }
                                }
                            }
                        )
                    }
                    items(if (sectionExpanded["attention"] ?: true) attention else emptyList(), key = { "attn-${it.item.id}" }) { item ->
                        val id = item.item.id
                        val selected = id in state.selectedIds
                        SwipeableWishCard(
                            item = item,
                            onClick = {
                                when {
                                    revealedItemId != null && revealedItemId != id -> revealedItemId = null
                                    state.selectionMode -> vm.toggleSelection(id)
                                    else -> onOpenItem(id)
                                }
                            },
                            onEdit = { onEditItem(id) },
                            onRemove = { pendingDeleteId = id },
                            selected = selected,
                            selectionMode = state.selectionMode,
                            onLongClick = {
                                revealedItemId = null
                                if (!state.selectionMode) vm.enterSelection(id) else vm.toggleSelection(id)
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
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                            trailing = {
                                IconButton(onClick = { toggleSection("library") }) {
                                    Icon(
                                        imageVector = if (sectionExpanded["library"] ?: true) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                        contentDescription = if (sectionExpanded["library"] ?: true) "Collapse library" else "Expand library",
                                        tint = extra.muted
                                    )
                                }
                            }
                        )
                    }
                    items(if (sectionExpanded["library"] ?: true) rest else emptyList(), key = { "lib-${it.item.id}" }) { item ->
                        val id = item.item.id
                        val selected = id in state.selectedIds
                        SwipeableWishCard(
                            item = item,
                            onClick = {
                                when {
                                    revealedItemId != null && revealedItemId != id -> revealedItemId = null
                                    state.selectionMode -> vm.toggleSelection(id)
                                    else -> onOpenItem(id)
                                }
                            },
                            onEdit = { onEditItem(id) },
                            onRemove = { pendingDeleteId = id },
                            selected = selected,
                            selectionMode = state.selectionMode,
                            onLongClick = {
                                revealedItemId = null
                                if (!state.selectionMode) vm.enterSelection(id) else vm.toggleSelection(id)
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
                                    state.selectionMode -> vm.toggleSelection(id)
                                    else -> onOpenItem(id)
                                }
                            },
                            onEdit = { onEditItem(id) },
                            onRemove = { pendingDeleteId = id },
                            selected = selected,
                            selectionMode = state.selectionMode,
                            onLongClick = {
                                revealedItemId = null
                                if (!state.selectionMode) vm.enterSelection(id) else vm.toggleSelection(id)
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
                onCancel = vm::clearSelection,
                onSelectAll = { vm.selectAll(state.items.map { it.item.id }) },
                onReorder = { showReorderSheet = true },
                onMove = { showMoveSheet = true },
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
                    vm.deleteItem(id)
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
                    vm.deleteSelected()
                    showBulkDeleteConfirm = false
                },
                onDismiss = { showBulkDeleteConfirm = false }
            )
        }
        if (showMoveSheet && state.selectionMode) {
            MoveToCollectionSheet(
                categories = state.categories,
                selectedItemIds = state.selectedIds,
                tagMap = state.tagMap,
                onSave = { categoryIds ->
                    vm.moveSelectedToCategory(categoryIds)
                    showMoveSheet = false
                },
                onDismiss = { showMoveSheet = false }
            )
        }
        if (showReorderSheet && state.selectionMode) {
            ReorderItemsSheet(
                items = state.items,
                selectedCount = state.selectedIds.size,
                onSelectAll = { vm.selectAll(state.items.map { it.item.id }) },
                onSave = { orderedIds ->
                    vm.persistVisibleOrder(orderedIds)
                    showReorderSheet = false
                    vm.clearSelection()
                },
                onDismiss = { showReorderSheet = false }
            )
        }
    }
}

@Composable
private fun SelectionBar(
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
        shape = RoundedCornerShape(20.dp),
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

private data class WishlistNotification(
    val id: Long,
    val key: String,
    val title: String,
    val detail: String,
    val tone: com.wishwell.app.ui.components.BadgeTone
)

@Composable
private fun WishlistHeader(
    state: WishlistUiState,
    onOpenItem: (Long) -> Unit,
    onMarkNotificationsRead: (Set<String>) -> Unit
) {
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    val summary = state.summary
    var showNotifications by remember { mutableStateOf(false) }
    val notifications = remember(state.allItems, state.readNotificationKeys) {
        buildWishlistNotifications(state.allItems, state.readNotificationKeys)
    }

    if (showNotifications) {
        WishlistNotificationsSheet(
            notifications = notifications,
            onOpenItem = { id ->
                showNotifications = false
                onOpenItem(id)
            },
            onMarkAllRead = { onMarkNotificationsRead(notifications.mapTo(linkedSetOf()) { it.key }) },
            onDismiss = { showNotifications = false }
        )
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "WISHWELL",
                style = MaterialTheme.typography.titleLarge.copy(
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = colors.onBackground,
                modifier = Modifier.weight(1f)
            )
            NotificationButton(
                count = notifications.size,
                onClick = { showNotifications = true }
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Your library.",
            style = MaterialTheme.typography.displayMedium,
            color = colors.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Watched for price drops, all-time lows, and target hits.",
            style = MaterialTheme.typography.bodyMedium,
            color = extra.muted
        )

        if (summary.itemCount > 0) {
            Spacer(Modifier.height(16.dp))
            DashboardValueCard(summary = summary)
            Spacer(Modifier.height(10.dp))
            DashboardMetrics(summary = summary)
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
private fun NotificationButton(count: Int, onClick: () -> Unit) {
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (count > 0) {
                    Badge(
                        modifier = Modifier.offset(x = (-4).dp, y = 4.dp),
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ) {
                        Text(if (count > 99) "99+" else count.toString())
                    }
                }
            }
        ) {
            Icon(
                imageVector = if (count > 0) Icons.Rounded.Notifications else Icons.Rounded.NotificationsNone,
                contentDescription = if (count > 0) "Notifications, $count" else "Notifications",
                tint = if (count > 0) colors.onSurface else extra.muted
            )
        }
    }
}

private fun buildWishlistNotifications(
    items: List<ItemWithStats>,
    readKeys: Set<String> = emptySet()
): List<WishlistNotification> {
    val out = mutableListOf<WishlistNotification>()
    items.forEach { item ->
        val price = item.price
        val currency = item.item.currencyCode
        val target = item.item.targetPrice
        val n = when {
            target != null && price != null && price <= target + 0.005 -> WishlistNotification(
                id = item.item.id,
                key = "${item.item.id}:target",
                title = item.item.title,
                detail = "Target reached · ${formatMoney(price, currency)}",
                tone = com.wishwell.app.ui.components.BadgeTone.GOOD
            )
            item.isAtLowest -> WishlistNotification(
                id = item.item.id,
                key = "${item.item.id}:low",
                title = item.item.title,
                detail = "All-time low · ${formatMoney(price ?: 0.0, currency)}",
                tone = com.wishwell.app.ui.components.BadgeTone.LOW
            )
            item.isOnSale || (item.discountPercent ?: 0) >= 1 -> WishlistNotification(
                id = item.item.id,
                key = "${item.item.id}:sale",
                title = item.item.title,
                detail = buildString {
                    item.discountPercent?.let { append("-$it%") }
                    if (price != null) {
                        if (isNotEmpty()) append(" · ")
                        append(formatMoney(price, currency))
                    }
                    if (isEmpty()) append("On sale")
                },
                tone = com.wishwell.app.ui.components.BadgeTone.GOOD
            )
            item.item.lastCheckFailed -> WishlistNotification(
                id = item.item.id,
                key = "${item.item.id}:failed",
                title = item.item.title,
                detail = "Price check failed",
                tone = com.wishwell.app.ui.components.BadgeTone.HIGH
            )
            else -> null
        }
        if (n != null && n.key !in readKeys) out += n
    }
    return out.sortedWith(
        compareBy<WishlistNotification> {
            when (it.tone) {
                com.wishwell.app.ui.components.BadgeTone.HIGH -> 0
                com.wishwell.app.ui.components.BadgeTone.GOOD -> 1
                com.wishwell.app.ui.components.BadgeTone.LOW -> 2
                com.wishwell.app.ui.components.BadgeTone.NEUTRAL -> 3
            }
        }.thenBy { it.title.lowercase() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishlistNotificationsSheet(
    notifications: List<WishlistNotification>,
    onOpenItem: (Long) -> Unit,
    onMarkAllRead: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(extra.hairline)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (notifications.isEmpty()) "No notifications" else "Notifications",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (notifications.isNotEmpty()) {
                    TextButton(onClick = onMarkAllRead) {
                        Text("Mark all as read", color = colors.primary)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            if (notifications.isEmpty()) {
                Text(
                    "Price drops, target hits, all-time lows, and check issues will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = extra.muted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    notifications.forEach { n ->
                        key("notif-${n.id}-${n.detail}") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onOpenItem(n.id) },
                                shape = RoundedCornerShape(14.dp),
                                color = extra.sunken.copy(alpha = 0.45f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            n.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        DealBadge(n.detail, n.tone)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DashboardValueCard(summary: WishlistSummary) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    val hasValue = summary.trackedValue > 0.0
    val savings = summary.belowPeak
    val shape = RoundedCornerShape(24.dp)
    val sandLight = Color(0xFFF6F0E3)
    val sandMid = extra.sunken
    val sandDeep = Color(0xFFE8DECC)
    val ink = if (extra.isDark) colors.onSurface else Color(0xFF2A241B)
    val subInk = if (extra.isDark) extra.muted else Color(0xFF6B6254)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 500f),
        label = "dashboardCardScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null) { }
            .drawBehind {
                val accent = colors.primary.copy(alpha = if (extra.isDark) 0.18f else 0.10f)
                val baseTop = if (extra.isDark) Color(0xFF262820) else sandLight
                val baseBottom = if (extra.isDark) Color(0xFF1C1F18) else sandDeep
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(baseTop, sandMid.copy(alpha = if (extra.isDark) 0.55f else 0.92f), baseBottom),
                        start = Offset(0f, 0f),
                        end = Offset(size.width * 0.9f, size.height)
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(accent, Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.15f),
                        radius = size.width * 0.75f
                    )
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (extra.isDark) 0.10f else 0.55f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (extra.isDark) 0.12f else 0.04f)
                        )
                    )
                )
                val grain = if (extra.isDark) Color.White else Color(0xFF6E6252)
                val step = 6.dp.toPx()
                var y = 0f
                var row = 0
                while (y < size.height) {
                    var x = if (row % 2 == 0) 0f else step * 0.5f
                    var col = 0
                    while (x < size.width) {
                        val n = ((row * 73856093) xor (col * 19349663) xor 83492791) and 31
                        if (n < 7) {
                            val a = if (extra.isDark) 0.018f + (n * 0.003f) else 0.028f + (n * 0.004f)
                            drawCircle(
                                color = grain.copy(alpha = a),
                                radius = 0.9.dp.toPx() + (n % 3) * 0.15.dp.toPx(),
                                center = Offset(x + (n % 5) * 0.35f, y + (n % 4) * 0.35f)
                            )
                        }
                        x += step
                        col++
                    }
                    y += step
                    row++
                }
            }
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (extra.isDark) 0.22f else 0.75f),
                            extra.hairline.copy(alpha = if (extra.isDark) 0.35f else 0.45f),
                            colors.primary.copy(alpha = if (extra.isDark) 0.18f else 0.12f)
                        )
                    )
                ),
                shape
            )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "TRACKED VALUE",
                style = MaterialTheme.typography.labelSmall,
                color = subInk
            )
            Spacer(Modifier.height(4.dp))
            RollingPriceText(
                text = if (hasValue) formatMoney(summary.trackedValue, summary.currencyCode)
                else "${summary.itemCount}",
                style = MaterialTheme.typography.displayMedium,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (savings >= 1.0) {
                    Surface(
                        shape = CircleShape,
                        color = colors.primary.copy(alpha = if (extra.isDark) 0.22f else 0.14f),
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = if (extra.isDark) 0.30f else 0.18f))
                    ) {
                        RollingPriceText(
                            text = "${formatMoney(savings, summary.currencyCode)} saved",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (extra.isDark) colors.primary else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = summary.itemCount.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = ink
                    )
                    Text(
                        text = if (summary.itemCount == 1) "item" else "items",
                        style = MaterialTheme.typography.labelSmall,
                        color = subInk
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardMetrics(summary: WishlistSummary) {
    val extra = LocalWishExtraColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = extra.sunken.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DashboardMetric(
                label = "At low",
                value = summary.atLowCount,
                modifier = Modifier.weight(1f)
            )
            MetricDivider()
            DashboardMetric(
                label = "On sale",
                value = summary.onSaleCount,
                modifier = Modifier.weight(1f)
            )
            MetricDivider()
            DashboardMetric(
                label = "Targets",
                value = summary.belowTargetCount,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DashboardMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    val extra = LocalWishExtraColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = extra.muted
        )
    }
}

@Composable
private fun MetricDivider() {
    val extra = LocalWishExtraColors.current
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(extra.hairline)
    )
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
    onEnterSelection: (Long) -> Unit,
    onItemPurchasedChanged: ((Long, Boolean) -> Unit)? = null,
    expanded: Boolean = true
) {
    if (!expanded) return
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
                    onTogglePurchased = { onItemPurchasedChanged?.invoke(id, !item.item.isPurchased) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

private fun LazyListScope.carouselRows(
    items: List<ItemWithStats>,
    keyPrefix: String,
    selectedIds: Set<Long>,
    selectionMode: Boolean,
    onOpenItem: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onEnterSelection: (Long) -> Unit,
    onItemPurchasedChanged: ((Long, Boolean) -> Unit)? = null,
    expanded: Boolean = true
) {
    if (!expanded) return
    if (items.isEmpty()) return
    item(key = "$keyPrefix-carousel") {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { "$keyPrefix-${it.item.id}" }) { item ->
                val id = item.item.id
                val selected = id in selectedIds
                WishlistGridCard(
                    item = item,
                    onClick = { if (selectionMode) onToggleSelection(id) else onOpenItem(id) },
                    selected = selected,
                    selectionMode = selectionMode,
                    onLongClick = { if (!selectionMode) onEnterSelection(id) else onToggleSelection(id) },
                    onTogglePurchased = { onItemPurchasedChanged?.invoke(id, !item.item.isPurchased) },
                    modifier = Modifier.width(172.dp)
                )
            }
        }
    }
}

@Composable
private fun FilterBar(
    state: WishlistUiState,
    onCategoryToggle: (Long) -> Unit,
    onClearStores: () -> Unit,
    onStoreToggle: (String) -> Unit,
    onAttention: (AttentionFilter) -> Unit,
    onToggleShowPurchased: () -> Unit
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    var storeExpanded by remember { mutableStateOf(false) }
    val hasUnsorted = state.allItems.any { item -> (state.tagMap[item.item.id] ?: emptySet()).isEmpty() }
    val selectedCategoryIds = state.categoryIds
    val selectedCategories = state.categories.filter { selectedCategoryIds.contains(it.category.id) }
    val categoryLabel = when {
        selectedCategoryIds.isEmpty() -> "All items"
        selectedCategoryIds.size == 1 && selectedCategories.isNotEmpty() -> selectedCategories.first().category.name
        else -> "${selectedCategoryIds.size} categories"
    }
    val categoryGlyph = if (selectedCategoryIds.isEmpty()) Icons.Rounded.Category else categoryIcon(selectedCategories.firstOrNull()?.category?.emoji, selectedCategories.firstOrNull()?.category?.name)
    val selectedStores = state.selectedStores
    val storeUrls = remember(state.allItems) {
        buildMap<String, String> {
            state.allItems.forEach { item ->
                val name = item.item.storeName.trim()
                if (name.isNotBlank() && !containsKey(name)) put(name, item.item.productUrl)
            }
        }
    }
    val singleStore = if (selectedStores.size == 1) selectedStores.first() else null
    val storeLabel = when {
        selectedStores.isEmpty() -> "All stores"
        selectedStores.size == 1 -> selectedStores.first()
        else -> "${selectedStores.size} stores"
    }
    val onSale = state.attentionFilter == AttentionFilter.ON_SALE

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            WishPill(
                text = categoryLabel,
                selected = selectedCategoryIds.isNotEmpty(),
                onClick = { categoryExpanded = !categoryExpanded },
                leadingIcon = categoryGlyph,
                trailingIcon = Icons.Rounded.ExpandMore
            )
            DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("All items") },
                    onClick = {
                        onCategoryToggle(-999L) // sentinel: clear all
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (selectedCategoryIds.isEmpty()) Icons.Rounded.Check else null
                    }
                )
                state.categories.forEach { category ->
                    val isSelected = selectedCategoryIds.contains(category.category.id)
                    DropdownMenuItem(
                        text = { Text(category.category.name) },
                        onClick = {
                            onCategoryToggle(category.category.id)
                        },
                        leadingIcon = {
                            Icon(
                                categoryIcon(category.category.emoji, category.category.name),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (isSelected) Icons.Rounded.Check else null
                        }
                    )
                }
                if (hasUnsorted) {
                    val isUnsortedSelected = selectedCategoryIds.contains(-1L)
                    DropdownMenuItem(
                        text = { Text("Unsorted") },
                        onClick = {
                            onCategoryToggle(-1L)
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (isUnsortedSelected) Icons.Rounded.Check else null
                        }
                    )
                }
            }
        }

        Box {
            WishPill(
                text = storeLabel,
                selected = selectedStores.isNotEmpty(),
                onClick = { storeExpanded = !storeExpanded },
                leadingIcon = if (singleStore != null) null else Icons.Rounded.Storefront,
                leadingContent = singleStore?.let {
                    {
                        StoreLogo(
                            productUrl = storeUrls[it],
                            storeName = it,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                trailingIcon = Icons.Rounded.ExpandMore
            )
            DropdownMenu(expanded = storeExpanded, onDismissRequest = { storeExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("All stores") },
                    onClick = { onClearStores() },
                    leadingIcon = {
                        Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (selectedStores.isEmpty()) Icons.Rounded.Check else null
                    }
                )
                state.storeNames.forEach { store ->
                    val isSelected = selectedStores.contains(store)
                    DropdownMenuItem(
                        text = { Text(store) },
                        onClick = { onStoreToggle(store) },
                        leadingIcon = {
                            StoreLogo(
                                productUrl = storeUrls[store],
                                storeName = store,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (isSelected) Icons.Rounded.Check else null
                        }
                    )
                }
            }
        }

        WishPill(
            text = if (state.summary.onSaleCount > 0) "On sale · ${state.summary.onSaleCount}" else "On sale",
            selected = onSale,
            onClick = { onAttention(if (onSale) AttentionFilter.ALL else AttentionFilter.ON_SALE) },
            leadingIcon = Icons.Rounded.LocalOffer
        )
        WishPill(
            text = if (state.summary.itemCount > 0) "Show bought" else "Show bought",
            selected = state.showPurchased,
            onClick = onToggleShowPurchased,
            leadingIcon = Icons.Rounded.ShoppingBag
        )
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
    onLongClick: (() -> Unit)? = null,
    onTogglePurchased: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    val price = item.price
    val listPrice = item.item.listPrice
    val borderColor = if (selected) colors.primary else extra.hairline
    val isPurchased = item.item.isPurchased

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
                    shape = RoundedCornerShape(14.dp),
                    storeUrl = item.item.productUrl
                )
                val badge = when {
                    item.isAtLowest -> "Low" to com.wishwell.app.ui.components.BadgeTone.LOW
                    item.discountPercent != null && item.discountPercent!! >= 1 ->
                        "-${item.discountPercent}%" to com.wishwell.app.ui.components.BadgeTone.GOOD
                    item.item.lastCheckFailed -> "!" to com.wishwell.app.ui.components.BadgeTone.HIGH
                    else -> null
                }
                if (isPurchased) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Done,
                                contentDescription = "Bought",
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp)
                                    .background(colors.surface, CircleShape)
                            )
                            Text(
                                text = "Bought",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (!isPurchased && badge != null) {
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
                if (!selectionMode && onTogglePurchased != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        IconButton(
                            onClick = onTogglePurchased,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isPurchased) Icons.Rounded.Done else Icons.Rounded.ShoppingBag,
                                contentDescription = if (isPurchased) "Mark not bought" else "Mark bought",
                                tint = if (isPurchased) colors.primary else extra.muted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreLogo(
                    productUrl = item.item.productUrl,
                    storeName = item.item.storeName,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = item.item.storeName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = item.item.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            RollingPriceText(
                text = formatMoney(price, item.item.currencyCode),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (listPrice != null && price != null && listPrice > price + 0.01) {
                RollingPriceText(
                    text = formatMoney(listPrice, item.item.currencyCode),
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

