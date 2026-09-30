package com.example.peliselprimazo.ui.screens.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.peliselprimazo.data.AdsManager
import com.example.peliselprimazo.ui.components.ParticleLoading
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ParticleLoading(size = 180.dp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Extrayendo enlace directo...",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            is PlayerUiState.Success -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    NativePlayer(
                        videoUrl = state.videoUrl,
                        headers = state.headers,
                        resumePosition = state.resumePosition,
                        onProgressUpdate = { pos, dur -> viewModel.updateProgress(pos, dur) }
                    )
                    
                    // Capa de Controles Superpuestos (Botones de navegación)
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) { 
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = Color.White) 
                        }

                        if (state.nextLink != null) {
                            IconButton(
                                onClick = {
                                    activity?.let { act ->
                                        adsManager.showRewardedVideo(act) {
                                            viewModel.loadVideo(state.nextLink.serverName, state.nextLink.fileId, state.movie.id)
                                        }
                                    } ?: viewModel.loadVideo(state.nextLink.serverName, state.nextLink.fileId, state.movie.id)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(44.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) { 
                                Icon(Icons.Rounded.SkipNext, "Siguiente", tint = Color.White) 
                            }
                        }
                    }
                    
                    if (state.isChangingEpisode) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            ParticleLoading(size = 150.dp)
                        }
                    }
                }
            }
            is PlayerUiState.Error -> {
                Surface(
                    modifier = Modifier.padding(24.dp),
                    color = Color(0xFF1A1A1A),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.Error, null, tint = Color.Red, modifier = Modifier.size(60.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "¡Ops! Algo salió mal",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.canRetry) {
                                Button(
                                    onClick = { viewModel.retry() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Refresh, null, tint = Color.Black)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Reintentar", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            OutlinedButton(
                                onClick = onBack,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cerrar", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun NativePlayer(
    videoUrl: String,
    headers: Map<String, String>,
    resumePosition: Long,
    onProgressUpdate: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    
    // Configuración robusta de ExoPlayer con Headers y User-Agent real
    val exoPlayer = remember(videoUrl) {
        val browserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(browserUserAgent)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(videoUrl)
                    .build()
                
                setMediaItem(mediaItem)
                prepare()
                if (resumePosition > 0) {
                    seekTo(resumePosition)
                }
                playWhenReady = true
            }
    }

    // Actualización de progreso
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (exoPlayer.isPlaying) {
                onProgressUpdate(exoPlayer.currentPosition, exoPlayer.duration)
            }
            delay(8000)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = true
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
