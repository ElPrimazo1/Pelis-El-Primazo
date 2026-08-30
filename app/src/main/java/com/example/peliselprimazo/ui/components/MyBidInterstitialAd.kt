package com.example.peliselprimazo.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MyBidInterstitialAd(
    adUrl: String,
    onClose: () -> Unit
) {
    val tag = "MyBidInterstitialAd"
    val totalTime = 10 // Reducido a 10s para pruebas
    var secondsRemaining by remember { mutableIntStateOf(totalTime) } 
    var isLoading by remember { mutableStateOf(true) }
    var rewardGranted by remember { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = 1f - (secondsRemaining.toFloat() / totalTime.toFloat()),
        label = "AdProgress"
    )

    LaunchedEffect(Unit) {
        Log.d(tag, "Iniciando interstitial con URL: $adUrl")
        while (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining--
        }
        rewardGranted = true
    }

    Dialog(
        onDismissRequest = { if (rewardGranted) onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = rewardGranted,
            dismissOnClickOutside = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                        }
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(v: WebView?, u: String?, f: Bitmap?) {
                                Log.d(tag, "WebView cargando: $u")
                                isLoading = true 
                            }
                            override fun onPageFinished(v: WebView?, u: String?) { 
                                Log.d(tag, "WebView finalizó carga: $u")
                                isLoading = false 
                            }
                            override fun onReceivedError(v: WebView?, r: WebResourceRequest?, e: WebResourceError?) {
                                Log.e(tag, "Error en WebView: ${e?.description}")
                            }
                        }
                        loadUrl(adUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Botón de cierre de emergencia (visible si hay error o pasa el tiempo)
            if (rewardGranted || !isLoading) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }

            // Header de Progreso
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(top = 40.dp, bottom = 12.dp, start = 16.dp, end = 80.dp) // Espacio para el botón Close
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (rewardGranted) Color.Green else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (rewardGranted) "¡CONTENIDO DESBLOQUEADO!" else "DESBLOQUEANDO EN ${secondsRemaining}s",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = if (rewardGranted) Color.Green else MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
