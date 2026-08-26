package org.syezw.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.syezw.data.ProductOffer
import org.syezw.data.ProductOfferDao

data class ProductUiState(val offers: List<ProductOffer> = emptyList(), val searchQuery: String = "", val selectedName: String? = null, val editingOffer: ProductOffer? = null)

class ProductViewModel(private val dao: ProductOfferDao) : ViewModel() {
    private val _uiState = MutableStateFlow(ProductUiState())
    val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()
    init { viewModelScope.launch { dao.getAll().collect { offers -> _uiState.update { it.copy(offers = offers) } } } }
    fun setSearchQuery(value: String) { _uiState.update { it.copy(searchQuery = value) } }
    fun openProduct(name: String) { _uiState.update { it.copy(selectedName = name) } }
    fun closeProduct() { _uiState.update { it.copy(selectedName = null, editingOffer = null) } }
    fun edit(offer: ProductOffer) { _uiState.update { it.copy(editingOffer = offer) } }
    fun cancelEdit() { _uiState.update { it.copy(editingOffer = null) } }
    fun save(offer: ProductOffer) { viewModelScope.launch { val now = System.currentTimeMillis(); val stamped = offer.copy(timestamp = now, updatedAt = now); if (stamped.id == 0) dao.insert(stamped) else dao.update(stamped); _uiState.update { it.copy(editingOffer = null, selectedName = stamped.name) } } }
    fun delete(offer: ProductOffer) { viewModelScope.launch { dao.delete(offer) } }
    fun visibleOffers(): List<ProductOffer> { val q = _uiState.value.searchQuery.trim(); return if (q.isBlank()) _uiState.value.offers else _uiState.value.offers.filter { it.name.contains(q, true) || it.merchant.contains(q, true) } }
    fun offersForSelected(): List<ProductOffer> = _uiState.value.offers.filter { it.name == _uiState.value.selectedName }
}

class ProductViewModelFactory(private val dao: ProductOfferDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ProductViewModel(dao) as T
}
