package com.punkstore

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Uygulama içi tarayıcı: bağlantılar harici tarayıcıyı açmak yerine bu sayfada açılır. */
object Browser {
    var url by mutableStateOf<String?>(null)
    fun open(u: String) { if (u.isNotBlank()) url = u }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(url: String, onClose: () -> Unit) {
    var title by remember(url) { mutableStateOf(url) }
    var progress by remember(url) { mutableIntStateOf(0) }
    var view by remember { mutableStateOf<WebView?>(null) }
    val back: () -> Unit = { if (view?.canGoBack() == true) view?.goBack() else onClose() }
    DisposableEffect(Unit) { onDispose { view?.destroy() } }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().background(Steam.detailBg)) {
        Row(Modifier.fillMaxWidth().background(Steam.topBrush).statusBarsPadding().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Geri", "Back"), tint = androidx.compose.ui.graphics.Color.White) }
            Text(title, Modifier.weight(1f), color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            IconButton(onClose) { Icon(Icons.Filled.Close, t("Kapat", "Close"), tint = androidx.compose.ui.graphics.Color.White) }
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, Modifier.fillMaxWidth().height(2.dp), color = Steam.btn)
        AndroidView<WebView>(factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                // Yaş doğrulamasını geç (VR / olgun içerik sayfaları giriş istemesin)
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    listOf("https://store.steampowered.com", "https://steamcommunity.com").forEach { h ->
                        setCookie(h, "birthtime=631152001; path=/"); setCookie(h, "lastagecheckage=1-January-1990; path=/"); setCookie(h, "wants_mature_content=1; path=/"); setCookie(h, "mature_content=1; path=/")
                    }
                }
                webViewClient = object : WebViewClient() { override fun shouldOverrideUrlLoading(v: WebView, r: android.webkit.WebResourceRequest) = !r.url.scheme.orEmpty().startsWith("http") }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(v: WebView, p: Int) { progress = p }
                    override fun onReceivedTitle(v: WebView, tt: String?) { if (!tt.isNullOrBlank()) title = tt }
                }
                loadUrl(url); view = this
            }
        }, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}
