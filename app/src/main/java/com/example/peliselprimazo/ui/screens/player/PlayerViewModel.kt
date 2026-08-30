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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        val isEmbed: Boolean = false,
        val serverName: String = "",
        val resumePosition: Long = 0L,
        val isChangingEpisode: Boolean = false
    ) : PlayerUiState()
    data class Error(val message: String) : PlayerUiState()
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val extractorFactory: StreamExtractorFactory,
    private val adsManager: AdsManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tag = "PlayerViewModel"
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var currentMovieId: Int? = null

    init {
        val serverName: String? = savedStateHandle["serverName"]
        val fileId: String? = savedStateHandle["fileId"]
        val movieId: Int? = savedStateHandle["movieId"]
        val videoUrl: String? = savedStateHandle["videoUrl"]
        // Aceptamos un adUrl opcional desde la navegación para evitar doble rotación
        val passedAdUrl: String? = savedStateHandle["adUrl"]

        Log.d(tag, "Init PlayerViewModel con: server=$serverName, movieId=$movieId, videoUrl=$videoUrl")

        if (serverName != null && fileId != null) {
            loadVideo(serverName, fileId, movieId, videoUrl, passedAdUrl)
        } else {
            _uiState.value = PlayerUiState.Error("Faltan parámetros de reproducción")
        }
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
            
            if (currentState is PlayerUiState.Success && currentState.currentLink?.fileId == fileId) return@launch

            if (currentState is PlayerUiState.Success) {
                _uiState.value = currentState.copy(isChangingEpisode = true)
            } else {
                _uiState.value = PlayerUiState.Loading
            }

            try {
                val movie = movieId?.let { repository.getMovieById(it) }
                if (movie == null) {
                    _uiState.value = PlayerUiState.Error("No se encontró información del contenido")
                    return@launch
                }

                val videoUrl = if (!preloadedUrl.isNullOrBlank()) {
                    preloadedUrl
                } else {
                    val extractor = extractorFactory.getExtractor(serverName)
                    extractor.extract(fileId, repository)
                }

                val extractor = extractorFactory.getExtractor(serverName)
                val headers = extractor.getHeaders(fileId)

                if (!videoUrl.isNullOrBlank()) {
                    val currentLink = movie.serverLinks.find { it.fileId == fileId }
                    val nextLink = findNextEpisode(movie, currentLink)

                    // Obtenemos el anuncio usando la rotación (Round Robin) si no viene pre-cargado
                    val adUrl = preloadedAdUrl ?: adsManager.getPlayerAdUrl()

                    _uiState.value = PlayerUiState.Success(
                        movie = movie,
                        currentLink = currentLink,
                        nextLink = nextLink,
                        videoUrl = videoUrl,
                        adUrl = adUrl,
                        headers = headers,
                        isEmbed = !isDirectLink(videoUrl),
                        serverName = serverName,
                        resumePosition = if (currentState is PlayerUiState.Success) 0L else movie.lastPosition,
                        isChangingEpisode = false
                    )
                    
                    repository.addToWatching(movie.id)
                } else {
                    _uiState.value = PlayerUiState.Error("No se pudo obtener el enlace de video del servidor $serverName")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error cargando video", e)
                _uiState.value = PlayerUiState.Error("Error: ${e.message}")
            }
        }
    }

    private fun findNextEpisode(movie: Movie, current: ServerLink?): ServerLink? {
        if (current == null || current.episode == null) return null
        return movie.serverLinks
            .filter { it.season == current.season }
            .find { it.episode == current.episode!! + 1 }
            ?: movie.serverLinks.find { it.season == (current.season ?: 0) + 1 && it.episode == 1 }
    }

    fun updateProgress(position: Long, duration: Long) {
        val id = currentMovieId ?: return
        if (duration <= 0) return
        viewModelScope.launch { repository.updatePlaybackProgress(id, position, duration) }
    }

    fun reportError() {
        val state = _uiState.value as? PlayerUiState.Success ?: return
        viewModelScope.launch {
            repository.reportBrokenLink(state.movie.id, state.serverName, state.currentLink?.fileId ?: "")
        }
    }

    private fun isDirectLink(url: String): Boolean {
        val lowUrl = url.lowercase()
        return lowUrl.contains(".mp4") || lowUrl.contains(".m3u8") || 
               lowUrl.contains(".mkv") || lowUrl.contains("get_video") || 
               lowUrl.contains("streamtape.com/get_video") || lowUrl.contains("/download")
    }
}
