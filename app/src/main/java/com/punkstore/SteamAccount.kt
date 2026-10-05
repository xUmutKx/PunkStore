package com.punkstore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/** Steam hesabını bağla: profil ID/özel URL (anahtarsız); oyunlar ve oyun başarımları. */
@Composable
fun SteamAccountScreen(s: Store, onBack: () -> Unit) {
    var who by remember { mutableStateOf(s.steamWho) }
    var profile by remember { mutableStateOf<SteamLink.Profile?>(null) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var game by remember { mutableStateOf<SteamLink.Game?>(null) }
    var achs by remember { mutableStateOf<List<SteamLink.Ach>?>(null) }
    var q by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val lang = if (I18n.isTr) "turkish" else "english"

    fun connect() { loading = true; err = null; scope.launch2 {
        runCatching { SteamLink.load(who.trim(), lang) }.onSuccess { profile = it; s.saveSteam("", who.trim(), it.id) }.onFailure { err = it.message }
        loading = false } }
    LaunchedEffect(Unit) { if (s.steamWho.isNotBlank() && profile == null) connect() }
    LaunchedEffect(game) { achs = null; val g = game ?: return@LaunchedEffect; err = null
        runCatching { SteamLink.achievements(profile!!.id, g.appId, lang) }.onSuccess { achs = it }.onFailure { err = it.message; achs = emptyList() } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ if (game != null) game = null else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Geri", "Back")) }
            Text(game?.name ?: t("Steam hesabı", "Steam account"), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        err?.let { Text(it, Modifier.padding(16.dp, 4.dp), color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        val p = profile
        if (p == null) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Steam profil ID'ni ya da özel URL adını gir (API anahtarı gerekmez). Profilin ve 'Oyun ayrıntıları' herkese açık olmalı.", "Enter your Steam profile ID or vanity URL name (no API key needed). Your profile and 'Game details' must be public."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(who, { who = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(t("Profil ID / özel URL adı", "Profile ID / vanity name")) })
            Button({ connect() }, enabled = !loading && who.isNotBlank()) { Text(t("Bağlan", "Connect")) }
        } else if (game == null) LazyColumn {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(p.avatar, null, Modifier.size(72.dp).clip(RoundedCornerShape(6.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(t("Seviye ${p.level} · ${p.games.size} oyun · ${p.games.sumOf { it.minutes } / 60} saat", "Level ${p.level} · ${p.games.size} games · ${p.games.sumOf { it.minutes } / 60} h"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ profile = null; s.saveSteam("", "", ""); who = "" }) { Text(t("Çıkış", "Unlink")) }
                }
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, placeholder = { Text(t("Oyunlarda ara", "Search games")) })
            }
            items(p.games.filter { it.name.contains(q, true) }.take(200), key = { it.appId }) { g ->
                Row(Modifier.fillMaxWidth().clickable { game = g }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(g.header, null, Modifier.width(120.dp).height(56.dp).clip(RoundedCornerShape(3.dp)).background(Color.DarkGray), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(10.dp))
                    Column { Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); Text("${g.minutes / 60} ${t("saat", "h")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        } else {
            val l = achs
            if (l == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else LazyColumn {
                item { val d = l.count { it.done }; Text("$d / ${l.size} " + t("başarım", "achievements"), Modifier.padding(16.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (l.isNotEmpty()) LinearProgressIndicator({ d.toFloat() / l.size }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(6.dp), color = Steam.btn) }
                items(l, key = { it.api }) { a ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(a.icon, null, Modifier.size(56.dp).clip(RoundedCornerShape(3.dp)).background(Color.DarkGray))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, fontWeight = FontWeight.Bold, color = if (a.done) Color.White else Steam.dim)
                            Text(a.desc.ifBlank { if (a.hidden) t("Gizli başarım", "Hidden achievement") else "" }, fontSize = 12.sp, color = Steam.dim)
                            if (a.done && a.time > 0) Text(t("Açıldı: ", "Unlocked: ") + df.format(Date(a.time * 1000)), fontSize = 11.sp, color = Steam.greenA)
                        }
                        if (a.percent >= 0) Text("%.1f%%".format(a.percent), fontSize = 12.sp, color = Steam.link)
                    }
                }
            }
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launch2(block: suspend () -> Unit) { launch { block() } }
