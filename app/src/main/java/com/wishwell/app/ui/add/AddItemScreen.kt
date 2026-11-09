package com.wishwell.app.ui.add

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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
fun AddItemScreen(
    container: AppContainer,
    initialUrl: String,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val viewModel: AddItemViewModel = viewModel(
        key = "add-${initialUrl.hashCode()}",
        factory = viewModelFactory {
            initializer { AddItemViewModel(container, initialUrl) }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                text = "NEW ITEM",
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
            Text(
                text = "Save a want.",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Paste a link from Amazon, Etsy, Nike, Target — any store. We'll fill in what we can.",
                style = MaterialTheme.typography.bodyMedium,
                color = extra.muted
            )

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::setUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Product link") },
                placeholder = { Text("https://…", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                colors = fieldColors()
            )

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = viewModel::fetchDetails,
                enabled = !state.fetching && state.url.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                )
            ) {
                if (state.fetching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Reading the page…")
                } else {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Fetch details")
                }
            }

            if (state.fetchError != null) {
                Spacer(Modifier.height(10.dp))
                val msg = state.fetchError.orEmpty()
                NoticeBar(
                    text = msg,
                    isError = !msg.startsWith("Details found"),
                    onDismiss = viewModel::dismissError
                )
            } else if (state.detailsFetched) {
                Spacer(Modifier.height(10.dp))
                NoticeBar(
                    text = "Details found. Check everything below before saving — stores change their pages a lot.",
                    onDismiss = viewModel::dismissError
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("The details")
            Spacer(Modifier.height(10.dp))

            if (state.imageUrl.isNotBlank()) {
                ProductThumb(
                    imageUrl = state.imageUrl,
                    storeName = state.storeName.ifBlank { state.title },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    shape = RoundedCornerShape(18.dp),
                    storeUrl = state.url
                )
                if (state.imageUrls.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${state.imageUrls.size} images captured for the carousel",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.muted
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Item name") },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors()
            )
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.price,
                    onValueChange = viewModel::setPrice,
                    modifier = Modifier.weight(1.6f),
                    label = { Text("Current price") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = state.currency,
                    onValueChange = viewModel::setCurrency,
                    modifier = Modifier.weight(1f),
                    label = { Text("Currency") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    colors = fieldColors()
                )
            }
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.listPrice,
                    onValueChange = viewModel::setListPrice,
                    modifier = Modifier.weight(1f),
                    label = { Text("List price") },
                    placeholder = { Text("Optional") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = state.targetPrice,
                    onValueChange = viewModel::setTargetPrice,
                    modifier = Modifier.weight(1f),
                    label = { Text("Target price") },
                    placeholder = { Text("Optional") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors()
                )
            }
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = state.storeName,
                onValueChange = viewModel::setStoreName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Store") },
                placeholder = { Text("Filled from the link") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors()
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = state.imageUrl,
                onValueChange = viewModel::setImageUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Image URL") },
                placeholder = { Text("Optional") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = fieldColors()
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("Collection")
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "none") {
                    WishPill(
                        text = "Unsorted",
                        selected = state.categoryIds.isEmpty(),
                        onClick = { viewModel.clearCategories() }
                    )
                }
                items(state.categories, key = { it.category.id }) { category ->
                    WishPill(
                        text = category.category.name,
                        leadingIcon = categoryIcon(category.category.emoji, category.category.name),
                        selected = state.categoryIds.contains(category.category.id),
                        onClick = { viewModel.toggleCategory(category.category.id) }
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("Note")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Size, colour, why you want it…") },
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors()
            )

            if (state.description.isNotBlank() || state.detailsFetched) {
                Spacer(Modifier.height(22.dp))
                SectionLabel("Description")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Product description (optional)") },
                    placeholder = { Text("Fetched from the product page when available") },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors()
                )
            }

            if (state.specs.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionLabel("Specs")
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, extra.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        state.specs.take(30).forEachIndexed { index, (k, v) ->
                            if (index > 0) HorizontalDivider(color = extra.hairline.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = k,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = extra.muted,
                                    modifier = Modifier.weight(0.9f)
                                )
                                Text(
                                    text = v,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurface,
                                    modifier = Modifier.weight(1.1f)
                                )
                            }
                        }
                        if (state.specs.size > 30) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "+${state.specs.size - 30} more specs saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.muted
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = extra.sunken.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "A note on automatic checks",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Some retailers block automated price checks. Wishwell will try on a schedule, and you can always update the price by hand from the item page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
        }

        Column {
            HorizontalDivider(color = extra.hairline)
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (state.saveError != null) {
                    NoticeBar(
                        text = state.saveError.orEmpty(),
                        isError = true,
                        onDismiss = viewModel::dismissError
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = { viewModel.save(onSaved) },
                    enabled = !state.saving,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    )
                ) {
                    if (state.saving) {
                        LoadingSpinner(size = 16.dp, color = colors.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Saving…")
                    } else {
                        Text("Save to wishlist")
                    }
                }
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
