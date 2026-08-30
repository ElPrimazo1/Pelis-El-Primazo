package com.example.peliselprimazo.ui.screens.home

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
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.peliselprimazo.data.remote.MyBidSpot
import com.example.peliselprimazo.domain.model.Movie
import com.example.peliselprimazo.domain.model.User
import com.example.peliselprimazo.ui.components.AuthDialog
import com.example.peliselprimazo.ui.components.MyBidBanner
import com.example.peliselprimazo.ui.components.ParticleLoading
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMovieClick: (Int) -> Unit,
    onDirectPlayClick: (Movie, String) -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
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
    
    val moviesListFiltered by viewModel.moviesListFiltered.collectAsState()
    val seriesListFiltered by viewModel.seriesListFiltered.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val watchingContent by viewModel.watchingContent.collectAsState()
    val savedContent by viewModel.savedContent.collectAsState()
    val likedContent by viewModel.likedContent.collectAsState()
    val watchLaterContent by viewModel.watchLaterContent.collectAsState()
    val finishedContent by viewModel.finishedContent.collectAsState()
    val totalHours by viewModel.totalHours.collectAsState()
    val adsSpots by viewModel.adsSpots.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var showAuthDialog by remember { mutableStateOf(false) }
    var pendingMovieToPlay by remember { mutableStateOf<Movie?>(null) }

    val categories = listOf(
        "all" to "Inicio",
        "movie" to "Películas",
        "series" to "Series",
        "profile" to "Perfil"
    )

    val pagerState = rememberPagerState(initialPage = 0) { categories.size }

    val handlePlayRequest = { movie: Movie ->
        if (user?.isLoggedIn == true) {
            viewModel.prepareAndPlay(movie) { url ->
                onDirectPlayClick(movie, url)
            }
        } else {
            pendingMovieToPlay = movie
            showAuthDialog = true
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
                        userScrollEnabled = true,
                        beyondViewportPageCount = 1
                    ) { page ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (page) {
                                0 -> HomeSectionsContent(homeSections, adsSpots, onMovieClick, handlePlayRequest)
                                1 -> CategoryTabContent(movieSections, adsSpots, moviesListFiltered, selectedGenre, onMovieClick)
                                2 -> CategoryTabContent(seriesSections, adsSpots, seriesListFiltered, selectedGenre, onMovieClick)
                                3 -> ProfileScreenContent(
                                    user = user,
                                    watching = watchingContent,
                                    saved = savedContent,
                                    liked = likedContent,
                                    watchLater = watchLaterContent,
                                    finished = finishedContent,
                                    totalHours = totalHours,
                                    viewModel = viewModel,
                                    onMovieClick = onMovieClick,
                                    onDirectPlayClick = handlePlayRequest
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top bar - Cargador infinito para scroll/refresco
        if (!isSearchActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 0.dp)
                    .zIndex(2f),
                contentAlignment = Alignment.TopCenter
            ) {
                if (isLoading || isRefreshing) {
                    ParticleLoading(
                        modifier = Modifier
                            .size(60.dp)
                            .offset(y = (-15).dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 25.dp, bottomEnd = 25.dp)), 
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PELIS EL PRIMAZO",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        IconButton(onClick = { viewModel.onSearchActiveChange(true) }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color.White)
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 0.dp)
                    .zIndex(2f)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(25.dp)),
                    color = Color.Black.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        placeholder = { Text("¿Qué quieres ver hoy?", color = Color.Gray) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.onSearchActiveChange(false) }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                            }
                        },
                        singleLine = true
                    )
                }
            }
        }

        // Overlay central para pre-extracción de video (Play) - PANTALLA COMPLETA NEGRA
        AnimatedVisibility(
            visible = isPreloading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.zIndex(100f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(enabled = false) {}, // Bloquear clicks
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ParticleLoading(size = 200.dp)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Preparando el cine...",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bottom navigation
        if (!isSearchActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .zIndex(2f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(30.dp)),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        categories.forEachIndexed { index, pair ->
                            CategoryBubbleItem(
                                label = pair.second,
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.scrollToPage(index)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showAuthDialog) {
            AuthDialog(
                isLoading = isLoading,
                error = error,
                onLogin = { e, p ->
                    viewModel.login(e, p) {
                        showAuthDialog = false
                        pendingMovieToPlay?.let { handlePlayRequest(it) }
                        pendingMovieToPlay = null
                    }
                },
                onRegister = { u, e, p ->
                    viewModel.register(u, e, p) {
                        showAuthDialog = false
                        pendingMovieToPlay?.let { handlePlayRequest(it) }
                        pendingMovieToPlay = null
                    }
                },
                onDismiss = { showAuthDialog = false }
            )
        }
    }
}

@Composable
fun SearchContent(
    query: String,
    history: List<String>,
    results: List<Movie>,
    onQueryChange: (String) -> Unit,
    onMovieClick: (Int) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 80.dp)) {
        if (query.isBlank()) {
            if (history.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Búsquedas recientes", color = Color.White, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClearHistory) {
                        Text("Limpiar", color = MaterialTheme.colorScheme.primary)
                    }
                }
                LazyColumn {
                    items(history) { item ->
                        ListItem(
                            headlineContent = { Text(item, color = Color.LightGray) },
                            leadingContent = { Icon(Icons.Rounded.History, contentDescription = null, tint = Color.Gray) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onQueryChange(item) }
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Busca tus películas o series favoritas", color = Color.Gray)
                }
            }
        } else {
            SearchGrid(results, onMovieClick)
        }
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
    onDirectPlayClick: (Movie) -> Unit
) {
    var selectedListCategory by remember { mutableStateOf<String?>(null) }
    
    BackHandler(enabled = selectedListCategory != null) {
        selectedListCategory = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selectedListCategory,
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "ProfileContentTransition"
        ) { category ->
            if (category == null) {
                ProfileMainContent(
                    user = user,
                    watchingCount = watching.size,
                    likedCount = liked.size,
                    finishedCount = finished.size,
                    watchLaterCount = watchLater.size,
                    totalCount = watching.size + liked.size + finished.size + watchLater.size,
                    totalHours = totalHours,
                    onCategoryClick = { selectedListCategory = it },
                    viewModel = viewModel
                )
            } else {
                val listToShow = when(category) {
                    "Favoritos" -> liked
                    "Viendo" -> watching
                    "Terminados" -> finished
                    "Ver Después" -> watchLater
                    "Guardados" -> saved
                    else -> emptyList()
                }
                ProfileListDetail(
                    title = category,
                    items = listToShow,
                    onBack = { selectedListCategory = null },
                    onMovieClick = onMovieClick
                )
            }
        }
    }
}

