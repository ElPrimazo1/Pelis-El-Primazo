package com.example.peliselprimazo.ui.screens.home

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.peliselprimazo.domain.model.Movie
import com.example.peliselprimazo.domain.model.User
import com.example.peliselprimazo.ui.components.AuthDialog
import com.example.peliselprimazo.ui.components.ParticleLoading
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMovieClick: (Int) -> Unit,
    onDirectPlayClick: (Movie) -> Unit
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingAuth by viewModel.isLoadingAuth.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isPreloading by viewModel.isPreloading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val user by viewModel.user.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    
    val homeSections by viewModel.homeSections.collectAsState()
    val movieSections by viewModel.movieSections.collectAsState()
    val seriesSections by viewModel.seriesSections.collectAsState()
    val animeSections by viewModel.animeSections.collectAsState()
    
    val moviesListFiltered by viewModel.moviesListFiltered.collectAsState()
    val seriesListFiltered by viewModel.seriesListFiltered.collectAsState()
    val animeListFiltered by viewModel.animeListFiltered.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val watchingContent by viewModel.watchingContent.collectAsState()
    val savedContent by viewModel.savedContent.collectAsState()
    val likedContent by viewModel.likedContent.collectAsState()
    val watchLaterContent by viewModel.watchLaterContent.collectAsState()
    val finishedContent by viewModel.finishedContent.collectAsState()
    val totalHours by viewModel.totalHours.collectAsState()
    
    val updateConfig by viewModel.updateConfig.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var showAuthDialog by remember { mutableStateOf(false) }
    var pendingMovieToPlay by remember { mutableStateOf<Movie?>(null) }
    var showUpdateDialog by rememberSaveable { mutableStateOf(true) }

    val appTitle = updateConfig?.visualFlags?.get("ui_app_title") ?: "CFilm"
    val loadingText = updateConfig?.visualFlags?.get("ui_loading_text") ?: "Preparando el cine..."
    val cambiosStatus = updateConfig?.visualFlags?.get("Cambios") ?: ""

    val categories = remember { listOf("Inicio", "Películas", "Series", "Anime", "Perfil") }
    val pagerState = rememberPagerState(initialPage = 0) { categories.size }

    val handlePlayRequest = remember(user?.isLoggedIn) {
        { movie: Movie ->
            if (user?.isLoggedIn == true) {
                onDirectPlayClick(movie)
            } else {
                pendingMovieToPlay = movie
                showAuthDialog = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshData() },
            modifier = Modifier.fillMaxSize(),
            state = rememberPullToRefreshState()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isSearchActive) {
                    SearchContent(
                        query = searchQuery,
                        history = searchHistory,
                        results = searchResults,
                        onQueryChange = { viewModel.onSearchQueryChange(it) },
                        onMovieClick = onMovieClick,
                        onClearHistory = { viewModel.clearSearchHistory() }
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1
                    ) { page ->
                        when (page) {
                            0 -> HomeSectionsContent(homeSections, onMovieClick, handlePlayRequest)
                            1 -> CategoryTabContent(movieSections, moviesListFiltered, selectedGenre, onMovieClick)
                            2 -> CategoryTabContent(seriesSections, seriesListFiltered, selectedGenre, onMovieClick)
                            3 -> CategoryTabContent(animeSections, animeListFiltered, selectedGenre, onMovieClick)
                            4 -> ProfileScreenContent(
                                user = user,
                                watching = watchingContent,
                                saved = savedContent,
                                liked = likedContent,
                                watchLater = watchLaterContent,
                                finished = finishedContent,
                                totalHours = totalHours,
                                viewModel = viewModel,
                                onMovieClick = onMovieClick,
                                cambiosStatus = cambiosStatus 
                            )
                        }
                    }
                }
            }
        }

        if (!isSearchActive) {
            HomeTopBar(appTitle, isLoading || isRefreshing, onSearchClick = { viewModel.onSearchActiveChange(true) })
        } else {
            SearchTopBar(searchQuery, onQueryChange = { viewModel.onSearchQueryChange(it) }, onClose = { viewModel.onSearchActiveChange(false) })
        }

        AnimatedVisibility(visible = isPreloading, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).zIndex(100f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ParticleLoading(size = 150.dp)
                    Text(loadingText, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!isSearchActive) {
            BottomNavBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                categories = categories,
                currentPage = pagerState.currentPage,
                onPageSelected = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } }
            )
        }

        if (showAuthDialog) {
            AuthDialog(
                isLoading = isLoadingAuth,
                error = error,
                onLogin = { e, p -> 
                    viewModel.login(e, p) { 
                        showAuthDialog = false
                        pendingMovieToPlay?.let { onDirectPlayClick(it) }
                        pendingMovieToPlay = null 
                    } 
                },
                onRegister = { u, e, p -> 
                    viewModel.register(u, e, p) { 
                        showAuthDialog = false
                        pendingMovieToPlay?.let { onDirectPlayClick(it) }
                        pendingMovieToPlay = null 
                    } 
                },
                onDismiss = { showAuthDialog = false; pendingMovieToPlay = null }
            )
        }
        
        if (updateConfig?.isUpdateAvailable == true && showUpdateDialog) {
            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = { Text("¡Actualización!") },
                text = { Text("Nueva versión v${updateConfig?.latestVersionName} disponible.") },
                confirmButton = { Button(onClick = { updateConfig?.updateUrl?.let { viewModel.startUpdate(it) }; showUpdateDialog = false }) { Text("Actualizar") } },
                dismissButton = { if (updateConfig?.isForceUpdate == false) TextButton(onClick = { showUpdateDialog = false }) { Text("Ahora no") } }
            )
        }

        if (isDownloading) {
            Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
                Surface(color = Color(0xFF1A1A1A), shape = RoundedCornerShape(24.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(progress = { downloadProgress })
                        Text("Descargando actualización...", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HomeTopBar(title: String, isWorking: Boolean, onSearchClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp).zIndex(10f)) {
        Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)), color = Color.Black.copy(alpha = 0.8f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isWorking) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    IconButton(onClick = onSearchClick) { Icon(Icons.Default.Search, null, tint = Color.White) }
                }
            }
        }
    }
}

