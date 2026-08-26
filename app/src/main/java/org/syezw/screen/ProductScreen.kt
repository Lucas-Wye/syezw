package org.syezw.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.syezw.data.ProductOffer
import org.syezw.model.ProductViewModel
import java.text.SimpleDateFormat
import java.util.*

private fun dateText(value: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
private fun number(value: Double) = String.format(Locale.getDefault(), "%.2f", value)

@Composable
fun ProductScreen(viewModel: ProductViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    if (state.selectedName != null) { ProductDetailScreen(viewModel, state.selectedName!!, modifier); return }
    var adding by remember { mutableStateOf(false) }
    val grouped = viewModel.visibleOffers().groupBy { it.name }
    Scaffold(modifier = modifier, floatingActionButton = { FloatingActionButton({ adding = true }) { Icon(Icons.Default.Add, "添加商品") } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OutlinedTextField(state.searchQuery, viewModel::setSearchQuery, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("搜索商品或商家...") }, leadingIcon = { Icon(Icons.Default.Search, "搜索") }, trailingIcon = { if (state.searchQuery.isNotEmpty()) IconButton({ viewModel.setSearchQuery("") }) { Icon(Icons.Default.Clear, "清除") } }) }
            if (grouped.isEmpty()) item { Text("暂无商品，点击右下角添加", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(grouped.entries.toList(), key = { it.key }) { (name, entries) ->
                val colors = listOf(Color(0xFFE8F5E9), Color(0xFFFFF3E0), Color(0xFFE3F2FD), Color(0xFFF3E5F5))
                Card(Modifier.fillMaxWidth().clickable { viewModel.openProduct(name) }, colors = CardDefaults.cardColors(colors[name.hashCode().ushr(1) % colors.size])) { Column(Modifier.padding(14.dp)) { Text(name, style = MaterialTheme.typography.titleMedium); Text("${entries.size} 个商家 · 最低单价 ¥${number(entries.minOf { it.unitPrice })}/${entries.first().quantityUnit}"); Text(entries.joinToString("、") { it.merchant }, style = MaterialTheme.typography.bodySmall) } }
            }
        }
    }
    if (adding) OfferEditor(null, { adding = false }, { viewModel.save(it); adding = false })
}

@Composable
private fun ProductDetailScreen(viewModel: ProductViewModel, name: String, modifier: Modifier) {
    val offers = viewModel.offersForSelected(); var deleting by remember { mutableStateOf<ProductOffer?>(null) }; var editing by remember { mutableStateOf<ProductOffer?>(null) }
    Scaffold(modifier = modifier) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.fillMaxWidth()) { IconButton(viewModel::closeProduct) { Icon(Icons.Default.ArrowBack, "返回") }; Text(name, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge) } }
        items(offers, key = { it.uuid }) { offer -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(offer.merchant, style = MaterialTheme.typography.titleMedium); Text("¥${number(offer.price)}") }; Text("含量：${number(offer.quantity)} ${offer.quantityUnit}"); Text("单价：¥${number(offer.unitPrice)} / ${offer.quantityUnit}", color = MaterialTheme.colorScheme.primary); Text("添加日期：${dateText(offer.timestamp)}", style = MaterialTheme.typography.bodySmall); Row { IconButton({ editing = offer }) { Icon(Icons.Default.Edit, "更新") }; IconButton({ deleting = offer }) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) } } } } }
    } }
    editing?.let { OfferEditor(it, { editing = null }, { viewModel.save(it); editing = null }) }
    deleting?.let { offer -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除报价？") }, text = { Text("确定删除 ${offer.merchant} 的报价吗？") }, confirmButton = { TextButton({ viewModel.delete(offer); deleting = null }) { Text("删除") } }, dismissButton = { TextButton({ deleting = null }) { Text("取消") } }) }
}

@Composable
private fun OfferEditor(initial: ProductOffer?, onDismiss: () -> Unit, onSave: (ProductOffer) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }; var merchant by remember { mutableStateOf(initial?.merchant ?: "") }; var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }; var quantity by remember { mutableStateOf(initial?.quantity?.toString() ?: "") }; var unit by remember { mutableStateOf(initial?.quantityUnit ?: "g") }
    val valid = name.isNotBlank() && merchant.isNotBlank() && (price.toDoubleOrNull() ?: -1.0) >= 0 && (quantity.toDoubleOrNull() ?: 0.0) > 0
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "添加商品报价" else "更新商品报价") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(name, { name = it }, label = { Text("商品名称") }, singleLine = true); OutlinedTextField(merchant, { merchant = it }, label = { Text("商家") }, singleLine = true); OutlinedTextField(price, { price = it }, label = { Text("价格") }, singleLine = true); OutlinedTextField(quantity, { quantity = it }, label = { Text("含量") }, singleLine = true); OutlinedTextField(unit, { unit = it }, label = { Text("含量单位（g/ml/个等）") }, singleLine = true) } }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(ProductOffer(id = initial?.id ?: 0, uuid = initial?.uuid ?: UUID.randomUUID().toString(), name = name.trim(), merchant = merchant.trim(), price = price.toDouble(), quantity = quantity.toDouble(), quantityUnit = unit.trim().ifBlank { "个" })) }) { Text("保存") } }, dismissButton = { TextButton(onDismiss) { Text("取消") } })
}