@Composable
fun ProfileMainContent(
    user: User?,
    watchingCount: Int,
    likedCount: Int,
    finishedCount: Int,
    watchLaterCount: Int,
    totalCount: Int,
    totalHours: Long,
    onCategoryClick: (String) -> Unit,
    viewModel: HomeViewModel
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.updateAvatar(it.toString()) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 80.dp, bottom = 120.dp, start = 16.dp, end = 16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Surface(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .clickable { launcher.launch("image/*") },
                        color = Color.DarkGray
                    ) {
                        if (!user?.profilePictureUri.isNullOrEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(user?.profilePictureUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person, 
                                contentDescription = null, 
                                modifier = Modifier.padding(20.dp), 
                                tint = Color.White
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { launcher.launch("image/*") },
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit, 
                            contentDescription = "Editar", 
                            modifier = Modifier.padding(8.dp).size(16.dp)
                        )
                    }
                }
                Text(
                    text = user?.username ?: "Usuario El Primazo",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 16.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Text(
                text = "Mis Listas",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileGridItem(
                        title = "Favoritos",
                        count = likedCount,
                        icon = Icons.Rounded.Favorite,
                        iconColor = Color(0xFFE50914),
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryClick("Favoritos") }
                    )
                    ProfileGridItem(
                        title = "Viendo",
                        count = watchingCount,
                        icon = Icons.Rounded.PlayCircle,
                        iconColor = Color(0xFF2196F3),
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryClick("Viendo") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileGridItem(
                        title = "Terminados",
                        count = finishedCount,
                        icon = Icons.Rounded.CheckCircle,
                        iconColor = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryClick("Terminados") }
                    )
                    ProfileGridItem(
                        title = "Ver Después",
                        count = watchLaterCount,
                        icon = Icons.Rounded.WatchLater,
                        iconColor = Color(0xFFFF9800),
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryClick("Ver Después") }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1A1A1A),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = totalCount.toString(), color = Color(0xFFE50914), fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text(text = "Total Contenido", color = Color.Gray, fontSize = 12.sp)
                    }
                    Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.DarkGray).align(Alignment.CenterVertically))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "~$totalHours", color = Color(0xFFE50914), fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text(text = "Horas Aprox.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "Ajustes", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            
            ProfileSettingItem(
                title = "Limpiar Caché",
                icon = Icons.Default.Delete,
                onClick = { viewModel.clearCache() }
            )
            ProfileSettingItem(
                title = "Notificaciones",
                icon = Icons.Default.Notifications,
                onClick = { }
            )
            if (user?.isLoggedIn == true) {
                ProfileSettingItem(
                    title = "Cerrar Sesión",
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    iconColor = Color.Red,
                    textColor = Color.Red,
                    onClick = { viewModel.logout() }
                )
            }
        }
    }
}

