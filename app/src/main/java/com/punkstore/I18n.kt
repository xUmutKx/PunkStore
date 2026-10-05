package com.punkstore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

enum class LangPref { AUTO, TR, EN }

/** Uygulama dili. Compose durumu olduğu için değişince arayüz kendiliğinden yenilenir. */
object I18n {
    var pref by mutableStateOf(LangPref.EN)
    val isTr: Boolean get() = when (pref) {
        LangPref.TR -> true
        LangPref.EN -> false
        LangPref.AUTO -> Locale.getDefault().language == "tr"
    }

    private val cats = mapOf(
        "Games" to "Oyunlar", "Internet" to "İnternet", "Multimedia" to "Çoklu ortam", "System" to "Sistem",
        "Connectivity" to "Bağlantı", "Development" to "Geliştirme", "Graphics" to "Grafik", "Money" to "Para",
        "Navigation" to "Navigasyon", "Phone & SMS" to "Telefon ve SMS", "Reading" to "Okuma", "Science & Education" to "Bilim ve eğitim",
        "Security" to "Güvenlik", "Sports & Health" to "Spor ve sağlık", "Theming" to "Temalar", "Time" to "Zaman",
        "Writing" to "Yazı", "Office" to "Ofis", "Education" to "Eğitim", "Music" to "Müzik", "Video" to "Video",
        "Tools" to "Araçlar", "Social" to "Sosyal", "Shopping" to "Alışveriş", "Travel" to "Seyahat", "Weather" to "Hava durumu",
        "Communication" to "İletişim", "Productivity" to "Verimlilik", "Photography" to "Fotoğraf", "Entertainment" to "Eğlence",
        "Finance" to "Finans", "Health & Fitness" to "Sağlık ve fitness", "News & Magazines" to "Haberler", "Books & Reference" to "Kitaplar",
        "Maps & Navigation" to "Haritalar", "Lifestyle" to "Yaşam tarzı", "Food & Drink" to "Yiyecek ve içecek", "Business" to "İş",
        "Personalization" to "Kişiselleştirme", "Art & Design" to "Sanat ve tasarım", "Auto & Vehicles" to "Araçlar", "Medical" to "Tıp",
    )

    fun category(c: String) = if (isTr) cats[c] ?: c else c
}

/** Kısa çeviri yardımcısı: t("Türkçe", "English"). */
fun t(tr: String, en: String) = if (I18n.isTr) tr else en
