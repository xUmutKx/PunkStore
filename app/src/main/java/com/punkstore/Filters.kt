package com.punkstore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FilterButton(s: Store, onClick: () -> Unit) {
    BadgedBox({ if (s.filters.count > 0) Badge { Text("${s.filters.count}") } }) {
        IconButton(onClick) { Icon(Icons.Filled.FilterList, t("Filtre", "Filter"), tint = if (s.filters.active) Steam.btn else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(s: Store, onDismiss: () -> Unit) {
    var f by remember { mutableStateOf(s.filters) }
    ModalBottomSheet(onDismissRequest = { s.applyFilters(f); onDismiss() }, containerColor = if (s.design.steam) Steam.panel else MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Filtreler", "Filters"), Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton({ f = Filters(sort = f.sort) }) { Text(t("Temizle", "Clear")) }
            }
            @Composable fun Sw(label: String, hint: String, v: Boolean, on: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth().clickable { on(!v) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(label); Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(v, on)
            }
            Sw(t("Reklamsız", "No ads"), t("Reklam içerdiği bilinen uygulamaları gizle", "Hide apps known to contain ads"), f.noAds) { f = f.copy(noAds = it) }
            Sw(t("İzleyicisiz", "No trackers"), t("F-Droid'de 'Tracking' işaretli olanları gizle", "Hide apps flagged 'Tracking' on F-Droid"), f.noTracking) { f = f.copy(noTracking = it) }
            Sw(t("Yalnızca ücretsiz", "Free only"), t("Ücretli Play uygulamalarını gizle", "Hide paid Play apps"), f.freeOnly) { f = f.copy(freeOnly = it) }
            Sw(t("Tamamen özgür (FOSS)", "Fully free (FOSS)"), t("Sadece F-Droid'deki, kapalı kaynak bileşeni olmayanlar", "Only F-Droid apps without non-free components"), f.foss) { f = f.copy(foss = it) }
            Text(t("En az yıldız", "Minimum stars"), Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
            Text(t("Puan yalnızca Google Play'de var", "Ratings exist only on Google Play"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0f to t("Hepsi", "Any"), 3f to "★ 3+", 3.5f to "★ 3.5+", 4f to "★ 4+", 4.5f to "★ 4.5+").forEach { (v, l) -> FilterChip(f.minRating == v, { f = f.copy(minRating = v) }, { Text(l) }) }
            }
            Text(t("Kaynak", "Source"), Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(t("Hepsi", "All"), "F-Droid", "Google Play").forEachIndexed { i, l -> FilterChip(f.source == i, { f = f.copy(source = i) }, { Text(l) }) }
            }
            Text(t("Sırala", "Sort by"), Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SortBy.values().forEach { b -> FilterChip(f.sort == b, { f = f.copy(sort = b) }, { Text(t(b.tr, b.en)) }) } }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (s.design.steam) Steam.corner.dp else 20.dp)).background(if (s.design.steam) Steam.btn else MaterialTheme.colorScheme.primary).clickable { s.applyFilters(f); onDismiss() }.padding(14.dp), contentAlignment = Alignment.Center) {
                Text(t("Uygula", "Apply"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
