package com.wishwell.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wishwell.app.data.model.CategoryWithCount
import com.wishwell.app.ui.theme.LocalWishExtraColors

/**
 * Slide-up sheet that moves one or more selected items to collections.
 *
 * Each row shows the current state across the selected items in the caption
 * ("All 3 items", "2 of 3 items", "None"), while the checkbox marks what will be
 * applied to every selected item when "Apply" is pressed. "Unsorted" clears all
 * tags. Tapping a row toggles that collection for all selected items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToCollectionSheet(
    categories: List<CategoryWithCount>,
    selectedItemIds: Set<Long>,
    tagMap: Map<Long, Set<Long>>,
    onSave: (Set<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val selectedIdsList = selectedItemIds.toList()
    val allItemTags = selectedIdsList.map { id -> tagMap[id] ?: emptySet() }
    val intersectionTags =
        if (allItemTags.isEmpty()) emptySet<Long>() else allItemTags.reduce { a, b -> a.intersect(b) }
    val unsortedCount = allItemTags.count { it.isEmpty() }
    val itemCount = selectedItemIds.size

    // Pending tag set to apply to every selected item. Starts from the tags all
    // items already share so unchecking removes a shared tag and checking an
    // unshared tag adds it to everything.
    var selectedTags by remember(selectedItemIds, tagMap) {
        mutableStateOf(intersectionTags.toSet())
    }

    fun tagCount(categoryId: Long): Int = allItemTags.count { it.contains(categoryId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        dragHandle = {
            androidx.compose.foundation.layout.Box(
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
                    Text(
                        text = "Move to collection",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface
                    )
                    Text(
                        text = "$itemCount item${if (itemCount != 1) "s" else ""} selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Unsorted option: clears every tag from the selected items.
            CollectionTagRow(
                name = "Unsorted",
                icon = Icons.Rounded.Category,
                checked = selectedTags.isEmpty(),
                caption = buildCaption(unsortedCount, itemCount),
                onClick = { selectedTags = emptySet() }
            )

            Spacer(Modifier.height(8.dp))

            categories.forEach { category ->
                val catId = category.category.id
                val count = tagCount(catId)
                CollectionTagRow(
                    name = category.category.name,
                    icon = categoryIcon(category.category.emoji, category.category.name),
                    checked = catId in selectedTags,
                    caption = buildCaption(count, itemCount),
                    onClick = {
                        if (catId in selectedTags) selectedTags -= catId else selectedTags += catId
                    }
                )
            }

            Spacer(Modifier.height(14.dp))
            TextButton(
                onClick = { onSave(selectedTags) },
                enabled = selectedItemIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    containerColor = colors.primary.copy(alpha = 0.12f),
                    contentColor = colors.primary,
                    disabledContainerColor = extra.sunken.copy(alpha = 0.35f),
                    disabledContentColor = extra.muted
                )
            ) {
                Text(
                    text = "Apply to $itemCount item${if (itemCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CollectionTagRow(
    name: String,
    icon: ImageVector,
    checked: Boolean,
    caption: String?,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp)
            .sizeIn(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) colors.primary else Color.Transparent)
                .border(
                    if (checked) BorderStroke(0.dp, Color.Transparent)
                    else BorderStroke(1.dp, extra.hairline.copy(alpha = 0.9f)),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = extra.muted
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (checked) colors.primary else colors.onSurface,
                    fontWeight = if (checked) androidx.compose.ui.text.font.FontWeight.Medium
                    else androidx.compose.ui.text.font.FontWeight.Normal
                )
            }
            if (caption != null && caption.isNotBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted
                )
            }
        }
    }
}

private fun buildCaption(count: Int, total: Int): String? {
    if (total == 0) return null
    return when {
        count == total -> "All $total items"
        count > 0 -> "$count of $total items"
        else -> "None"
    }
}
