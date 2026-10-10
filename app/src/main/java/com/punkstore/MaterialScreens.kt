package com.punkstore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private class MatItem(val key: String, val icon: ImageVector, val label: String, val badge: Int = 0)

/** Material menu: plain Material rows with icons, no Steam colours or fonts. */
@Composable
fun MaterialMenu(s: Store, onNav: (String) -> Unit) {
    val main = listOf(
        MatItem("store", Icons.Filled.LocalOffer, t("Mağaza", "Store")),
        MatItem("discover", Icons.Filled.Newspaper, t("Keşfet", "Discover")),
        MatItem("library", Icons.Filled.GridView, t("Kütüphane", "Library")),
        MatItem("updates", Icons.Filled.Notifications, t("Güncellemeler", "Updates"), s.updateCount),
        MatItem("wishlist", Icons.Filled.Favorite, t("İstek listesi", "Wishlist"), s.wishlist.size),
        MatItem("achievements", Icons.Filled.EmojiEvents, t("Başarımlar", "Achievements")),
        MatItem("profile", Icons.Filled.Person, t("Profil", "Profile")),
    )
    val more = listOf(
        MatItem("daily", Icons.Filled.WbSunny, t("Günün uygulaması", "App of the day")),
        MatItem("random", Icons.Filled.Casino, t("Rastgele uygulama", "Random app")),
        MatItem("refresh", Icons.Filled.Refresh, t("Kataloğu yenile", "Refresh catalog")),
        MatItem("settings", Icons.Filled.Settings, t("Ayarlar", "Settings")),
        MatItem("about", Icons.Filled.Info, t("Hakkında", "About")),
    )
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        items(main, key = { it.key }) { m -> MatRow(m, onNav) }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        items(more, key = { it.key }) { m -> MatRow(m, onNav) }
    }
}

@Composable
private fun MatRow(m: MatItem, onNav: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(16.dp)).clickable { onNav(m.key) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(m.icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(m.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (m.badge > 0) Badge { Text("${m.badge}") }
    }
}

/** Material updates: the apps with an update, as the normal Material app rows. */
@Composable
fun MaterialUpdates(s: Store, onOpen: (String) -> Unit) {
    val upd = s.apps.filter { s.isInstalled(it) && s.hasUpdate(it) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        item { Text(t("Güncellemeler", "Updates"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(4.dp)) }
        if (upd.isEmpty()) item { Text(t("Her şey güncel", "Everything is up to date"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
        items(upd, key = { it.pkg }) { a -> AppRow(s, a, onOpen) }
    }
}
