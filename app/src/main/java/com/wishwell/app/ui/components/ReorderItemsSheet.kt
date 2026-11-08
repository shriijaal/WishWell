package com.wishwell.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wishwell.app.data.model.ItemWithStats
import com.wishwell.app.ui.theme.LocalWishExtraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderItemsSheet(
    items: List<ItemWithStats>,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onSave: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ordered = remember(items) { mutableStateListOf<ItemWithStats>().apply { addAll(items) } }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 58.dp.toPx() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

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
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reorder items", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Text(
                        "$selectedCount selected · Long-press handle to drag",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted
                    )
                }
                TextButton(
                    onClick = { onSave(ordered.map { it.item.id }) },
                    enabled = ordered.isNotEmpty()
                ) {
                    Text("Save", color = colors.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSelectAll) {
                    Text("Select all", color = colors.primary)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (ordered.isEmpty()) {
                Text("No items to reorder.", style = MaterialTheme.typography.bodyMedium, color = extra.muted)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ordered.forEachIndexed { index, item ->
                        key(item.item.id) {
                            val isDragging = draggingIndex == index
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isDragging) dragOffset else 0f
                                        shadowElevation = if (isDragging) 10f else 0f
                                    }
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isDragging) colors.primary.copy(alpha = 0.10f) else extra.sunken.copy(alpha = 0.45f))
                                    .border(
                                        BorderStroke(
                                            if (isDragging) 1.5.dp else 1.dp,
                                            if (isDragging) colors.primary.copy(alpha = 0.35f) else extra.hairline.copy(alpha = 0.7f)
                                        ),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = if (isDragging) colors.primary else extra.muted,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .padding(5.dp)
                                        .pointerInput(ordered.size, item.item.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggingIndex = ordered.indexOfFirst { it.item.id == item.item.id }.takeIf { it >= 0 }
                                                    dragOffset = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                                    dragOffset += dragAmount.y
                                                    val moved = (dragOffset / rowHeightPx).toInt()
                                                    if (moved != 0) {
                                                        val to = (from + moved).coerceIn(0, ordered.lastIndex)
                                                        if (to != from) {
                                                            ordered.add(to, ordered.removeAt(from))
                                                            draggingIndex = to
                                                            dragOffset -= (to - from) * rowHeightPx
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggingIndex = null
                                                    dragOffset = 0f
                                                },
                                                onDragCancel = {
                                                    draggingIndex = null
                                                    dragOffset = 0f
                                                }
                                            )
                                        }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.item.storeName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extra.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}
