package com.wishwell.app.ui.tracking

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wishwell.app.di.AppContainer
import com.wishwell.app.ui.components.EmptyState
import com.wishwell.app.ui.components.LoadingSpinner
import com.wishwell.app.ui.components.NoticeBar
import com.wishwell.app.ui.components.SectionLabel
import com.wishwell.app.ui.components.StatTile
import com.wishwell.app.ui.components.WishCard
import com.wishwell.app.ui.theme.LocalWishExtraColors
import com.wishwell.app.util.formatMoney

@Composable
fun PriceTrackingScreen(
    container: AppContainer,
    onOpenItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val viewModel: PriceTrackingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PriceTrackingViewModel(container) }
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 120.dp)
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "PRICE TRACKING",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                        color = colors.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.refreshing) {
                        LoadingSpinner(size = 18.dp, modifier = Modifier.padding(9.dp))
                    } else {
                        IconButton(onClick = viewModel::refreshAll) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh prices",
                                tint = extra.muted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = extra.muted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Price watch.",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Drops, all-time lows, and targets reached — all in one place.",
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
                            text = "Totals use ${summary.currencyCode}. Other currencies aren't summed.",
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
                    body = "We'll show drops, all-time lows, and target hits here as they happen. Tap refresh to check prices now.",
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
                        WishCard(
                            item = item,
                            onClick = { onOpenItem(item.item.id) },
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
                        WishCard(
                            item = item,
                            onClick = { onOpenItem(item.item.id) },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                        )
                    }
                }

                if (state.biggestDrops.isNotEmpty()) {
                    item(key = "drops-label") {
                        SectionLabel(
                            text = "Biggest drops",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(state.biggestDrops, key = { "drop-${it.item.id}" }) { item ->
                        WishCard(
                            item = item,
                            onClick = { onOpenItem(item.item.id) },
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
                        WishCard(
                            item = item,
                            onClick = { onOpenItem(item.item.id) },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}
