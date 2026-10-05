package com.punkstore

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Aurora Store gibi: Google'ın gömülü giriş sayfası; oauth_token çerezi alınır. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleLoginScreen(onToken: (email: String, token: String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Geri", "Back")) }
            Text(t("Google ile giriş", "Sign in with Google"), Modifier.align(androidx.compose.ui.Alignment.CenterVertically), style = MaterialTheme.typography.titleLarge) }
        AndroidView(factory = { ctx ->
            CookieManager.getInstance().removeAllCookies(null)
            WebView(ctx).apply {
                settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                var done = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
                        val tok = cookies.split(';').map { it.trim() }.firstOrNull { it.startsWith("oauth_token=") }?.substringAfter('=')
                        if (tok != null && !done) {
                            done = true
                            view.evaluateJavascript("(function(){var e=document.querySelector('[data-profile-identifier]');return e?e.textContent:''})()") { raw ->
                                val mail = raw?.trim('"')?.takeIf { it.contains('@') }
                                    ?: cookies.split(';').map { it.trim() }.firstOrNull { it.startsWith("oauth_email=") }?.substringAfter('=')?.let { java.net.URLDecoder.decode(it, "UTF-8") }.orEmpty()
                                onToken(mail, tok)
                            }
                        }
                    }
                }
                loadUrl("https://accounts.google.com/EmbeddedSetup")
            }
        }, modifier = Modifier.fillMaxSize())
    }
}
