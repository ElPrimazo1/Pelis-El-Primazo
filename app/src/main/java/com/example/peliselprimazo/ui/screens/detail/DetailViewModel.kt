package com.example.peliselprimazo.ui.screens.detail

import android.app.Activity
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.peliselprimazo.data.AdsManager
import com.example.peliselprimazo.data.extractor.StreamExtractorFactory
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

    init {
        loadMovieDetail()
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

    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit) {
        adsManager.showInterstitialIfReady(activity, onAdDismissed)
    }

    fun prepareAndPlay(
        server: String, 
        fileId: String, 
        onReady: (String, String?) -> Unit
    ) {
        viewModelScope.launch {
            _preloadingVideo.value = true
            try {
                val extractor = extractorFactory.getExtractor(server)
                val videoUrl = extractor.extract(fileId, repository)
                if (!videoUrl.isNullOrBlank()) {
                    Log.d(tag, "Video extraído correctamente. Pasando al reproductor.")
                    onReady(videoUrl, null)
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

    fun register(u: String, e: String, p: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoadingAuth.value = true
            _error.value = null
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
}

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Success(val movie: Movie) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}
