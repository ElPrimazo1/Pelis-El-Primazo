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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
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
        val isEmbed: Boolean = true,
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
    private val getUpdateConfigUseCase: GetUpdateConfigUseCase,
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
        val passedAdUrl: String? = savedStateHandle["adUrl"]

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
            
            if (currentState is PlayerUiState.Success && currentState.currentLink?.fileId == fileId && !currentState.isChangingEpisode) return@launch

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

                // Aseguramos formato /e/ (Embed) en todos los servidores conocidos para reducir basura
                val videoUrlToLoad = when (serverName.lowercase()) {
                    "streamtape" -> "https://streamtape.com/e/$fileId"
                    "doodstream", "dood" -> "https://dood.to/e/$fileId"
                    "filemoon" -> "https://filemoon.sx/e/$fileId"
                    "vidhide", "vidhidepro", "vidhidevip" -> "https://vidhidepro.com/e/$fileId"
                    "upstream" -> "https://upstream.to/e/$fileId"
                    "voe" -> "https://voe.sx/e/$fileId"
                    else -> {
                        val extractor = extractorFactory.getExtractor(serverName)
                        extractor.extract(fileId, repository)
                    }
                }

                if (!videoUrlToLoad.isNullOrBlank()) {
                    val currentLink = movie.serverLinks.find { it.fileId == fileId }
                    val nextLink = findNextEpisode(movie, currentLink)

                    val config = getUpdateConfigUseCase().firstOrNull()
                    val remoteAdUrl = config?.visualFlags?.get("ui_player_ad_vast_url")
                    val adUrl = preloadedAdUrl ?: remoteAdUrl ?: adsManager.getPlayerAdUrl()

                    _uiState.value = PlayerUiState.Success(
                        movie = movie,
                        currentLink = currentLink,
                        nextLink = nextLink,
                        videoUrl = videoUrlToLoad,
                        adUrl = adUrl,
                        isEmbed = true,
                        serverName = serverName,
                        resumePosition = if (currentState is PlayerUiState.Success) 0L else movie.lastPosition,
                        isChangingEpisode = false
                    )
                    
                    repository.addToWatching(movie.id)
                } else {
                    _uiState.value = PlayerUiState.Error("No se pudo obtener el enlace de reproducción")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error cargando video", e)
                _uiState.value = PlayerUiState.Error("Error: ${e.message}")
            }
        }
    }

    private fun findNextEpisode(movie: Movie, current: ServerLink?): ServerLink? {
        if (current == null || current.episode == null) return null
        val nextInSameServer = movie.serverLinks
            .filter { it.season == current.season && it.serverName == current.serverName }
            .find { it.episode == current.episode + 1 }
        if (nextInSameServer != null) return nextInSameServer
        val nextInAnyServer = movie.serverLinks
            .filter { it.season == current.season }
            .find { it.episode == current.episode + 1 }
        if (nextInAnyServer != null) return nextInAnyServer
        return null
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
}