@Composable
fun ProfileGridItem(
    title: String,
    count: Int,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(120.dp)
            .clickable { onClick() },
        color = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = count.toString(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Text(text = title, color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ProfileSettingItem(
    title: String,
    icon: ImageVector,
    iconColor: Color = Color.Gray,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, color = textColor) },
        leadingContent = { Icon(icon, contentDescription = null, tint = iconColor) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
fun ProfileListDetail(
    title: String,
    items: List<Movie>,
    onBack: () -> Unit,
    onMovieClick: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(text = title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        }
        
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No hay nada aquí todavía", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { movie ->
                    MovieCardItem(movie, onClick = onMovieClick)
                }
            }
        }
    }
}

@Composable
fun CategoryBubbleItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(25.dp)).clickable { onClick() },
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) Color.Black else Color.White
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HomeSectionsContent(
    sections: List<HomeSection>, 
    adsSpots: List<MyBidSpot>,
    onMovieClick: (Int) -> Unit,
    onDirectPlayClick: (Movie) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 80.dp, bottom = 120.dp)) {
        itemsIndexed(sections, key = { _, section -> section.title }) { index, section ->
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(text = section.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(section.items, key = { it.id }) { movie -> 
                        if (section.isWatchingSection) {
                            WatchingCardItem(movie) { onDirectPlayClick(movie) }
                        } else {
                            MovieCardItem(movie, { onMovieClick(it) }, width = 130.dp) 
                        }
                    }
                }
            }
            
            // Insertar anuncio cada 2 secciones si hay spots disponibles
            if (index > 0 && index % 2 == 0 && adsSpots.isNotEmpty()) {
                val adIndex = (index / 2 - 1) % adsSpots.size
                adsSpots[adIndex].tag_url?.let { url ->
                    MyBidBanner(
                        adTagUrl = url,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryTabContent(
    sections: List<HomeSection>,
    adsSpots: List<MyBidSpot>,
    filteredList: List<Movie>,
    selectedGenre: String?,
    onMovieClick: (Int) -> Unit
) {
    if (selectedGenre != null) {
        VerticalMovieGrid(movies = filteredList, onMovieClick = onMovieClick)
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 80.dp, bottom = 120.dp)) {
            itemsIndexed(sections, key = { _, section -> section.title }) { index, section ->
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(text = section.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(section.items, key = { it.id }) { movie ->
                            MovieCardItem(movie, { onMovieClick(it) }, width = 130.dp)
                        }
                    }
                }
                
                if (index > 0 && index % 3 == 0 && adsSpots.isNotEmpty()) {
                    val adIndex = (index / 3 - 1) % adsSpots.size
                    adsSpots[adIndex].tag_url?.let { url ->
                        MyBidBanner(
                            adTagUrl = url,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WatchingCardItem(movie: Movie, onClick: () -> Unit) {
    Column(modifier = Modifier.width(200.dp).clickable { onClick() }) {
        Card(
            modifier = Modifier.height(110.dp).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
        ) {
            Box {
                AsyncImage(
                    model = movie.backdropUrl ?: movie.posterUrl,
                    contentDescription = movie.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).align(Alignment.Center),
                    tint = Color.White
                )
                
                if (movie.totalDuration > 0) {
                    val progressRatio = movie.lastPosition.toFloat() / movie.totalDuration.toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.Gray.copy(alpha = 0.5f))
                            .align(Alignment.BottomStart)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressRatio.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
        Text(
            text = movie.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun VerticalMovieGrid(movies: List<Movie>, onMovieClick: (Int) -> Unit, topPadding: androidx.compose.ui.unit.Dp = 80.dp) {
    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topPadding, start = 8.dp, end = 8.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(movies, key = { it.id }) { movie -> MovieCardItem(movie, { onMovieClick(it) }) }
    }
}

@Composable
fun SearchGrid(movies: List<Movie>, onMovieClick: (Int) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, start = 8.dp, end = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(movies, key = { it.id }) { movie -> MovieCardItem(movie, { onMovieClick(it) }) }
    }
}

@Composable
fun MovieCardItem(movie: Movie, onClick: (Int) -> Unit, width: androidx.compose.ui.unit.Dp? = null) {
    val modifier = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()
    val context = LocalContext.current
    Column(modifier = modifier.clickable { onClick(movie.id) }) {
        Card(modifier = Modifier.aspectRatio(0.7f), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(movie.posterUrl).crossfade(true).diskCachePolicy(CachePolicy.ENABLED).memoryCachePolicy(CachePolicy.ENABLED).build(),
                    contentDescription = movie.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
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
