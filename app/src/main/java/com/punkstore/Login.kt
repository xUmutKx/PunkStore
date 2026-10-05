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
                settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.databaseEnabled = true; settings.allowContentAccess = true
                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                var done = false
                // Aurora Store gibi: giriş sayfasındaki 'profileIdentifier' öğesinden e-posta okunur; öğe geç gelebilir, birkaç kez denenir
                fun readMail(view: WebView, cookies: String, tok: String, tries: Int) {
                    view.evaluateJavascript("(function(){var e=document.getElementById('profileIdentifier')||document.querySelector('[data-profile-identifier]')||document.querySelector('[data-email]');return e?(e.innerText||e.textContent||e.getAttribute('data-email')||''):''})()") { raw ->
                        val mail = raw?.trim('"')?.replace("\\u0040", "@")?.trim()?.takeIf { it.contains('@') }
                            ?: cookies.split(';').map { it.trim() }.firstOrNull { it.startsWith("oauth_email=") }?.substringAfter('=')?.let { java.net.URLDecoder.decode(it, "UTF-8") }?.takeIf { it.contains('@') }
                        when {
                            mail != null -> onToken(mail, tok)
                            tries < 8 -> view.postDelayed({ readMail(view, cookies, tok, tries + 1) }, 600)
                            else -> { done = false }
                        }
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
                        val tok = cookies.split(';').map { it.trim() }.firstOrNull { it.startsWith("oauth_token=") }?.substringAfter('=')
                        if (tok != null && !done) { done = true; readMail(view, cookies, tok, 0) }
                    }
                }
                loadUrl("https://accounts.google.com/EmbeddedSetup/identifier?flowName=EmbeddedSetupAndroid")
            }
        }, modifier = Modifier.fillMaxSize())
    }
}
