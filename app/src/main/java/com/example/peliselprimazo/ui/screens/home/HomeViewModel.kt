package com.example.peliselprimazo.ui.screens.home

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.peliselprimazo.data.AdsManager
import com.example.peliselprimazo.data.UpdateManager
import com.example.peliselprimazo.data.extractor.StreamExtractorFactory
import com.example.peliselprimazo.domain.model.ContentType
import com.example.peliselprimazo.domain.model.Movie
import com.example.peliselprimazo.domain.repository.MovieRepository
import com.example.peliselprimazo.domain.usecase.GetUpdateConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val extractorFactory: StreamExtractorFactory,
    private val adsManager: AdsManager,
    private val getUpdateConfigUseCase: GetUpdateConfigUseCase,
    private val updateManager: UpdateManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingAuth = MutableStateFlow(false)
    val isLoadingAuth = _isLoadingAuth.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isPreloading = MutableStateFlow(false)
    val isPreloading = _isPreloading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive = _isSearchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre = _selectedGenre.asStateFlow()

    val user = repository.getUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val allContent = repository.getAllContent()
        .catch { Log.e("HomeViewModel", "Error cargando todo el contenido", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchingContent = repository.getWatchingContent()
        .catch { Log.e("HomeViewModel", "Error cargando contenido viendo", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val finishedContent = repository.getFinishedContent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedContent = repository.getSavedContent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedContent = repository.getLikedContent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val watchLaterContent = repository.getWatchLaterContent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory = repository.getSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trendingContent = repository.getTrends()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRewardedVideoReady = adsManager.isRewardedVideoReady

    // Update Configuration
    val updateConfig = getUpdateConfigUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isDownloading = updateManager.isDownloading
    val downloadProgress = updateManager.downloadProgress

    val moviesListFiltered = combine(allContent, _selectedGenre) { list, genre ->
        list.filter { it.contentType == ContentType.MOVIE && (genre == null || it.genres.contains(genre)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seriesListFiltered = combine(allContent, _selectedGenre) { list, genre ->
        list.filter { it.contentType == ContentType.TV && (genre == null || it.genres.contains(genre)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val animeListFiltered = combine(allContent, _selectedGenre) { list, genre ->
        list.filter { it.contentType == ContentType.ANIME && (genre == null || it.genres.contains(genre)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResults = combine(allContent, _searchQuery) { list, query ->
        if (query.isBlank()) emptyList()
        else list.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalHours = allContent.map { list ->
        val totalMs = list.sumOf { it.totalDuration }
        totalMs / (1000 * 60 * 60)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val homeSections = combine(allContent, watchingContent, trendingContent) { list, watching, trends ->
        createSections(list, watching, trends)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movieSections = allContent.map { list ->
        createCategorySections(list.filter { it.contentType == ContentType.MOVIE }, "Películas")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seriesSections = allContent.map { list ->
        createCategorySections(list.filter { it.contentType == ContentType.TV }, "Series")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val animeSections = allContent.map { list ->
        createCategorySections(list.filter { it.contentType == ContentType.ANIME }, "Anime")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun createSections(list: List<Movie>, watching: List<Movie>, trends: List<Movie>): List<HomeSection> {
        if (list.isEmpty()) return emptyList()
        return buildList {
            if (watching.isNotEmpty()) add(HomeSection("Continuar Viendo", watching.take(12), isWatchingSection = true))
            if (trends.isNotEmpty()) add(HomeSection("Tendencias Globales", trends))
            add(HomeSection("Estrenos Exclusivos", list.sortedByDescending { it.releaseDate }.take(15)))
            add(HomeSection("Los más valorados", list.sortedByDescending { it.rating }.take(15)))
            
            listOf("Acción", "Comedia", "Ciencia Ficción", "Terror", "Drama").forEach { genre ->
                val filtered = list.filter { it.genres.contains(genre) }
                if (filtered.isNotEmpty()) add(HomeSection(genre, filtered.shuffled().take(15)))
            }
        }
    }

    private fun createCategorySections(list: List<Movie>, baseTitle: String): List<HomeSection> {
        if (list.isEmpty()) return emptyList()
        return buildList {
            add(HomeSection("Recomendados de $baseTitle", list.shuffled().take(15)))
            add(HomeSection("Populares", list.sortedByDescending { it.rating }.take(15)))
            
            listOf("Acción", "Suspenso", "Animación", "Aventura").forEach { genre ->
                val filtered = list.filter { it.genres.contains(genre) }
                if (filtered.isNotEmpty()) add(HomeSection(genre, filtered.shuffled().take(15)))
            }
        }
    }

    init {
        refreshData(isFirstLoad = true)
        syncConfig()
    }

    fun syncConfig() {
        viewModelScope.launch {
            getUpdateConfigUseCase.sync()
        }
    }

    fun startUpdate(url: String) {
        updateManager.downloadAndInstall(url)
    }

    fun showRewardedVideo(activity: Activity, onReward: () -> Unit) {
        adsManager.showRewardedVideo(activity) { success ->
            if (success) onReward()
        }
    }

    fun launchTestSuite(activity: Activity) {
        adsManager.launchTestSuite(activity)
    }

    fun refreshData(isFirstLoad: Boolean = false) {
        viewModelScope.launch {
            if (isFirstLoad && allContent.value.isEmpty()) {
                _isLoading.value = true
            } else {
                _isRefreshing.value = true
            }
            try {
                repository.refreshContent()
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Error al actualizar contenido"
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    fun prepareAndPlay(movie: Movie, onReady: (String) -> Unit) {
        val firstLink = movie.serverLinks.firstOrNull() ?: return
        viewModelScope.launch {
            _isPreloading.value = true
            try {
                val extractor = extractorFactory.getExtractor(firstLink.serverName)
                val (videoUrl, _) = withContext(Dispatchers.IO) {
                    extractor.extract(firstLink.fileId, repository)
                }
                if (!videoUrl.isNullOrBlank()) {
                    onReady(videoUrl)
                } else {
                    _error.value = "No se pudo obtener el enlace de video"
                }
            } catch (e: Exception) {
                _error.value = "Error al preparar video: ${e.message}"
            } finally {
                _isPreloading.value = false
            }
        }
    }

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun onSearchActiveChange(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _searchQuery.value = ""
            _selectedGenre.value = null
        }
    }
    fun onGenreChange(genre: String?) { _selectedGenre.value = genre }
    fun clearSearchHistory() { viewModelScope.launch { repository.clearSearchHistory() } }
    fun logout() { viewModelScope.launch { repository.logout() } }
    fun clearCache() { viewModelScope.launch { repository.clearCache(); refreshData() } }

    fun updateAvatar(uri: String) { viewModelScope.launch { repository.updateAvatar(uri) } }
    
    fun clearAuthStatus() {
        _isLoadingAuth.value = false
        _error.value = null
    }

    fun register(u: String, e: String, p: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _error.value = null
            _isLoadingAuth.value = true
            try {
                repository.register(u, e, p)
                _isLoadingAuth.value = false
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al registrarse"
                _isLoadingAuth.value = false
            }
        }
    }

    fun login(e: String, p: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _error.value = null
            _isLoadingAuth.value = true
            try {
                if (repository.login(e, p)) {
                    _isLoadingAuth.value = false
                    onSuccess()
                } else {
                    _error.value = "Credenciales incorrectas"
                    _isLoadingAuth.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al iniciar sesión"
                _isLoadingAuth.value = false
            }
        }
    }
}

data class HomeSection(
    val title: String, 
    val items: List<Movie>,
    val isWatchingSection: Boolean = false
)
