package com.punkstore

/** Arama sıralaması: ad eşleşmesi > ad öneki > kelime öneki > içerir > paket/özet; Play sonuçları kendi popülerlik sırasını korur. */
object Rank {
    private fun norm(x: String) = x.lowercase().replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c').filter { it.isLetterOrDigit() || it == ' ' }.trim()

    fun score(a: AppItem, q: String): Int {
        val n = norm(a.name); val nq = norm(q); val nospace = n.replace(" ", ""); val qq = nq.replace(" ", "")
        var sc = when {
            n == nq || nospace == qq -> 1000
            n.startsWith(nq) || nospace.startsWith(qq) -> 800
            n.split(' ').any { it.startsWith(nq) } -> 600
            n.contains(nq) || nospace.contains(qq) -> 400
            a.pkg.lowercase().contains(qq) -> 200
            norm(a.summary).contains(nq) -> 100
            else -> 0
        }
        if (sc > 0 && a.source == "PLAY") sc += 40
        if (sc > 0 && a.installs.isNotBlank()) sc += 10
        return sc
    }

    /** Birleşik liste: puana göre; eşitlikte kaynaktaki sıra korunur. Hiç eşleşmeyen Play sonuçları (Play kendi alaka sırasını bilir) sona eklenir. */
    fun merge(q: String, vararg lists: List<AppItem>): List<AppItem> {
        val all = lists.flatMap { it }.distinctBy { it.pkg }
        val scored = all.map { it to score(it, q) }
        return scored.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first } + scored.filter { it.second == 0 && it.first.source == "PLAY" }.map { it.first }
    }

    /** Çok bilinen uygulamalar: Play araması bir sebeple göstermese de paket adıyla doğrudan bulunur. */
    val aliases = mapOf(
        "whatsapp" to "com.whatsapp", "instagram" to "com.instagram.android", "telegram" to "org.telegram.messenger", "facebook" to "com.facebook.katana",
        "messenger" to "com.facebook.orca", "tiktok" to "com.zhiliaoapp.musically", "youtube" to "com.google.android.youtube", "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient", "twitter" to "com.twitter.android", "x" to "com.twitter.android", "snapchat" to "com.snapchat.android", "discord" to "com.discord",
        "chrome" to "com.android.chrome", "gmail" to "com.google.android.gm", "maps" to "com.google.android.apps.maps", "haritalar" to "com.google.android.apps.maps",
        "signal" to "org.thoughtcrime.securesms", "viber" to "com.viber.voip", "skype" to "com.skype.raider", "zoom" to "us.zoom.videomeet", "pinterest" to "com.pinterest",
        "reddit" to "com.reddit.frontpage", "twitch" to "tv.twitch.android.app", "firefox" to "org.mozilla.firefox", "vlc" to "org.videolan.vlc", "shazam" to "com.shazam.android",
        "linkedin" to "com.linkedin.android", "whatsapp business" to "com.whatsapp.w4b", "youtube music" to "com.google.android.apps.youtube.music", "drive" to "com.google.android.apps.docs",
        "google drive" to "com.google.android.apps.docs", "translate" to "com.google.android.apps.translate", "çeviri" to "com.google.android.apps.translate", "minecraft" to "com.mojang.minecraftpe",
        "roblox" to "com.roblox.client", "pubg" to "com.tencent.ig", "among us" to "com.innersloth.spacemafia", "candy crush" to "com.king.candycrushsaga", "uber" to "com.ubercab", "clash royale" to "com.supercell.clashroyale", "clash of clans" to "com.supercell.clashofclans", "brawl stars" to "com.supercell.brawlstars", "hay day" to "com.supercell.hayday", "boom beach" to "com.supercell.boombeach", "squad busters" to "com.supercell.squad", "subway surfers" to "com.kiloo.subwaysurf", "genshin impact" to "com.miHoYo.GenshinImpact", "free fire" to "com.dts.freefireth", "call of duty mobile" to "com.activision.callofduty.shooter", "fortnite" to "com.epicgames.fortnite", "temple run" to "com.imangi.templerun", "coin master" to "com.moonactive.coinmaster", "clash mini" to "com.supercell.clashmini", "bitwarden" to "com.x8bit.bitwarden",
    )

    fun guesses(q: String): List<String> {
        val k = norm(q); val c = k.replace(" ", "")
        if (c.length < 3) return emptyList()
        return listOfNotNull(aliases[k] ?: aliases[c]).ifEmpty { listOf("com.$c", "com.$c.android", "org.$c", "com.$c.app") }
    }
}
