package com.punkstore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Steam mobil gibi arama: yazarken üstte açılan açılır menü (arkadaki sayfa açık kalır, kararır);
 * Enter / "tüm sonuçlar" ile Steam'in sonuç sayfası (Sort by + Filters + N sonuç + satırlar).
 */
@Composable
fun SteamSearch(s: Store, onOpen: (String) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    val st = rememberSearch(s, q, full)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(q) { if (q.isNotBlank()) full = false }
    fun submit() { full = true; if (q.isNotBlank()) s.noteSearch(q) }

    @Composable fun Field(mod: Modifier) = TextField(q, { q = it }, mod.focusRequester(focus), singleLine = true, placeholder = { Text(t("Ara", "Search"), color = Steam.dim) },
        leadingIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Geri", "Back"), tint = Steam.dim) } },
        trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Filled.Close, null, tint = Steam.dim) } else Icon(Icons.Filled.Search, null, tint = Steam.dim) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }),
        colors = TextFieldDefaults.colors(focusedContainerColor = Steam.card, unfocusedContainerColor = Steam.card, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Steam.blue))

    if (full) {
        Column(Modifier.fillMaxSize().background(Steam.bg).statusBarsPadding()) {
            Field(Modifier.fillMaxWidth().padding(12.dp, 8.dp))
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(3.dp)).background(Steam.card).clickable { filterOpen = true }.padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Sırala: ", "Sort by: "), color = Steam.dim, fontSize = 15.sp)
                    Text(t(s.filters.sort.tr, s.filters.sort.en), color = Steam.link, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Filled.ArrowDropDown, null, tint = Steam.link)
                }
                Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Steam.card).clickable { filterOpen = true }.padding(16.dp, 10.dp)) {
                    Text(t("Filtreler", "Filters") + if (s.filters.count > 0) " (${s.filters.count})" else "", color = Color.White, fontSize = 15.sp)
                }
            }
            Text(t("${st.res.size} sonuç eşleşti.", "${st.res.size} results match your search."), Modifier.padding(14.dp, 12.dp), color = Color.White, fontSize = 14.sp)
            if (st.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp), color = Steam.blue)
            LazyColumn { items(st.res, key = { it.pkg }) { AppRow(s, it, onOpen) } }
        }
        if (filterOpen) FilterSheet(s) { filterOpen = false }
        return
    }

    // Açılır menü modu: arka plan sayfası görünür kalır, kararır; boşluğa dokununca kapanır
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(remember { MutableInteractionSource() }, null) { onClose() })
        Column(Modifier.fillMaxWidth().background(Steam.panel).statusBarsPadding()) {
            Field(Modifier.fillMaxWidth().padding(12.dp, 8.dp))
            if (st.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp), color = Steam.blue)
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                if (q.isBlank()) {
                    if (s.searches.isNotEmpty()) item { Text(t("Son aramalar", "Recent searches"), Modifier.padding(14.dp, 8.dp, 14.dp, 2.dp), color = Steam.dim, fontSize = 12.sp) }
                    items(s.searches.toList()) { h ->
                        Row(Modifier.fillMaxWidth().clickable { q = h }.padding(14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.History, null, tint = Steam.dim, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(12.dp)); Text(h, color = Color.White)
                        }
                    }
                } else {
                    items(st.res.take(8), key = { it.pkg }) { a ->
                        Row(Modifier.fillMaxWidth().clickable { s.noteSearch(q); onOpen(a.pkg) }.padding(10.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Capsule(a, Modifier.width(92.dp).height(43.dp), 30, 3)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(when (a.source) { "STEAM" -> "Steam"; "PLAY" -> "Google Play"; else -> "F-Droid" }, color = Steam.dim, fontSize = 12.sp)
                            }
                            Text(a.price.ifBlank { t("Ücretsiz", "Free") }, color = if (a.discount > 0) Steam.greenB else Color.White, fontSize = 13.sp)
                        }
                    }
                    item {
                        Text(t("Tüm sonuçları göster", "See all results") + " (${st.res.size})", Modifier.fillMaxWidth().clickable { submit() }.padding(14.dp), color = Steam.link, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
