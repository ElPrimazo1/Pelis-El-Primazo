package com.example.peliselprimazo.ui.screens.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.peliselprimazo.data.AdsManager
import com.example.peliselprimazo.domain.model.ContentType
import com.example.peliselprimazo.domain.model.Subtitle
import com.example.peliselprimazo.ui.components.ParticleLoading
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

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
                        "Preparando el cine...",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            is PlayerUiState.Success -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Forzar recreación completa del reproductor al cambiar la URL
                    key(state.videoUrl) {
                        NativePlayer(
                            videoUrl = state.videoUrl,
                            headers = state.headers,
                            resumePosition = state.resumePosition,
                            movieTitle = state.movie.title,
                            season = state.currentLink?.season,
                            episode = state.currentLink?.episode,
                            contentType = state.movie.contentType,
                            subtitles = state.subtitles,
                            isChangingEpisode = state.isChangingEpisode,
                            onBack = onBack,
                            onNextEpisode = state.nextLink?.let { next ->
                                {
                                    activity?.let { act ->
                                        adsManager.showRewardedVideo(act) {
                                            viewModel.loadVideo(next.serverName, next.fileId, state.movie.id)
                                        }
                                    } ?: viewModel.loadVideo(next.serverName, next.fileId, state.movie.id)
                                }
                            },
                            onPrevEpisode = state.previousLink?.let { prev ->
                                {
                                    activity?.let { act ->
                                        adsManager.showRewardedVideo(act) {
                                            viewModel.loadVideo(prev.serverName, prev.fileId, state.movie.id)
                                        }
                                    } ?: viewModel.loadVideo(prev.serverName, prev.fileId, state.movie.id)
                                }
                            },
                            onProgressUpdate = { pos, dur -> viewModel.updateProgress(pos, dur) }
                        )
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
                PlayerError(message = state.message, canRetry = state.canRetry, onRetry = { viewModel.retry() }, onBack = onBack)
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
    movieTitle: String,
    season: Int?,
    episode: Int?,
    contentType: ContentType,
    subtitles: List<Subtitle>,
    isChangingEpisode: Boolean,
    onBack: () -> Unit,
    onNextEpisode: (() -> Unit)?,
    onPrevEpisode: (() -> Unit)?,
    onProgressUpdate: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    val exoPlayer = remember(videoUrl, subtitles) {
        val browserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(browserUserAgent)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)

        val subtitleConfigs = subtitles.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(if (sub.url.endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                .setLanguage(sub.language)
                .setLabel(sub.label)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }

        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                setMediaItem(mediaItem)
                prepare()
                if (resumePosition > 0) seekTo(resumePosition)
                playWhenReady = true
            }
    }

    // Detener audio inmediatamente si se inicia un cambio (para anuncios)
    LaunchedEffect(isChangingEpisode) {
        if (isChangingEpisode) {
            exoPlayer.pause()
            exoPlayer.stop()
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    
    // Gestures states
    var brightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness ?: 0.5f) }
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration
                }
            }
        }
        exoPlayer.addListener(listener)
        while (isActive) {
            currentPosition = exoPlayer.currentPosition
            if (exoPlayer.isPlaying) {
                onProgressUpdate(currentPosition, exoPlayer.duration)
            }
            delay(1000)
        }
    }

    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            delay(5000)
            isControlsVisible = false
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { 
            exoPlayer.stop()
            exoPlayer.release() 
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        showBrightnessIndicator = false
                        showVolumeIndicator = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val isLeftSide = change.position.x < size.width / 2
                        if (isLeftSide) {
                            showBrightnessIndicator = true
                            showVolumeIndicator = false
                            val delta = -dragAmount.y / size.height
                            brightness = (brightness + delta).coerceIn(0f, 1f)
                            activity?.let { act ->
                                val lp = act.window.attributes
                                lp.screenBrightness = if (brightness <= 0.01f) 0.01f else brightness
                                act.window.attributes = lp
                            }
                        } else {
                            showVolumeIndicator = true
                            showBrightnessIndicator = false
                            val delta = -dragAmount.y / size.height
                            val volumeDelta = (delta * maxVolume * 2).roundToInt()
                            if (volumeDelta != 0) {
                                volume = (volume + volumeDelta).coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                            }
                        }
                    }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { isControlsVisible = !isControlsVisible }
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        // Visual Indicators for Gestures
        GestureIndicator(
            visible = showBrightnessIndicator,
            value = brightness,
            icon = Icons.Rounded.LightMode,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 64.dp)
        )
        GestureIndicator(
            visible = showVolumeIndicator,
            value = volume.toFloat() / maxVolume.coerceAtLeast(1),
            icon = if (volume == 0) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 64.dp)
        )

        // Custom Controls
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 }
        ) {
            PlayerControls(
                movieTitle = movieTitle,
                season = season,
                episode = episode,
                contentType = contentType,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                playbackSpeed = playbackSpeed,
                onBack = onBack,
                onTogglePlay = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                onSeek = { exoPlayer.seekTo(it) },
                onForward = { exoPlayer.seekTo(exoPlayer.currentPosition + 10000) },
                onRewind = { exoPlayer.seekTo(exoPlayer.currentPosition - 10000) },
                onNextEpisode = onNextEpisode?.let { { 
                    exoPlayer.pause()
                    exoPlayer.stop()
                    it() 
                } },
                onPrevEpisode = onPrevEpisode?.let { { 
                    exoPlayer.pause()
                    exoPlayer.stop()
                    it() 
                } },
                onChangeSpeed = {
                    playbackSpeed = if (it >= 2.0f) 0.5f else it + 0.25f
                    exoPlayer.setPlaybackSpeed(playbackSpeed)
                },
                exoPlayer = exoPlayer
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerControls(
    movieTitle: String,
    season: Int?,
    episode: Int?,
    contentType: ContentType,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onForward: () -> Unit,
    onRewind: () -> Unit,
    onNextEpisode: (() -> Unit)?,
    onPrevEpisode: (() -> Unit)?,
    onChangeSpeed: (Float) -> Unit,
    exoPlayer: ExoPlayer
) {
    var showTrackSelector by remember { mutableStateOf(false) }
    var selectorType by remember { mutableStateOf("audio") }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = movieTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (contentType != ContentType.MOVIE && season != null && episode != null) {
                    Text(
                        text = "Temporada $season • Episodio $episode",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Center Controls
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            IconButton(onClick = onRewind, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Rounded.Replay10, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(80.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
            IconButton(onClick = onForward, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Rounded.Forward10, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Time bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(currentPosition), color = Color.White, fontSize = 12.sp)
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..duration.coerceAtLeast(0L).toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Text(formatTime(duration), color = Color.White, fontSize = 12.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onPrevEpisode != null) {
                        Button(
                            onClick = onPrevEpisode,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Anterior", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    if (onNextEpisode != null) {
                        Button(
                            onClick = onNextEpisode,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Siguiente", color = Color.White, fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onChangeSpeed(playbackSpeed) }) {
                        Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                    IconButton(onClick = { 
                        selectorType = "subtitles"
                        showTrackSelector = true
                    }) {
                        Icon(Icons.Rounded.Subtitles, null, tint = Color.White)
                    }
                    IconButton(onClick = { 
                        selectorType = "audio"
                        showTrackSelector = true
                    }) {
                        Icon(Icons.Rounded.Language, null, tint = Color.White)
                    }
                }
            }
        }
    }

    if (showTrackSelector) {
        TrackSelectorDialog(
            type = selectorType,
            exoPlayer = exoPlayer,
            onDismiss = { showTrackSelector = false }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TrackSelectorDialog(
    type: String,
    exoPlayer: ExoPlayer,
    onDismiss: () -> Unit
) {
    val trackGroups = exoPlayer.currentTracks
    val trackType = if (type == "audio") C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
    
    val availableTracks = remember {
        val tracks = mutableListOf<Triple<String, Int, Int>>()
        trackGroups.groups.forEachIndexed { groupIdx, group ->
            if (group.type == trackType) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val label = format.label ?: format.language ?: "Pista ${tracks.size + 1}"
                    tracks.add(Triple(label, groupIdx, i))
                }
            }
        }
        tracks
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(320.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (type == "audio") "Idioma de Audio" else "Subtítulos",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                if (availableTracks.isEmpty()) {
                    Text("No hay opciones disponibles", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                }

                availableTracks.forEach { (label, groupIdx, trackIdx) ->
                    val isSelected = trackGroups.groups[groupIdx].isTrackSelected(trackIdx)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable {
                                val trackGroup = trackGroups.groups[groupIdx].mediaTrackGroup
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(
                                        TrackSelectionOverride(trackGroup, trackIdx)
                                    )
                                    .apply { if (type == "subtitles") setDisabledTrackTypes(emptySet()) }
                                    .build()
                                onDismiss()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (type == "audio") Icons.Rounded.Language else Icons.Rounded.Subtitles,
                            null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = label, 
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                
                if (type == "subtitles") {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setDisabledTrackTypes(setOf(C.TRACK_TYPE_TEXT))
                                .build()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Desactivar Subtítulos", color = Color.Red)
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cerrar")
                }
            }
        }
    }
}

@Composable
fun GestureIndicator(
    visible: Boolean,
    value: Float,
    icon: ImageVector,
    modifier: Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(120.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(value.coerceIn(0f, 1f))
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
fun PlayerError(message: String, canRetry: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
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
                text = message,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canRetry) {
                    Button(
                        onClick = onRetry,
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

fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
