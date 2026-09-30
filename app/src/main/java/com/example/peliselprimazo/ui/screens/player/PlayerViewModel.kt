package com.example.peliselprimazo.ui.screens.player

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.peliselprimazo.data.AdsManager
import com.example.peliselprimazo.data.extractor.StreamExtractorFactory
import com.example.peliselprimazo.domain.model.Movie
import com.example.peliselprimazo.domain.model.ServerLink
import com.example.peliselprimazo.domain.repository.MovieRepository
import com.example.peliselprimazo.domain.usecase.GetUpdateConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

sealed class PlayerUiState {
    data object Loading : PlayerUiState()
    data class Success(
        val movie: Movie,
        val currentLink: ServerLink? = null,
        val nextLink: ServerLink? = null,
        val videoUrl: String,
        val adUrl: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val serverName: String = "",
        val resumePosition: Long = 0L,
        val isChangingEpisode: Boolean = false
    ) : PlayerUiState()
    data class Error(val message: String, val canRetry: Boolean = true) : PlayerUiState()
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val extractorFactory: StreamExtractorFactory,
    private val adsManager: AdsManager,
    private val getUpdateConfigUseCase: GetUpdateConfigUseCase,
    private val okHttpClient: OkHttpClient,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tag = "PlayerViewModel"
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var currentMovieId: Int? = null
    private val browserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    init {
        loadFromSavedState()
    }

    private fun loadFromSavedState() {
        val serverName: String? = savedStateHandle["serverName"]
        val fileId: String? = savedStateHandle["fileId"]
        val movieId: Int? = savedStateHandle["movieId"]
        val videoUrl: String? = savedStateHandle["videoUrl"]
        val passedAdUrl: String? = savedStateHandle["adUrl"]

        if (serverName != null && fileId != null) {
            loadVideo(serverName, fileId, movieId, videoUrl, passedAdUrl)
        } else {
            _uiState.value = PlayerUiState.Error("Faltan parámetros de reproducción", false)
        }
    }

    fun retry() {
        loadFromSavedState()
    }

    fun loadVideo(
        serverName: String, 
        fileId: String, 
        movieId: Int? = null, 
        preloadedUrl: String? = null,
        preloadedAdUrl: String? = null
    ) {
        currentMovieId = movieId
        viewModelScope.launch {
            val currentState = _uiState.value
            
            if (currentState is PlayerUiState.Success && currentState.currentLink?.fileId == fileId && !currentState.isChangingEpisode) return@launch

            if (currentState is PlayerUiState.Success) {
                _uiState.value = currentState.copy(isChangingEpisode = true)
            } else {
                _uiState.value = PlayerUiState.Loading
            }

            try {
                val movie = withContext(Dispatchers.IO) {
                    movieId?.let { repository.getMovieById(it) }
                }
                
                if (movie == null) {
                    _uiState.value = PlayerUiState.Error("No se encontró información del contenido", false)
                    return@launch
                }

                // Extracción en hilo secundario con User-Agent de navegador
                val extractor = extractorFactory.getExtractor(serverName)
                val (extractedUrl, extractedHeaders) = withContext(Dispatchers.IO) {
                    if (!preloadedUrl.isNullOrBlank()) {
                        preloadedUrl to extractor.getHeaders()
                    } else {
                        extractor.extract(fileId, repository)
                    }
                }

                if (!extractedUrl.isNullOrBlank()) {
                    // Ping Keep-Alive asíncrono
                    if (serverName.lowercase().contains("streamtape")) {
                        pingStreamtape(fileId)
                    }

                    val currentLink = movie.serverLinks.find { it.fileId == fileId }
                    val nextLink = findNextEpisode(movie, currentLink)

                    _uiState.value = PlayerUiState.Success(
                        movie = movie,
                        currentLink = currentLink,
                        nextLink = nextLink,
                        videoUrl = extractedUrl,
                        adUrl = preloadedAdUrl ?: adsManager.getPlayerAdUrl(),
                        headers = extractedHeaders,
                        serverName = serverName,
                        resumePosition = if (currentState is PlayerUiState.Success) 0L else movie.lastPosition,
                        isChangingEpisode = false
                    )
                    
                    withContext(Dispatchers.IO) {
                        repository.addToWatching(movie.id)
                    }
                } else {
                    _uiState.value = PlayerUiState.Error("No se pudo extraer el enlace directo (Token expirado o error de servidor).")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error en flujo: ${e.message}")
                _uiState.value = PlayerUiState.Error("Fallo de red: ${e.localizedMessage}")
            }
        }
    }

    private fun pingStreamtape(fileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://streamtape.com/v/$fileId")
                    .header("User-Agent", browserUserAgent)
                    .build()
                okHttpClient.newCall(request).execute().use { }
            } catch (e: Exception) {
                Log.w(tag, "Keep-alive silenciado")
            }
        }
    }

    private fun findNextEpisode(movie: Movie, current: ServerLink?): ServerLink? {
        if (current == null || current.episode == null) return null
        return movie.serverLinks.find { it.season == current.season && it.episode == (current.episode!! + 1) }
    }

    fun updateProgress(position: Long, duration: Long) {
        val id = currentMovieId ?: return
        if (duration <= 0) return
        viewModelScope.launch(Dispatchers.IO) { repository.updatePlaybackProgress(id, position, duration) }
    }
}
