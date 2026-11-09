package com.wishwell.app.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wishwell.app.data.model.CategoryWithCount
import com.wishwell.app.data.net.HistoricalPrice
import com.wishwell.app.data.net.ScrapeResult
import com.wishwell.app.data.repo.NewItemDraft
import com.wishwell.app.di.AppContainer
import com.wishwell.app.util.validCurrencyOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import java.util.Locale

data class AddItemUiState(
    val url: String = "",
    val fetching: Boolean = false,
    val fetchError: String? = null,
    val detailsFetched: Boolean = false,
    val title: String = "",
    val storeName: String = "",
    val imageUrl: String = "",
    val imageUrls: List<String> = emptyList(),
    val price: String = "",
    val listPrice: String = "",
    val currency: String = "USD",
    val brand: String = "",
    val targetPrice: String = "",
    val notes: String = "",
    val description: String = "",
    val specs: List<Pair<String, String>> = emptyList(),
    val inStock: Boolean? = null,
    val categoryIds: Set<Long> = emptySet(),
    val categories: List<CategoryWithCount> = emptyList(),
    val saving: Boolean = false,
    val saveError: String? = null,
    val fetchedHistory: List<HistoricalPrice> = emptyList()
)

class AddItemViewModel(
    private val container: AppContainer,
    initialUrl: String?
) : ViewModel() {

    private val repo = container.repository

    private val _state = MutableStateFlow(AddItemUiState(url = initialUrl.orEmpty()))
    val state: StateFlow<AddItemUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeCategories().collect { categories ->
                _state.update { current -> current.copy(categories = categories) }
            }
        }
        if (!initialUrl.isNullOrBlank()) {
            _state.update { it.copy(currency = inferCurrency(initialUrl)) }
            fetchDetails()
        }
    }

    private fun inferCurrency(url: String): String {
        val host = runCatching {
            val raw = url.trim()
            if (raw.isEmpty()) return@runCatching null
            URI(if ("://" in raw) raw else "https://$raw").host
        }.getOrNull()
        return container.storeRegistry.defaultCurrencyFor(host)
    }

    fun setUrl(value: String) = _state.update {
        val inferred = inferCurrency(value)
        it.copy(url = value, fetchError = null, currency = inferred)
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value, saveError = null) }

    fun setStoreName(value: String) = _state.update { it.copy(storeName = value) }

    fun setPrice(value: String) = _state.update { it.copy(price = value, saveError = null) }

    fun setListPrice(value: String) = _state.update { it.copy(listPrice = value) }

    fun setCurrency(value: String) = _state.update {
        it.copy(currency = value.uppercase().take(3))
    }

    fun setBrand(value: String) = _state.update { it.copy(brand = value) }

    fun setTargetPrice(value: String) = _state.update { it.copy(targetPrice = value) }

    fun setNotes(value: String) = _state.update { it.copy(notes = value.take(600)) }

    fun setDescription(value: String) = _state.update { it.copy(description = value.take(4000)) }

    fun setImageUrl(value: String) = _state.update { it.copy(imageUrl = value) }

    fun toggleCategory(id: Long) = _state.update { current ->
        val next = current.categoryIds.toMutableSet()
        if (!next.add(id)) next.remove(id)
        current.copy(categoryIds = next)
    }

    fun clearCategories() = _state.update { current -> current.copy(categoryIds = emptySet()) }

    fun dismissError() = _state.update { it.copy(fetchError = null, saveError = null) }

    fun fetchDetails() {
        val current = _state.value
        if (current.fetching) return
        if (current.url.isBlank()) {
            _state.update { it.copy(fetchError = "Paste a product link first.") }
            return
        }

        _state.update { it.copy(fetching = true, fetchError = null, saveError = null) }
        viewModelScope.launch {
            when (val result = repo.fetchProduct(current.url)) {
                is ScrapeResult.Success -> {
                    val metadata = result.metadata
                    _state.update { s ->
                        val imgs = metadata.imageUrls.ifEmpty { listOfNotNull(metadata.imageUrl) }
                        val extras = buildList {
                            if (metadata.priceHistory.isNotEmpty()) add("${metadata.priceHistory.size} past price point(s)")
                            if (imgs.size > 1) add("${imgs.size} images")
                            if (!metadata.description.isNullOrBlank()) add("description")
                            if (metadata.specs.isNotEmpty()) add("${metadata.specs.size} spec(s)")
                            metadata.inStock?.let { add(if (it) "in stock" else "out of stock") }
                        }
                        s.copy(
                            fetching = false,
                            detailsFetched = true,
                            title = metadata.title ?: s.title,
                            storeName = metadata.storeName ?: s.storeName,
                            imageUrl = imgs.firstOrNull() ?: metadata.imageUrl ?: s.imageUrl,
                            imageUrls = if (imgs.isNotEmpty()) imgs else s.imageUrls,
                            price = metadata.price?.let(::plainPrice) ?: s.price,
                            listPrice = metadata.listPrice?.let(::plainPrice) ?: s.listPrice,
                            currency = metadata.currencyCode ?: inferCurrency(metadata.finalUrl.ifBlank { s.url }).ifBlank { s.currency },
                            brand = metadata.brand ?: s.brand,
                            description = if (s.description.isBlank()) metadata.description.orEmpty() else s.description,
                            specs = if (metadata.specs.isNotEmpty()) metadata.specs else s.specs,
                            inStock = metadata.inStock ?: s.inStock,
                            fetchedHistory = metadata.priceHistory,
                            fetchError = if (metadata.price == null) {
                                "We found the product but not its price. Add it below."
                            } else if (extras.isNotEmpty()) {
                                "Details found, including ${extras.joinToString(", ")}."
                            } else {
                                null
                            }
                        )
                    }
                }

                is ScrapeResult.Failure -> _state.update {
                    it.copy(fetching = false, fetchError = result.message)
                }
            }
        }
    }

    fun save(onSaved: (Long) -> Unit) {
        val current = _state.value
        if (current.saving) return

        val title = current.title.trim()
        if (title.isEmpty()) {
            _state.update { it.copy(saveError = "Give the item a name first.") }
            return
        }
        val price = repo.parsePriceInput(current.price)
        if (price == null) {
            _state.update { it.copy(saveError = "Add the current price so we can track it.") }
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

        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val id = repo.addItem(
                    NewItemDraft(
                        title = title,
                        storeName = current.storeName,
                        productUrl = current.url.trim(),
                        imageUrl = current.imageUrl.trim().takeIf { it.isNotEmpty() },
                        imageUrls = current.imageUrls.ifEmpty { listOfNotNull(current.imageUrl.trim().takeIf { it.isNotEmpty() }) },
                        price = price,
                        listPrice = listPrice,
                        currencyCode = currency,
                        targetPrice = target,
                        categoryIds = current.categoryIds,
                        notes = current.notes,
                        brand = current.brand.trim().takeIf { it.isNotEmpty() },
                        priceHistory = current.fetchedHistory,
                        description = current.description,
                        specs = current.specs,
                        inStock = current.inStock
                    )
                )
                onSaved(id)
            } catch (t: Throwable) {
                _state.update {
                    it.copy(saving = false, saveError = t.message ?: "Couldn't save that item.")
                }
            }
        }
    }
}

internal fun plainPrice(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.2f", value)
