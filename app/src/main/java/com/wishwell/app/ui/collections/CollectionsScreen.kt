package com.wishwell.app.ui.collections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wishwell.app.data.model.Category
import com.wishwell.app.data.model.CategoryWithCount
import com.wishwell.app.di.AppContainer
import com.wishwell.app.ui.components.ConfirmDialog
import com.wishwell.app.ui.components.EmptyState
import com.wishwell.app.ui.components.IconCircleButton
import com.wishwell.app.ui.components.NoticeBar
import com.wishwell.app.ui.components.SectionLabel
import com.wishwell.app.ui.components.WishDialog
import com.wishwell.app.ui.components.CategoryIcons
import com.wishwell.app.ui.components.categoryIcon
import com.wishwell.app.ui.theme.LocalWishExtraColors
import com.wishwell.app.util.Palette

@Composable
fun CollectionsScreen(container: AppContainer) {
    val viewModel: CollectionsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CollectionsViewModel(container) }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme

    var showEditor by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var colorInput by remember { mutableStateOf(Palette.FOREST) }
    var iconKeyInput by remember { mutableStateOf<String?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }

    fun openEditor(category: Category?) {
        editingCategory = category
        nameInput = category?.name.orEmpty()
        colorInput = category?.colorArgb ?: Palette.all.random()
        iconKeyInput = category?.emoji
        showEditor = true
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 120.dp)
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "COLLECTIONS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                            color = colors.onBackground
                        )
                    }
                    IconCircleButton(
                        onClick = { openEditor(null) },
                        icon = Icons.Rounded.Add,
                        contentDescription = "New collection"
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Sort the wants.",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Collections keep long wishlists sane. Every item belongs to one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = extra.muted
                )
                Spacer(Modifier.height(20.dp))
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

        if (state.categories.isEmpty() && !state.loading) {
            item(key = "empty") {
                EmptyState(
                    title = "No collections yet",
                    body = "Create one to group your saves by room, person or project.",
                    actionLabel = "Create a collection",
                    onAction = { openEditor(null) }
                )
            }
        } else {
            item(key = "label") {
                SectionLabel(
                    text = "${state.categories.size} collections",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
            items(state.categories, key = { it.category.id }) { category ->
                CollectionCard(
                    category = category,
                    onEdit = { openEditor(category.category) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                )
            }
        }
    }

    if (showEditor) {
        WishDialog(
            title = if (editingCategory == null) "New collection" else "Edit collection",
            confirmLabel = "Save",
            confirmEnabled = nameInput.isNotBlank(),
            onDismiss = { showEditor = false },
            onConfirm = {
                val category = editingCategory
                if (category == null) {
                    viewModel.add(nameInput, colorInput, iconKeyInput)
                } else {
                    viewModel.update(category, nameInput, colorInput, iconKeyInput)
                }
                showEditor = false
            }
        ) {
            Column {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(30) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = editorFieldColors()
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "COLOUR",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Palette.all.take(8).forEach { color ->
                        val selected = colorInput == color
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .then(
                                    if (selected) Modifier.border(2.dp, extra.limeInk, CircleShape)
                                    else Modifier
                                )
                                .clickable { colorInput = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(extra.limeInk.copy(alpha = 0.75f))
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "ICON",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryIcons.all.chunked(6).forEach { rowIcons ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowIcons.forEach { option ->
                                val selected = iconKeyInput == option.key
                                val tint = Color(colorInput)
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) tint.copy(alpha = 0.18f) else extra.sunken.copy(alpha = 0.45f)
                                        )
                                        .then(
                                            if (selected) Modifier.border(1.5.dp, tint.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                            else Modifier.border(1.dp, extra.hairline.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                        )
                                        .clickable { iconKeyInput = if (selected) null else option.key },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = option.label,
                                        tint = if (selected) tint else extra.muted,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            repeat((6 - rowIcons.size).coerceAtLeast(0)) {
                                Spacer(Modifier.size(40.dp))
                            }
                        }
                    }
                }
                if (editingCategory != null) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            deletingCategory = editingCategory
                            showEditor = false
                        }
                    ) {
                        Text(
                            text = "Delete this collection",
                            color = colors.error,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }

    deletingCategory?.let { category ->
        ConfirmDialog(
            title = "Delete \"${category.name}\"?",
            body = "Items in this collection stay in your wishlist; they'll move to Unsorted.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.delete(category)
                deletingCategory = null
            },
            onDismiss = { deletingCategory = null }
        )
    }
}

@Composable
private fun CollectionCard(
    category: CategoryWithCount,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    val color = Color(category.category.colorArgb)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, extra.hairline)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(category.category.emoji, category.category.name),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(category.itemCount)
                        append(if (category.itemCount == 1) " item" else " items")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "Edit collection",
                    tint = extra.muted,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = LocalWishExtraColors.current.hairline,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)
