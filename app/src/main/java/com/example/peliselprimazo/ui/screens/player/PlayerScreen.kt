package com.example.peliselprimazo.ui.screens.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.peliselprimazo.data.AdsManager
import com.example.peliselprimazo.ui.components.ParticleLoading

@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    adsManager: AdsManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val view = LocalView.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        activity?.let { act ->
            val window = act.window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = true
        }
        
        onDispose {
            activity?.let { act ->
                val window = act.window
                val controller = WindowCompat.getInsetsController(window, view)
                controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                view.keepScreenOn = false
            }
        }
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is PlayerUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    ParticleLoading(size = 200.dp)
                }
            }
            is PlayerUiState.Success -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    WebPlayer(
                        url = state.videoUrl, 
                        onBack = onBack,
                        onNext = state.nextLink?.let { next ->
                            {
                                activity?.let { act ->
                                    adsManager.showRewardedVideo(act) {
                                        viewModel.loadVideo(next.serverName, next.fileId, state.movie.id)
                                    }
                                } ?: viewModel.loadVideo(next.serverName, next.fileId, state.movie.id)
                            }
                        }
                    )
                    
                    if (state.isChangingEpisode) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            ParticleLoading(size = 200.dp)
                        }
                    }
                }
            }
            is PlayerUiState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Error, null, tint = Color.Red, modifier = Modifier.size(48.dp))
                    Text(state.message, color = Color.White, modifier = Modifier.padding(16.dp))
                    Button(onClick = onBack) { Text("Volver") }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPlayer(
    url: String, 
    onBack: () -> Unit,
    onNext: (() -> Unit)? = null
) {
    var isWebViewLoading by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    visibility = View.INVISIBLE
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        
                        // Configuración para el control de ventanas
                        setSupportMultipleWindows(true) 
                        javaScriptCanOpenWindowsAutomatically = false
                        mediaPlaybackRequiresUserGesture = false
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isWebViewLoading = true
                            view?.visibility = View.INVISIBLE
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val uri = request?.url ?: return true
                            val urlString = uri.toString()
                            
                            // 1. Bloqueo de esquemas no estándar (publicidad que intenta abrir apps)
                            if (uri.scheme != "http" && uri.scheme != "https") return true
                            
                            // 2. Control de navegación: Solo permitir Streamtape y sus recursos
                            val allowedHosts = listOf("streamtape.com", "streamtape.to", "tapecontent.net")
                            val isAllowedHost = allowedHosts.any { uri.host?.contains(it) == true }
                            
                            // Recursos necesarios para la carga del reproductor
                            val isResource = urlString.contains("static") || 
                                            urlString.contains("video") || 
                                            urlString.contains(".mp4")
                            
                            // Si no es un host permitido ni un recurso, bloqueamos (true)
                            return !(isAllowedHost || isResource)
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val requestUrl = request?.url?.toString()?.lowercase() ?: return null
                            
                            // Lista de dominios y patrones conocidos de redes publicitarias
                            val adDomains = listOf(
                                "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                                "popads.net", "adnxs.com", "adsystem.com", "adservice.google",
                                "onclickads.net", "taboola.com", "outbrain.com", "histats.com",
                                "a.bestcontent", "traffichunt.com", "juicyads.com", "exoclick.com",
                                "propellerads.com", "yandex.ru", "google-analytics.com",
                                "scorecardresearch.com", "quantserve.com", "ad-delivery",
                                "mads.amazon", "serving-sys.com", "flashtalking.com", "casalemedia.com"
                            )

                            // Si la URL coincide con algún patrón publicitario, interceptamos y devolvemos una respuesta vacía
                            if (adDomains.any { requestUrl.contains(it) }) {
                                return WebResourceResponse("text/plain", "UTF-8", null)
                            }

                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            
                            // Inyección de script JS para detectar y ocultar divs superpuestos (overlays/popups)
                            val jsCode = """
                                (function() {
                                    function hideAds() {
                                        var elements = document.getElementsByTagName('*');
                                        for (var i = 0; i < elements.length; i++) {
                                            var el = elements[i];
                                            var style = window.getComputedStyle(el);
                                            
                                            if (style.position === 'fixed' || style.position === 'absolute') {
                                                var zIndex = parseInt(style.zIndex);
                                                if (zIndex > 100 || el.id === 'popads' || el.className.indexOf('popup') !== -1) {
                                                    if (!el.contains(document.querySelector('video'))) {
                                                        el.style.display = 'none';
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    hideAds();
                                    setInterval(hideAds, 1000);
                                })();
                            """.trimIndent()
                            
                            view?.evaluateJavascript(jsCode) {
                                // Implementación del Handler para retrasar la visibilidad 500ms
                                Handler(Looper.getMainLooper()).postDelayed({
                                    view.visibility = View.VISIBLE
                                    isWebViewLoading = false
                                }, 500)
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            return false
                        }
                    }
                }
            },
            update = { webView ->
                if (webView.url != url) {
                    webView.loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isWebViewLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                ParticleLoading(size = 150.dp)
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) { 
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = Color.White) 
        }

        if (onNext != null) {
            IconButton(
                onClick = onNext,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) { 
                Icon(Icons.Rounded.SkipNext, "Siguiente", tint = Color.White) 
            }
        }
    }
}