@Composable
fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp).zIndex(10f)) {
        Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)), color = Color(0xFF1A1A1A)) {
            TextField(
                value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar...", color = Color.Gray) },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                trailingIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color.White) } },
                singleLine = true
            )
        }
    }
}

@Composable
fun BottomNavBar(modifier: Modifier = Modifier, categories: List<String>, currentPage: Int, onPageSelected: (Int) -> Unit) {
    Box(modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp).zIndex(10f)) {
        Surface(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)), color = Color(0xFF121212).copy(alpha = 0.95f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {
            Row(modifier = Modifier.padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                categories.forEachIndexed { index, label ->
                    val selected = currentPage == index
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(25.dp)).clickable { onPageSelected(index) },
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (selected) Color.Black else Color.White
                    ) {
                        Text(
                            text = label, 
                            modifier = Modifier.padding(vertical = 10.dp), 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold, 
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchContent(query: String, history: List<String>, results: List<Movie>, onQueryChange: (String) -> Unit, onMovieClick: (Int) -> Unit, onClearHistory: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 85.dp)) {
        if (query.isBlank()) {
            if (history.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Recientes", color = Color.White, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClearHistory) { Text("Limpiar") }
                }
                LazyColumn { items(history) { Text(it, color = Color.Gray, modifier = Modifier.fillMaxWidth().clickable { onQueryChange(it) }.padding(16.dp)) } }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(results, key = { it.id }) { MovieCardItem(it, onMovieClick) }
            }
        }
    }
}

@Composable
fun HomeSectionsContent(sections: List<HomeSection>, onMovieClick: (Int) -> Unit, onPlay: (Movie) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 110.dp, bottom = 100.dp)) {
        items(sections, key = { it.title }) { section ->
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(text = section.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(section.items, key = { it.id }) { movie -> 
                        if (section.isWatchingSection) WatchingCardItem(movie) { onPlay(movie) } 
                        else MovieCardItem(movie, onMovieClick, width = 130.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryTabContent(sections: List<HomeSection>, filtered: List<Movie>, genre: String?, onMovieClick: (Int) -> Unit) {
    if (genre != null) {
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 110.dp, start = 8.dp, end = 8.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { movie -> MovieCardItem(movie, onMovieClick) }
        }
    } else {
        HomeSectionsContent(sections, onMovieClick, {})
    }
}

@Composable
fun ProfileScreenContent(
    user: User?, 
    watching: List<Movie>, 
    saved: List<Movie>, 
    liked: List<Movie>, 
    watchLater: List<Movie>, 
    finished: List<Movie>, 
    totalHours: Long, 
    viewModel: HomeViewModel, 
    onMovieClick: (Int) -> Unit, 
    cambiosStatus: String
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    
    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }

    if (selectedCategory == null) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 110.dp, bottom = 110.dp, start = 16.dp, end = 16.dp)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(modifier = Modifier.size(90.dp).clip(CircleShape), color = Color.DarkGray) {
                        if (user?.profilePictureUri != null) AsyncImage(model = user.profilePictureUri, contentDescription = null, contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.Person, null, modifier = Modifier.padding(20.dp), tint = Color.White)
                    }
                    Text(user?.username ?: "Usuario", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    if (cambiosStatus.isNotEmpty()) {
                        Text(cambiosStatus, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileGridItem("Favoritos", liked.size, Icons.Rounded.Favorite, Color.Red) { selectedCategory = "Favoritos" }
                    ProfileGridItem("Viendo", watching.size, Icons.Rounded.PlayCircle, Color.Blue) { selectedCategory = "Viendo" }
                    ProfileGridItem("Terminados", finished.size, Icons.Rounded.CheckCircle, Color.Green) { selectedCategory = "Terminados" }
                    ProfileGridItem("Ver Después", watchLater.size, Icons.Rounded.WatchLater, Color.Yellow) { selectedCategory = "Ver Después" }
                    ProfileGridItem("Guardados", saved.size, Icons.Rounded.Bookmark, Color.Magenta) { selectedCategory = "Guardados" }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1A1A1A), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val totalCount = liked.size + watching.size + finished.size + watchLater.size + saved.size
                            Text(text = totalCount.toString(), color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text(text = "Contenido", color = Color.Gray, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "~$totalHours", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text(text = "Horas", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                ProfileSettingItem("Limpiar Caché", Icons.Default.Delete) { viewModel.clearCache() }
                if (user?.isLoggedIn == true) ProfileSettingItem("Cerrar Sesión", Icons.AutoMirrored.Filled.ExitToApp, Color.Red, Color.Red) { viewModel.logout() }
            }
        }
    } else {
        val list = when(selectedCategory) { 
            "Favoritos" -> liked
            "Viendo" -> watching
            "Terminados" -> finished
            "Ver Después" -> watchLater
            "Guardados" -> saved
            else -> emptyList() 
        }
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            // Se usa statusBarsPadding + top padding para asegurar que el título y el botón
            // aparezcan debajo de la HomeTopBar (CFilm/Buscador) sin solaparse.
            Row(
                Modifier
                    .statusBarsPadding()
                    .padding(top = 75.dp, start = 8.dp, end = 16.dp, bottom = 8.dp), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedCategory = null }) { 
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) 
                }
                Text(
                    text = selectedCategory!!, 
                    color = Color.White, 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Black
                )
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay nada aquí todavía", color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { MovieCardItem(it, onMovieClick) }
                }
            }
        }
    }
}

