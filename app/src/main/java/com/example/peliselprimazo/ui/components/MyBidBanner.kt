package com.example.peliselprimazo.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MyBidBanner(
    adTagUrl: String,
    modifier: Modifier = Modifier.fillMaxWidth().height(60.dp)
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    // Habilitar protección contra sitios peligrosos
                    safeBrowsingEnabled = true
                }

                webChromeClient = object : WebChromeClient() {
                    // Bloquear cuadros de diálogo JS (evita alertas de "tienes un virus")
                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        result?.confirm()
                        return true
                    }
                    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        result?.confirm()
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        
                        // Blacklist de palabras clave usadas en anuncios fraudulentos
                        val scarewareKeywords = listOf("virus", "cleaner", "security-check", "system-update", "scan", "repair", "infected")
                        if (scarewareKeywords.any { url.lowercase().contains(it) }) {
                            return true // Bloquea la navegación
                        }

                        return if (url.startsWith("http")) {
                            view?.loadUrl(url)
                            true 
                        } else {
                            true // Bloquea market://, intent://, etc.
                        }
                    }

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                        handler?.cancel() // No entrar en sitios con certificados dudosos
                    }
                }
                loadUrl(adTagUrl)
            }
        },
        modifier = modifier
    )
}
