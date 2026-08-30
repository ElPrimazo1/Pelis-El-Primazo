package com.example.peliselprimazo.ui.screens.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.util.Log
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.peliselprimazo.domain.model.Movie
import com.example.peliselprimazo.domain.model.ServerLink
import com.example.peliselprimazo.ui.components.ParticleLoading
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(UnstableApi::class)
@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
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
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = true
        }
        
        onDispose {
            activity?.let { act ->
                val window = act.window
                val controller = WindowCompat.getInsetsController(window, view)
                controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                    if (state.isEmbed) {
                        WebPlayer(url = state.videoUrl)
                    } else {
                        VideoPlayerWithControls(
                            state = state,
                            onBack = onBack,
                            onProgressUpdate = { pos, dur -> viewModel.updateProgress(pos, dur) },
                            onNextEpisode = { next ->
                                viewModel.loadVideo(next.serverName, next.fileId, state.movie.id)
                            },
                            onReportError = { 
                                viewModel.reportError()
                                Toast.makeText(context, "Error reportado", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    
                    AnimatedVisibility(
                        visible = state.isChangingEpisode,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black).zIndex(100f),
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

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerWithControls(
    state: PlayerUiState.Success,
    onBack: () -> Unit,
    onProgressUpdate: (Long, Long) -> Unit,
    onNextEpisode: (ServerLink) -> Unit,
    onReportError: () -> Unit
) {
    val context = LocalContext.current
    val tag = "ADS_LOG"
    val adUrl = state.adUrl

    // 1. Cargador de anuncios con logs detallados
    val adsLoader = remember(adUrl) {
        if (!adUrl.isNullOrBlank()) {
            Log.d(tag, "Configurando AdsLoader para: $adUrl")
            val sdkFactory = ImaSdkFactory.getInstance()
            val imaSdkSettings = sdkFactory.createImaSdkSettings().apply {
                maxRedirects = 8
                language = "es"
                isDebugMode = true
            }
            ImaAdsLoader.Builder(context)
                .setImaSdkSettings(imaSdkSettings)
                .setAdEventListener { event ->
                    Log.i(tag, "IMA EVENTO: ${event.type}")
                }
                .setAdErrorListener { error ->
                    Log.e(tag, "IMA ERROR CRÍTICO: ${error.error.message}")
                }
                .build()
        } else {
            Log.w(tag, "No hay URL de anuncio disponible.")
            null
        }
    }

    val playerView = remember {
        PlayerView(context).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }

    var isAdActive by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasStartedPlaying by remember(state.videoUrl) { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }

    val exoPlayer = remember(state.videoUrl, adsLoader) {
        val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
            setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
        
        adsLoader?.let { loader -> 
            Log.d(tag, "Vinculando AdsLoader al MediaSource")
            mediaSourceFactory.setLocalAdInsertionComponents({ loader }, playerView) 
        }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                adsLoader?.setPlayer(this)
            }
    }

    LaunchedEffect(state.videoUrl, adUrl) {
        val mediaItemBuilder = MediaItem.Builder().setUri(state.videoUrl.toUri())
        if (state.videoUrl.contains(".m3u8") || state.videoUrl.contains("hls")) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        if (!adUrl.isNullOrBlank()) {
            Log.d(tag, ">>> SOLICITANDO EJECUCIÓN DE ANUNCIO: $adUrl")
            mediaItemBuilder.setAdsConfiguration(
                MediaItem.AdsConfiguration.Builder(adUrl.toUri()).build()
            )
        }
        exoPlayer.setMediaItem(mediaItemBuilder.build())
        if (state.resumePosition > 0) exoPlayer.seekTo(state.resumePosition)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { 
                isPlaying = playing
                isAdActive = exoPlayer.isPlayingAd
                Log.d(tag, "Estado: isPlaying=$playing, isAdActive=$isAdActive")
                if (playing && !isAdActive) hasStartedPlaying = true
            }
            override fun onPlaybackStateChanged(s: Int) { 
                isBuffering = s == Player.STATE_BUFFERING
                isAdActive = exoPlayer.isPlayingAd || exoPlayer.currentAdGroupIndex != C.INDEX_UNSET
                if (s == Player.STATE_READY) {
                    duration = exoPlayer.duration
                    if (!isAdActive) hasStartedPlaying = true
                }
            }
            override fun onPositionDiscontinuity(oldPos: Player.PositionInfo, newPos: Player.PositionInfo, reason: Int) {
                isAdActive = exoPlayer.isPlayingAd || exoPlayer.currentAdGroupIndex != C.INDEX_UNSET
                if (oldPos.adGroupIndex != C.INDEX_UNSET && newPos.adGroupIndex == C.INDEX_UNSET) {
                    Log.i(tag, "Anuncio finalizado. Iniciando contenido principal.")
                    hasStartedPlaying = true
                    exoPlayer.play()
                }
            }
            override fun onPlayerError(error: PlaybackException) { 
                Log.e(tag, "Error ExoPlayer: ${error.message}")
            }
            override fun onTracksChanged(newTracks: Tracks) { tracks = newTracks }
        }
        exoPlayer.addListener(listener)
        onDispose { 
            if (!exoPlayer.isPlayingAd) onProgressUpdate(exoPlayer.currentPosition, exoPlayer.duration)
            exoPlayer.removeListener(listener)
            adsLoader?.setPlayer(null)
            exoPlayer.release()
            adsLoader?.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            isAdActive = exoPlayer.isPlayingAd || exoPlayer.currentAdGroupIndex != C.INDEX_UNSET
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration
            delay(500)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { playerView }, 
            update = { pv -> if (pv.player != exoPlayer) pv.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )
        
        // VISIBILIDAD: El loading y controles se ocultan totalmente si el anuncio está activo para dejar ver la publicidad
        if (!isAdActive) {
            if (isBuffering || !hasStartedPlaying) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ParticleLoading(size = 150.dp)
                }
            }

            PlayerControls(
                movie = state.movie,
                currentLink = state.currentLink,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onBack = onBack,
                onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                onSeek = { exoPlayer.seekTo(it) },
                onNextEpisode = state.nextLink?.let { { onNextEpisode(it) } },
                onReportError = onReportError,
                tracks = tracks,
                onAudioTrackSelected = { trackGroup, trackIndex ->
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .addOverride(TrackSelectionOverride(trackGroup, trackIndex))
                        .build()
                },
                onSubtitleTrackSelected = { trackGroup, trackIndex ->
                    if (trackIndex == -1) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .build()
                    } else {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .addOverride(TrackSelectionOverride(trackGroup, trackIndex))
                            .build()
                    }
                },
                onQualitySelected = { trackGroup, trackIndex ->
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .addOverride(TrackSelectionOverride(trackGroup, trackIndex))
                        .build()
                },
                exoPlayer = exoPlayer
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPlayer(url: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerControls(
    movie: Movie,
    currentLink: ServerLink?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNextEpisode: (() -> Unit)?,
    onReportError: () -> Unit,
    tracks: Tracks,
    onAudioTrackSelected: (TrackGroup, Int) -> Unit,
    onSubtitleTrackSelected: (TrackGroup, Int) -> Unit,
    onQualitySelected: (TrackGroup, Int) -> Unit,
    exoPlayer: ExoPlayer
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    var isVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    
    var brightness by remember { mutableStateOf(0.7f) }
    var volume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) }

    LaunchedEffect(isVisible) {
        if (isVisible && !isLocked) {
            delay(5000)
            isVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { isVisible = !isVisible },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            if (offset.x < size.width / 2) onSeek((currentPosition - 10000).coerceAtLeast(0))
                            else onSeek((currentPosition + 10000).coerceAtMost(duration))
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    if (!isLocked) {
                        if (change.position.x < size.width / 2) {
                            brightness = (brightness - dragAmount.y / size.height).coerceIn(0f, 1f)
                            val act = context as? Activity
                            val lp = act?.window?.attributes
                            lp?.screenBrightness = brightness
                            act?.window?.attributes = lp
                        } else {
                            volume = (volume - dragAmount.y / size.height).coerceIn(0f, 1f)
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                (volume * audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(),
                                0
                            )
                        }
                    }
                }
            }
    ) {
        AnimatedVisibility(visible = isVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
                
                IconButton(
                    onClick = { isLocked = !isLocked },
                    modifier = Modifier.align(Alignment.CenterStart).padding(24.dp)
                ) {
                    Icon(
                        if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        null, tint = Color.White, modifier = Modifier.size(32.dp)
                    )
                }

                if (!isLocked) {
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(movie.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            if (currentLink?.episode != null) {
                                Text("Temporada ${currentLink.season} • Episodio ${currentLink.episode}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, null, tint = Color.White) }
                        IconButton(onClick = onReportError) { Icon(Icons.Rounded.BugReport, null, tint = Color.White) }
                    }

                    Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onSeek((currentPosition - 10000).coerceAtLeast(0)) }) {
                            Icon(Icons.Rounded.Replay10, null, tint = Color.White, modifier = Modifier.size(56.dp))
                        }
                        Spacer(modifier = Modifier.width(48.dp))
                        IconButton(onClick = onPlayPause) {
                            Icon(
                                if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                                null, tint = Color.White, modifier = Modifier.size(92.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(48.dp))
                        IconButton(onClick = { onSeek((currentPosition + 10000).coerceAtMost(duration)) }) {
                            Icon(Icons.Rounded.Forward10, null, tint = Color.White, modifier = Modifier.size(56.dp))
                        }
                    }

                    Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTime(currentPosition), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                                onValueChange = { onSeek((it * duration).toLong()) },
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red)
                            )
                            Text(formatTime(duration), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    val currentSpeed = exoPlayer.playbackParameters.speed
                                    val nextSpeed = when {
                                        currentSpeed < 1f -> 1.0f
                                        currentSpeed < 1.5f -> 1.5f
                                        currentSpeed < 2f -> 2.0f
                                        else -> 0.5f
                                    }
                                    exoPlayer.setPlaybackSpeed(nextSpeed)
                                }) {
                                    Icon(Icons.Rounded.Speed, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${exoPlayer.playbackParameters.speed}x", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (onNextEpisode != null) {
                                Button(
                                    onClick = onNextEpisode,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Rounded.SkipNext, null, tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Siguiente", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (showSettings) {
            TrackSelectionDialog(
                tracks = tracks,
                onDismiss = { showSettings = false },
                onAudioSelected = onAudioTrackSelected,
                onSubtitleSelected = onSubtitleTrackSelected,
                onQualitySelected = onQualitySelected
            )
        }
    }
}

@Composable
fun TrackSelectionDialog(
    tracks: Tracks,
    onDismiss: () -> Unit,
    onAudioSelected: (TrackGroup, Int) -> Unit,
    onSubtitleSelected: (TrackGroup, Int) -> Unit,
    onQualitySelected: (TrackGroup, Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("Ajustes", color = Color.White, fontWeight = FontWeight.Black) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Calidad", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEach { group ->
                    for (i in 0 until group.length) {
                        val isSelected = group.isTrackSelected(i)
                        val format = group.getTrackFormat(i)
                        ListItem(
                            headlineContent = { Text("${format.height}p", color = Color.White) },
                            modifier = Modifier.clickable { onQualitySelected(group.mediaTrackGroup, i); onDismiss() },
                            trailingContent = { if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.Red) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Audio", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { group ->
                    for (i in 0 until group.length) {
                        val isSelected = group.isTrackSelected(i)
                        val format = group.getTrackFormat(i)
                        ListItem(
                            headlineContent = { Text(format.language?.uppercase() ?: "Audio ${i+1}", color = Color.White) },
                            modifier = Modifier.clickable { onAudioSelected(group.mediaTrackGroup, i); onDismiss() },
                            trailingContent = { if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.Red) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Subtítulos", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                ListItem(
                    headlineContent = { Text("Desactivados", color = Color.White) },
                    modifier = Modifier.clickable { onSubtitleSelected(TrackGroup(Format.Builder().build()), -1); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
                    for (i in 0 until group.length) {
                        val isSelected = group.isTrackSelected(i)
                        val format = group.getTrackFormat(i)
                        ListItem(
                            headlineContent = { Text(format.language?.uppercase() ?: "Idioma ${i+1}", color = Color.White) },
                            modifier = Modifier.clickable { onSubtitleSelected(group.mediaTrackGroup, i); onDismiss() },
                            trailingContent = { if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.Red) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = Color.Red) } }
    )
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, mins, secs)
    else String.format("%02d:%02d", mins, secs)
}
