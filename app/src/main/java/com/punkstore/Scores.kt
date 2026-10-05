package com.punkstore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MetaYellow = Color(0xFFFFCC33)

/** Metacritic tarzı sarı-siyah puan kutusu. */
@Composable
fun ScoreBadge(score: String, modifier: Modifier = Modifier, size: Int = 46) {
    Box(modifier.size(size.dp).clip(RoundedCornerShape(5.dp)).background(MetaYellow), contentAlignment = Alignment.Center) {
        Text(score, color = Color.Black, fontWeight = FontWeight.Black, fontSize = (size * .42f).sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ScoreLine(score: String, title: String, verdict: String, detail: String, pct: Int?, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), verticalAlignment = Alignment.CenterVertically) {
        ScoreBadge(score)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Steam.dim, fontSize = 12.sp, letterSpacing = .8.sp, fontWeight = FontWeight.SemiBold)
                if (verdict.isNotBlank()) Text("  ·  $verdict", color = Steam.link, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            if (detail.isNotBlank()) Text(detail, color = Steam.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
            if (pct != null) Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF1B2838))) {
                Box(Modifier.fillMaxWidth(pct.coerceIn(0, 100) / 100f).fillMaxHeight().background(MetaYellow))
            }
        }
    }
}

/** Tüm kaynaklardaki puanlar aynı görünümle, ayrıntılı. */
@Composable
fun ScoresBlock(a: AppItem) {
    val meta = a.extra["metacritic"]?.toIntOrNull()
    val steamPct = Regex("(\\d+)%").find(a.review)?.groupValues?.get(1)?.toIntOrNull()
    val steamCount = Regex("/\\s*([\\d.,]+)\\)").find(a.review)?.groupValues?.get(1)
    val steamVerdict = a.review.substringBefore(" (").trim().takeIf { it.isNotBlank() && !it.startsWith("(") }.orEmpty()
    var any = false
    if (meta != null) { any = true
        val v = when { meta >= 90 -> t("Evrensel beğeni", "Universal acclaim"); meta >= 75 -> t("Genel olarak olumlu", "Generally favorable"); meta >= 50 -> t("Karışık", "Mixed or average"); else -> t("Genel olarak olumsuz", "Generally unfavorable") }
        ScoreLine("$meta", "METACRITIC", v, t("Eleştirmen puanı · 100 üzerinden", "Critic score · out of 100"), meta) { a.extra["metaurl"]?.let { Browser.open(it) } }
    }
    if (steamPct != null) { any = true
        ScoreLine("$steamPct", "STEAM", steamVerdict, listOfNotNull(steamCount?.let { t("$it kullanıcı değerlendirmesi", "$it user reviews") }, a.extra["recs"]?.let { t("$it öneri", "$it recommendations") }).joinToString(" · "), steamPct)
    }
    if (steamPct == null && a.source == "PLAY" && a.rating > 0) { any = true
        val pct = (a.rating / 5f * 100).toInt()
        val v = when { pct >= 90 -> t("Çok olumlu", "Very Positive"); pct >= 75 -> t("Olumlu", "Positive"); pct >= 60 -> t("Karışık", "Mixed"); else -> t("Olumsuz", "Negative") }
        ScoreLine("$pct", "GOOGLE PLAY", v, String.format("★ %.1f / 5", a.rating) + a.installs.takeIf { it.isNotBlank() }?.let { " · $it " + t("indirme", "downloads") }.orEmpty(), pct)
    }
    if (!any) Row(Modifier.padding(vertical = 4.dp)) {
        Text(if (a.source == "FDROID") t("AÇIK KAYNAK: ", "OPEN SOURCE: ") else t("PUAN: ", "RATING: "), color = Steam.dim, fontSize = 15.sp)
        Text(a.license.ifBlank { t("henüz yok", "none yet") }, color = Steam.link, fontSize = 15.sp)
    }
}
