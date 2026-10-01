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
    
    private val _authMessage = MutableStateFlow<String?>(null)
    val authMessage = _authMessage.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive = _isSearchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre = _selectedGenre.asStateFlow()

    val user = repository.getUser()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val allContent = repository.getAllContent()
        .distinctUntilChanged()
        .catch { Log.e("HomeViewModel", "Error cargando contenido", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchingContent = repository.getWatchingContent()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trendingContent = repository.getTrends()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory = repository.getSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val finishedContent = repository.getFinishedContent()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedContent = repository.getSavedContent()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedContent = repository.getLikedContent()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val watchLaterContent = repository.getWatchLaterContent()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val updateConfig = getUpdateConfigUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isDownloading = updateManager.isDownloading
    val downloadProgress = updateManager.downloadProgress

    val homeSections = combine(allContent, watchingContent, trendingContent) { list, watching, trends ->
        withContext(Dispatchers.Default) { createSections(list, watching, trends) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movieSections = allContent.map { list ->
        withContext(Dispatchers.Default) { createCategorySections(list.filter { it.contentType == ContentType.MOVIE }, "Películas") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seriesSections = allContent.map { list ->
        withContext(Dispatchers.Default) { createCategorySections(list.filter { it.contentType == ContentType.TV }, "Series") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val animeSections = allContent.map { list ->
        withContext(Dispatchers.Default) { createCategorySections(list.filter { it.contentType == ContentType.ANIME }, "Anime") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val moviesListFiltered = combine(allContent, _selectedGenre) { list, genre ->
        withContext(Dispatchers.Default) { list.filter { it.contentType == ContentType.MOVIE && (genre == null || it.genres.contains(genre)) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seriesListFiltered = combine(allContent, _selectedGenre) { list, genre ->
        withContext(Dispatchers.Default) { list.filter { it.contentType == ContentType.TV && (genre == null || it.genres.contains(genre)) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val animeListFiltered = combine(allContent, _selectedGenre) { list, genre ->
        withContext(Dispatchers.Default) { list.filter { it.contentType == ContentType.ANIME && (genre == null || it.genres.contains(genre)) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResults = _searchQuery
        .debounce(300)
        .combine(allContent) { query, list ->
            if (query.isBlank()) emptyList()
            else list.filter { it.title.contains(query, ignoreCase = true) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalHours = allContent.map { list ->
        list.sumOf { it.totalDuration } / (1000 * 60 * 60)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private fun createSections(list: List<Movie>, watching: List<Movie>, trends: List<Movie>): List<HomeSection> {
        if (list.isEmpty()) return emptyList()
        return buildList {
            val newlyAdded = list.sortedByDescending { it.createdAt }.take(15)
            
            if (watching.isNotEmpty()) {
                add(HomeSection("Continuar Viendo", watching.take(12), isWatchingSection = true))
                if (newlyAdded.isNotEmpty()) add(HomeSection("Nuevos añadidos", newlyAdded))
            } else {
                if (newlyAdded.isNotEmpty()) add(HomeSection("Nuevos añadidos", newlyAdded))
            }
            
            if (trends.isNotEmpty()) add(HomeSection("Tendencias Globales", trends))
            add(HomeSection("Estrenos Exclusivos", list.sortedByDescending { it.releaseDate }.take(15)))
            add(HomeSection("Los más valorados", list.sortedByDescending { it.rating }.take(15)))
            
            listOf("Acción", "Comedia", "Ciencia Ficción", "Terror").forEach { genre ->
                val filtered = list.filter { it.genres.contains(genre) }
                if (filtered.isNotEmpty()) add(HomeSection(genre, filtered.take(15)))
            }
        }
    }

    private fun createCategorySections(list: List<Movie>, baseTitle: String): List<HomeSection> {
        if (list.isEmpty()) return emptyList()
        return buildList {
            add(HomeSection("Recomendados de $baseTitle", list.take(15)))
            add(HomeSection("Populares", list.sortedByDescending { it.rating }.take(15)))
            
            listOf("Acción", "Suspenso", "Animación", "Aventura").forEach { genre ->
                val filtered = list.filter { it.genres.contains(genre) }
                if (filtered.isNotEmpty()) add(HomeSection(genre, filtered.take(15)))
            }
        }
    }

    init {
        refreshData(isFirstLoad = true)
        syncConfig()
    }

    fun syncConfig() { viewModelScope.launch { getUpdateConfigUseCase.sync() } }
    fun startUpdate(url: String) { updateManager.downloadAndInstall(url) }

    fun refreshData(isFirstLoad: Boolean = false) {
        viewModelScope.launch {
            if (isFirstLoad && allContent.value.isEmpty()) _isLoading.value = true
            else _isRefreshing.value = true
            try {
                withContext(Dispatchers.IO) { repository.refreshContent() }
                _error.value = null
            } catch (e: java.net.UnknownHostException) {
                _error.value = "Error de conexión: Revisa tu internet"
            } catch (e: Exception) {
                _error.value = "Error al actualizar contenido"
                Log.e("HomeVM", "Sync error", e)
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    fun register(u: String, e: String, p: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _error.value = null
            _authMessage.value = null
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
            _authMessage.value = null
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
    
    fun resetPassword(email: String) {
        viewModelScope.launch {
            _error.value = null
            _authMessage.value = null
            if (email.isBlank()) {
                _error.value = "Introduce tu correo electrónico"
                return@launch
            }
            _isLoadingAuth.value = true
            try {
                repository.resetPassword(email)
                _authMessage.value = "Se ha enviado un correo para restablecer tu contraseña"
                _isLoadingAuth.value = false
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al enviar el correo"
                _isLoadingAuth.value = false
            }
        }
    }

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun onSearchActiveChange(active: Boolean) {
        _isSearchActive.value = active
        if (!active) _searchQuery.value = ""
    }
    fun onGenreChange(genre: String?) { _selectedGenre.value = genre }
    fun clearCache() = viewModelScope.launch(Dispatchers.IO) { repository.clearCache(); refreshData() }
    fun logout() = viewModelScope.launch(Dispatchers.IO) { repository.logout() }
    fun updateAvatar(uri: String) = viewModelScope.launch(Dispatchers.IO) { repository.updateAvatar(uri) }
    fun clearSearchHistory() = viewModelScope.launch(Dispatchers.IO) { repository.clearSearchHistory() }
    fun launchTestSuite(activity: Activity) = adsManager.launchTestSuite(activity)
}

data class HomeSection(
    val title: String, 
    val items: List<Movie>,
    val isWatchingSection: Boolean = false
)
