package com.wishwell.app.ui.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wishwell.app.di.AppContainer
import com.wishwell.app.ui.components.LoadingSpinner
import com.wishwell.app.ui.components.NoticeBar
import com.wishwell.app.ui.components.ProductThumb
import com.wishwell.app.ui.components.SectionLabel
import com.wishwell.app.ui.components.WishPill
import com.wishwell.app.ui.components.categoryIcon
import com.wishwell.app.ui.theme.LocalWishExtraColors

@Composable
fun EditItemScreen(
    container: AppContainer,
    itemId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val viewModel: EditItemViewModel = viewModel(
        key = "edit-$itemId",
        factory = viewModelFactory { initializer { EditItemViewModel(container, itemId) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme

    when {
        state.loading -> {
            Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                LoadingSpinner(size = 24.dp)
            }
            return
        }
        state.notFound -> {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.onBackground)
                }
                Spacer(Modifier.height(16.dp))
                Text("Item not found.", style = MaterialTheme.typography.titleMedium, color = colors.onBackground)
            }
            return
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.onBackground)
            }
            Text(
                text = "EDIT ITEM",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = extra.muted,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(48.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .imePadding()
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Edit saved item", style = MaterialTheme.typography.headlineLarge, color = colors.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(
                "Update details, prices, and images. Changing the current price adds a history point.",
                style = MaterialTheme.typography.bodyMedium,
                color = extra.muted
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("Images")
            Spacer(Modifier.height(6.dp))
            Text(
                "First image is the primary/cover image. Add, remove, or reorder.",
                style = MaterialTheme.typography.bodySmall,
                color = extra.muted
            )
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.imageInput,
                    onValueChange = viewModel::setImageInput,
                    modifier = Modifier.weight(1f),
                    label = { Text("Image URL") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    colors = fieldColors()
                )
                OutlinedButton(
                    onClick = viewModel::addImage,
                    enabled = state.imageInput.isNotBlank(),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, extra.hairline)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add image", modifier = Modifier.size(18.dp))
                }
            }

            if (state.imageUrls.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                state.imageUrls.forEachIndexed { index, url ->
                    ImageRow(
                        url = url,
                        index = index,
                        total = state.imageUrls.size,
                        isPrimary = index == 0,
                        onMakePrimary = { viewModel.makePrimary(index) },
                        onMoveUp = { viewModel.moveImage(index, index - 1) },
                        onMoveDown = { viewModel.moveImage(index, index + 1) },
                        onRemove = { viewModel.removeImage(index) }
                    )
                    if (index != state.imageUrls.lastIndex) Spacer(Modifier.height(8.dp))
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = extra.sunken.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No images yet. Add an image URL above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("Details")
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = state.title, onValueChange = viewModel::setTitle,
                modifier = Modifier.fillMaxWidth(), label = { Text("Item name") },
                maxLines = 3, shape = RoundedCornerShape(14.dp), colors = fieldColors()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.price, onValueChange = viewModel::setPrice,
                    modifier = Modifier.weight(1.6f), label = { Text("Current price") },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors()
                )
                OutlinedTextField(
                    value = state.currency, onValueChange = viewModel::setCurrency,
                    modifier = Modifier.weight(1f), label = { Text("Currency") },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), colors = fieldColors()
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.listPrice, onValueChange = viewModel::setListPrice,
                    modifier = Modifier.weight(1f), label = { Text("List price") }, placeholder = { Text("Optional") },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors()
                )
                OutlinedTextField(
                    value = state.targetPrice, onValueChange = viewModel::setTargetPrice,
                    modifier = Modifier.weight(1f), label = { Text("Target price") }, placeholder = { Text("Optional") },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors()
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.storeName, onValueChange = viewModel::setStoreName,
                modifier = Modifier.fillMaxWidth(), label = { Text("Store") },
                singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.brand, onValueChange = viewModel::setBrand,
                modifier = Modifier.fillMaxWidth(), label = { Text("Brand") }, placeholder = { Text("Optional") },
                singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.productUrl, onValueChange = viewModel::setProductUrl,
                modifier = Modifier.fillMaxWidth(), label = { Text("Product link") },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), colors = fieldColors()
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("Collection")
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "none") {
                    WishPill(text = "Unsorted", selected = state.categoryId == null, onClick = { viewModel.setCategory(null) })
                }
                items(state.categories, key = { it.category.id }) { category ->
                    WishPill(
                        text = category.category.name,
                        leadingIcon = categoryIcon(category.category.emoji, category.category.name),
                        selected = state.categoryId == category.category.id,
                        onClick = { viewModel.setCategory(category.category.id) }
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("Note")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.notes, onValueChange = viewModel::setNotes,
                modifier = Modifier.fillMaxWidth(), label = { Text("Size, colour, why you want it…") },
                minLines = 2, shape = RoundedCornerShape(14.dp), colors = fieldColors()
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("Description")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.description, onValueChange = viewModel::setDescription,
                modifier = Modifier.fillMaxWidth(), label = { Text("Product description (optional)") },
                minLines = 3, maxLines = 8, shape = RoundedCornerShape(14.dp), colors = fieldColors()
            )

            if (state.specs.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionLabel("Specs")
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp), color = colors.surface,
                    border = BorderStroke(1.dp, extra.hairline), modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        state.specs.take(30).forEachIndexed { index, (k, v) ->
                            if (index > 0) HorizontalDivider(color = extra.hairline.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(k, style = MaterialTheme.typography.labelLarge, color = extra.muted, modifier = Modifier.weight(0.9f))
                                Text(v, style = MaterialTheme.typography.bodySmall, color = colors.onSurface, modifier = Modifier.weight(1.1f))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }

        Column {
            HorizontalDivider(color = extra.hairline)
            Column(
                modifier = Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (state.saveError != null) {
                    NoticeBar(text = state.saveError.orEmpty(), isError = true, onDismiss = viewModel::dismissError)
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = { viewModel.save(onSaved) },
                    enabled = !state.saving,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
                ) {
                    if (state.saving) {
                        LoadingSpinner(size = 16.dp, color = colors.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Saving…")
                    } else {
                        Text("Save changes")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageRow(
    url: String,
    index: Int,
    total: Int,
    isPrimary: Boolean,
    onMakePrimary: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, if (isPrimary) colors.primary.copy(alpha = 0.45f) else extra.hairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductThumb(
                imageUrl = url,
                storeName = if (isPrimary) "Primary" else "Image",
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPrimary) "Primary image" else "Image ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isPrimary) colors.primary else colors.onSurface
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!isPrimary) {
                IconButton(onClick = onMakePrimary, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Star, contentDescription = "Make primary", tint = extra.muted, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up", tint = if (index > 0) extra.muted else extra.hairline, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down", tint = if (index < total - 1) extra.muted else extra.hairline, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove image", tint = extra.terracotta, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = LocalWishExtraColors.current.hairline,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    cursorColor = MaterialTheme.colorScheme.primary
)
