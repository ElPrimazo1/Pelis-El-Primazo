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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val _preloadingVideo = MutableStateFlow(false)
    val preloadingVideo = _preloadingVideo.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isLoadingAuth = MutableStateFlow(false)
    val isLoadingAuth = _isLoadingAuth.asStateFlow()

    // Optimizados con distinctUntilChanged() para máxima fluidez en Compose
    val user = repository.getUser()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isFavorito = repository.isMovieLiked(movieId)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isViendo = repository.isMovieWatching(movieId)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isTerminado = repository.isMovieFinished(movieId)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isVerDespues = repository.isMovieInWatchLater(movieId)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isGuardado = repository.isMovieSaved(movieId)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        loadMovieDetail()
    }

    fun loadMovieDetail() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                // Carga en IO para no laguear la animación de entrada
                val movie = withContext(Dispatchers.IO) {
                    repository.getMovieById(movieId)
                }
                _uiState.value = if (movie != null) {
                    DetailUiState.Success(movie)
                } else {
                    DetailUiState.Error("Contenido no encontrado")
                }
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun showRewardedVideo(activity: Activity, onReward: () -> Unit) {
        viewModelScope.launch {
            _preloadingVideo.value = true
            adsManager.showRewardedVideo(activity) { success ->
                _preloadingVideo.value = false
                if (success) onReward()
            }
        }
    }

    fun prepareAndPlay(server: String, fileId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            _preloadingVideo.value = true
            _error.value = null
            try {
                val extractor = extractorFactory.getExtractor(server)
                val (videoUrl, _) = withContext(Dispatchers.IO) {
                    extractor.extract(fileId, repository)
                }
                if (!videoUrl.isNullOrBlank()) {
                    onReady(videoUrl)
                } else {
                    _error.value = "Enlace no disponible en este momento"
                }
            } catch (e: Exception) {
                Log.e(tag, "Error al extraer video", e)
                _error.value = "Error al conectar con el servidor"
            } finally {
                _preloadingVideo.value = false
            }
        }
    }

    // Acciones lanzadas explícitamente en el pool de IO
    fun toggleFavorito() = viewModelScope.launch(Dispatchers.IO) { repository.toggleLike(movieId) }
    fun toggleVerDespues() = viewModelScope.launch(Dispatchers.IO) { repository.toggleWatchLater(movieId) }
    fun toggleTerminado() = viewModelScope.launch(Dispatchers.IO) { repository.toggleFinished(movieId) }
    fun toggleViendo() = viewModelScope.launch(Dispatchers.IO) { repository.addToWatching(movieId) }
    fun toggleGuardado() = viewModelScope.launch(Dispatchers.IO) { repository.toggleSave(movieId) }

    fun clearError() { _error.value = null }

    fun login(e: String, p: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoadingAuth.value = true
            val success = withContext(Dispatchers.IO) { repository.login(e, p) }
            _isLoadingAuth.value = false
            if (success) onSuccess() else _error.value = "Credenciales incorrectas"
        }
    }

    fun register(u: String, e: String, p: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoadingAuth.value = true
            try {
                withContext(Dispatchers.IO) { repository.register(u, e, p) }
                _isLoadingAuth.value = false
                onSuccess()
            } catch (e: Exception) {
                _isLoadingAuth.value = false
                _error.value = e.message ?: "Error en el registro"
            }
        }
    }
}

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Success(val movie: Movie) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}
