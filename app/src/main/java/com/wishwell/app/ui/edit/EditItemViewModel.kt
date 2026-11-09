package com.wishwell.app.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wishwell.app.data.model.CategoryWithCount
import com.wishwell.app.di.AppContainer
import com.wishwell.app.util.validCurrencyOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import java.util.Locale

data class EditItemUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val title: String = "",
    val storeName: String = "",
    val productUrl: String = "",
    val imageInput: String = "",
    val imageUrls: List<String> = emptyList(),
    val price: String = "",
    val listPrice: String = "",
    val currency: String = "USD",
    val brand: String = "",
    val targetPrice: String = "",
    val notes: String = "",
    val description: String = "",
    val specs: List<Pair<String, String>> = emptyList(),
    val categoryIds: Set<Long> = emptySet(),
    val categories: List<CategoryWithCount> = emptyList(),
    val saving: Boolean = false,
    val saveError: String? = null
)

class EditItemViewModel(
    private val container: AppContainer,
    private val itemId: Long
) : ViewModel() {

    private val repo = container.repository
    private val _state = MutableStateFlow(EditItemUiState())
    val state: StateFlow<EditItemUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeCategories().collect { categories ->
                _state.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            val item = repo.observeItem(itemId).first()
            if (item == null) {
                _state.update { it.copy(loading = false, notFound = true) }
                return@launch
            }
            val itemTags = repo.observeItemTags().first()
                .filter { it.itemId == itemId }
                .map { it.categoryId }
                .toSet()
            val w = item.item
            val images = w.imageUrls().ifEmpty { listOfNotNull(w.imageUrl?.takeIf { it.isNotBlank() }) }
            _state.update {
                it.copy(
                    loading = false,
                    title = w.title,
                    storeName = w.storeName,
                    productUrl = w.productUrl,
                    imageUrls = images,
                    price = w.currentPrice?.let(::plainPrice).orEmpty(),
                    listPrice = w.listPrice?.let(::plainPrice).orEmpty(),
                    currency = w.currencyCode.ifBlank { "USD" },
                    brand = w.brand.orEmpty(),
                    targetPrice = w.targetPrice?.let(::plainPrice).orEmpty(),
                    notes = w.notes,
                    description = w.description,
                    specs = w.specs(),
                    categoryIds = itemTags
                )
            }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v, saveError = null) }
    fun setStoreName(v: String) = _state.update { it.copy(storeName = v) }
    fun setProductUrl(v: String) = _state.update { it.copy(productUrl = v) }
    fun setPrice(v: String) = _state.update { it.copy(price = v, saveError = null) }
    fun setListPrice(v: String) = _state.update { it.copy(listPrice = v) }
    fun setCurrency(v: String) = _state.update { it.copy(currency = v.uppercase().take(3)) }
    fun setBrand(v: String) = _state.update { it.copy(brand = v) }
    fun setTargetPrice(v: String) = _state.update { it.copy(targetPrice = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v.take(600)) }
    fun setDescription(v: String) = _state.update { it.copy(description = v.take(4000)) }
    fun toggleCategory(id: Long) = _state.update { current ->
        val next = current.categoryIds.toMutableSet()
        if (!next.add(id)) next.remove(id)
        current.copy(categoryIds = next)
    }

    fun clearCategories() = _state.update { current -> current.copy(categoryIds = emptySet()) }
    fun setImageInput(v: String) = _state.update { it.copy(imageInput = v) }
    fun dismissError() = _state.update { it.copy(saveError = null) }

    fun addImage() {
        val raw = _state.value.imageInput.trim()
        if (raw.isBlank()) return
        val normalized = normalizeImageUrl(raw) ?: raw
        _state.update { s ->
            if (s.imageUrls.any { it.equals(normalized, ignoreCase = true) }) s.copy(imageInput = "")
            else s.copy(imageUrls = (s.imageUrls + normalized).distinct().take(12), imageInput = "")
        }
    }

    fun removeImage(index: Int) = _state.update { s ->
        s.copy(imageUrls = s.imageUrls.filterIndexed { i, _ -> i != index })
    }

    fun moveImage(from: Int, to: Int) = _state.update { s ->
        val list = s.imageUrls.toMutableList()
        if (from !in list.indices || to !in list.indices) return@update s
        val item = list.removeAt(from)
        list.add(to, item)
        s.copy(imageUrls = list)
    }

    fun makePrimary(index: Int) = moveImage(index, 0)

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (current.saving || current.loading || current.notFound) return
        val title = current.title.trim()
        if (title.isEmpty()) {
            _state.update { it.copy(saveError = "Give the item a name first.") }
            return
        }
        val price = current.price.trim().takeIf { it.isNotEmpty() }?.let(repo::parsePriceInput)
        if (current.price.isNotBlank() && price == null) {
            _state.update { it.copy(saveError = "That current price doesn't look right.") }
            return
        }
        val currency = validCurrencyOrNull(current.currency)
        if (currency == null) {
            _state.update { it.copy(saveError = "Use a three-letter currency code, like USD.") }
            return
        }
        val target = current.targetPrice.trim().takeIf { it.isNotEmpty() }?.let(repo::parsePriceInput)
        if (current.targetPrice.isNotBlank() && target == null) {
            _state.update { it.copy(saveError = "That target price doesn't look right.") }
            return
        }
        val listPrice = current.listPrice.trim().takeIf { it.isNotEmpty() }?.let(repo::parsePriceInput)
        if (current.listPrice.isNotBlank() && listPrice == null) {
            _state.update { it.copy(saveError = "That list price doesn't look right.") }
            return
        }

        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                repo.updateItem(
                    id = itemId,
                    title = title,
                    storeName = current.storeName,
                    productUrl = current.productUrl.trim(),
                    imageUrls = current.imageUrls,
                    currencyCode = currency,
                    currentPrice = price,
                    listPrice = listPrice,
                    targetPrice = target,
                    categoryIds = current.categoryIds,
                    notes = current.notes,
                    description = current.description,
                    specs = current.specs,
                    brand = current.brand.trim().takeIf { it.isNotEmpty() }
                )
                onSaved()
            } catch (t: Throwable) {
                _state.update { it.copy(saving = false, saveError = t.message ?: "Couldn't save changes.") }
            }
        }
    }

    private fun normalizeImageUrl(raw: String): String? = runCatching {
        val t = raw.trim()
        if (t.isBlank()) return@runCatching null
        if (t.startsWith("http://") || t.startsWith("https://")) return@runCatching t
        if (t.startsWith("//")) return@runCatching "https:$t"
        "https://$t"
    }.getOrNull()
}

private fun plainPrice(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.2f", value)
