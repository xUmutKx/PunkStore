package com.punkstore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Basılı tutunca ne olsun: 0 alttan açılan sayfa, 1 küçük menü, 2 hiçbir şey. */
object LongPressMode { const val SHEET = 0; const val MENU = 1; const val OFF = 2 }

/** Dokununca sayfayı açar; basılı tutunca ayarlara göre alt sayfa / menü gösterir. Menü, bileşenin yanında çizilir. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.appPress(s: Store, a: AppItem, onOpen: (String) -> Unit): Modifier = composed {
    var show by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    if (show) AppActions(s, a, onOpen) { show = false }
    this.combinedClickable(onClick = { onOpen(a.pkg) }, onLongClick = { if (s.longPress != LongPressMode.OFF) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); show = true } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActions(s: Store, a: AppItem, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    class Act(val icon: ImageVector, val label: String, val go: () -> Unit)
    val acts = buildList {
        add(Act(Icons.Filled.OpenInNew, t("Sayfayı aç", "Open page")) { onOpen(a.pkg) })
        add(Act(if (s.isWished(a)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (s.isWished(a)) t("İstek listesinden çıkar", "Remove from wishlist") else t("İstek listesine ekle", "Add to wishlist")) { s.toggleWish(a) })
        if (!s.isInstalled(a)) add(Act(Icons.Filled.VideogameAsset, if (a.pkg in s.libAdded) t("Kütüphaneden çıkar", "Remove from library") else t("Kütüphaneye ekle", "Add to library")) { s.toggleLibrary(a) })
        add(Act(if (a.pkg in s.pins) Icons.Filled.Star else Icons.Filled.StarBorder, if (a.pkg in s.pins) t("Favoriden çıkar", "Unpin") else t("Favorile", "Pin")) { s.togglePin(a) })
        if (s.isInstalled(a)) add(Act(Icons.Filled.PlayArrow, t("Aç", "Open app")) { s.open(ctx, a.pkg) })
        if (s.isInstalled(a)) add(Act(Icons.Filled.Delete, t("Kaldır", "Uninstall")) { s.uninstall(ctx, a.pkg) })
        add(Act(Icons.Filled.VisibilityOff, t("Görüldü say (ana sayfadan gizle)", "Mark as viewed (hide from home)")) { s.noteView(a.pkg) })
        add(Act(Icons.Filled.Share, t("Paylaş", "Share")) { ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "${a.name} — " + (if (a.source == "STEAM") a.web else "https://play.google.com/store/apps/details?id=${a.pkg}")), null)) })
    }
    if (s.longPress == LongPressMode.MENU) {
        DropdownMenu(true, onDismiss) { acts.forEach { c -> DropdownMenuItem({ Text(c.label) }, { onDismiss(); c.go() }, leadingIcon = { Icon(c.icon, null) }) } }
    } else {
        ModalBottomSheet(onDismiss, containerColor = Steam.panel) {
            Text(a.name, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            acts.forEach { c ->
                Row(Modifier.fillMaxWidth().clickable { onDismiss(); c.go() }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(c.icon, null, tint = Steam.link); Spacer(Modifier.width(16.dp)); Text(c.label, color = androidx.compose.ui.graphics.Color.White)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