@Composable
fun MovieCardItem(movie: Movie, onClick: (Int) -> Unit, width: androidx.compose.ui.unit.Dp? = null) {
    val context = LocalContext.current
    val movieRequest = remember(movie.posterUrl) {
        ImageRequest.Builder(context).data(movie.posterUrl).crossfade(true).diskCachePolicy(CachePolicy.ENABLED).memoryCachePolicy(CachePolicy.ENABLED).build()
    }
    
    val isNew = remember(movie.createdAt) {
        System.currentTimeMillis() - movie.createdAt < 2 * 24 * 60 * 60 * 1000L
    }

    Column(modifier = (if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).clickable { onClick(movie.id) }) {
        Card(modifier = Modifier.aspectRatio(0.7f), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
            Box {
                AsyncImage(model = movieRequest, contentDescription = movie.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                
                if (isNew) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        color = Color(0xFFFFD700),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "NEW",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                if (movie.rating > 0) {
                    Surface(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp), color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp)) {
                        Text(text = String.format(Locale.US, "%.1f", movie.rating), color = Color(0xFFFFD700), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }
        }
        Text(text = movie.title, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
    }
}

@Composable
fun WatchingCardItem(movie: Movie, onClick: () -> Unit) {
    Column(modifier = Modifier.width(200.dp).clickable { onClick() }) {
        Card(modifier = Modifier.height(110.dp).fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Box {
                AsyncImage(model = movie.backdropUrl ?: movie.posterUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Icon(Icons.Default.PlayCircleOutline, null, modifier = Modifier.size(36.dp).align(Alignment.Center), tint = Color.White)
                if (movie.totalDuration > 0) {
                    val progress = movie.lastPosition.toFloat() / movie.totalDuration.toFloat()
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomStart), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
                }
            }
        }
        Text(text = movie.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun ProfileGridItem(title: String, count: Int, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(80.dp).clickable { onClick() }, 
        color = Color(0xFF1A1A1A), 
        shape = RoundedCornerShape(16.dp), 
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp), 
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = color.copy(alpha = 0.1f)) {
                    Icon(icon, null, tint = color, modifier = Modifier.padding(8.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text(count.toString(), color = Color.Gray, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
    }
}

@Composable
fun ProfileSettingItem(title: String, icon: ImageVector, color: Color = Color.Gray, textColor: Color = Color.White, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title, color = textColor) }, leadingContent = { Icon(icon, null, tint = color) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable { onClick() })
}
