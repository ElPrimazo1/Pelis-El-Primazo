package com.example.peliselprimazo.ui.screens.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.peliselprimazo.data.AdsManager
import com.example.peliselprimazo.data.extractor.StreamExtractorFactory
import com.example.peliselprimazo.data.remote.MyBidSpot
import com.example.peliselprimazo.domain.model.Movie
import com.example.peliselprimazo.domain.repository.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val extractorFactory: StreamExtractorFactory,
    private val adsManager: AdsManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tag = "DetailViewModel"
    private val movieId: Int = checkNotNull(savedStateHandle["movieId"])

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _isLoadingAuth = MutableStateFlow(false)
    val isLoadingAuth = _isLoadingAuth.asStateFlow()

    private val _preloadingVideo = MutableStateFlow(false)
    val preloadingVideo = _preloadingVideo.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val user = repository.getUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isFavorito = repository.isMovieLiked(movieId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isViendo = repository.getAllContent().map { list ->
        list.any { it.id == movieId && it.isWatching }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isTerminado = repository.isMovieFinished(movieId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isVerDespues = repository.isMovieInWatchLater(movieId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val adsSpots: StateFlow<List<MyBidSpot>> = adsManager.spots

    init {
        loadMovieDetail()
        viewModelScope.launch {
            adsManager.loadAds()
        }
    }

    fun loadMovieDetail() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                val movie = repository.getMovieById(movieId)
                if (movie != null) {
                    _uiState.value = DetailUiState.Success(movie)
                } else {
                    _uiState.value = DetailUiState.Error("No se encontró el contenido")
                }
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun onPlayRequested(
        server: String, 
        fileId: String, 
        onPlayAction: (String, String, String?) -> Unit
    ) {
        Log.d(tag, "Reproducción solicitada. Obteniendo anuncio...")
        val adUrl = adsManager.getPlayerAdUrl()
        onPlayAction(server, fileId, adUrl)
    }

    fun prepareAndPlay(
        server: String, 
        fileId: String, 
        adUrl: String? = null,
        onReady: (String, String?) -> Unit
    ) {
        viewModelScope.launch {
            _preloadingVideo.value = true
            try {
                val extractor = extractorFactory.getExtractor(server)
                val videoUrl = extractor.extract(fileId, repository)
                if (!videoUrl.isNullOrBlank()) {
                    // NO codificamos aquí para evitar doble codificación.
                    // MainActivity.navigateToPlayer se encargará de codificar para la ruta.
                    Log.d(tag, "Video extraído correctamente. Pasando al reproductor.")
                    onReady(videoUrl, adUrl)
                } else {
                    _error.value = "No se pudo obtener el enlace de video"
                }
            } catch (e: Exception) {
                Log.e(tag, "Error al preparar video", e)
                _error.value = "Error al preparar video: ${e.message}"
            } finally {
                _preloadingVideo.value = false
            }
        }
    }

    fun toggleFavorito() { viewModelScope.launch { repository.toggleLike(movieId) } }
    fun toggleViendo() { viewModelScope.launch { repository.addToWatching(movieId) } }
    fun toggleTerminado() { viewModelScope.launch { repository.toggleFinished(movieId) } }
    fun toggleVerDespues() { viewModelScope.launch { repository.toggleWatchLater(movieId) } }

    fun login(e: String, p: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoadingAuth.value = true
            _error.value = null
            try {
                if (repository.login(e, p)) onSuccess()
                else _error.value = "Credenciales incorrectas"
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al iniciar sesión"
            } finally { _isLoadingAuth.value = false }
        }
    }

    fun register(u: String, e: String, p: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoadingAuth.value = true
            _error.value = null
            try {
                repository.register(u, e, p)
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al registrarse"
            } finally { _isLoadingAuth.value = false }
        }
    }
}

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Success(val movie: Movie) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}
