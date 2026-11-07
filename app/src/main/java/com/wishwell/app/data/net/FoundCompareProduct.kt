package com.wishwell.app.data.net

data class FoundCompareProduct(
    val storeName: String,
    val title: String,
    val price: Double?,
    val currencyCode: String,
    val imageUrl: String?,
    val productUrl: String,
    val listPrice: Double? = null,
    val note: String? = null
)

sealed interface CompareSearchState {
    data object Idle : CompareSearchState
    data object Loading : CompareSearchState
    data class Success(val products: List<FoundCompareProduct>, val source: String = "live") : CompareSearchState
    data class Empty(val message: String = "No live product results found.") : CompareSearchState
    data class Error(val message: String) : CompareSearchState
}
